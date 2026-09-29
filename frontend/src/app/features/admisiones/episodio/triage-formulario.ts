import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { debounceTime, filter, map, switchMap, catchError, EMPTY } from 'rxjs';

import { erroresCampos, mensajeError } from '../../../core/api/api';
import { Episodio, NIVELES_TRIAGE, ROMANO, SignosVitales, SugerenciaTriage } from '../admisiones.models';
import { AdmisionesService } from '../admisiones.service';

interface CampoSigno {
  clave: keyof SignosVitales;
  etiqueta: string;
  unidad: string;
  min: number;
  max: number;
  paso?: number;
}

/** Mismos rangos que valida el backend (SignosVitales.java): rechazan errores de digitación. */
const SIGNOS: CampoSigno[] = [
  { clave: 'frecuenciaCardiaca', etiqueta: 'Frecuencia cardiaca', unidad: 'lpm', min: 20, max: 250 },
  { clave: 'frecuenciaRespiratoria', etiqueta: 'Frecuencia respiratoria', unidad: 'rpm', min: 4, max: 70 },
  { clave: 'presionSistolica', etiqueta: 'Presión sistólica', unidad: 'mmHg', min: 40, max: 300 },
  { clave: 'presionDiastolica', etiqueta: 'Presión diastólica', unidad: 'mmHg', min: 20, max: 200 },
  { clave: 'temperatura', etiqueta: 'Temperatura', unidad: '°C', min: 30, max: 45, paso: 0.1 },
  { clave: 'saturacionOxigeno', etiqueta: 'Saturación de oxígeno', unidad: '%', min: 50, max: 100 },
  { clave: 'glasgow', etiqueta: 'Escala de Glasgow', unidad: '3–15', min: 3, max: 15 },
  { clave: 'dolor', etiqueta: 'Dolor (EVA)', unidad: '0–10', min: 0, max: 10 },
];

/** RFADM-005 / RFADM-006: signos vitales estructurados con nivel sugerido en vivo. */
@Component({
  selector: 'app-triage-formulario',
  imports: [ReactiveFormsModule],
  templateUrl: './triage-formulario.html',
  styleUrl: './triage-formulario.scss',
})
export class TriageFormulario implements OnInit {
  private readonly service = inject(AdmisionesService);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  readonly episodio = input.required<Episodio>();
  readonly guardado = output<Episodio>();
  readonly cancelar = output<void>();

  protected readonly campos = SIGNOS;
  protected readonly niveles = NIVELES_TRIAGE;
  protected readonly romano = ROMANO;

  protected readonly signos = this.fb.group(
    Object.fromEntries(
      SIGNOS.map((c) => [
        c.clave,
        new FormControl<number | null>(null, [Validators.required, Validators.min(c.min), Validators.max(c.max)]),
      ]),
    ) as Record<keyof SignosVitales, FormControl<number | null>>,
  );
  protected readonly form = this.fb.group({
    signos: this.signos,
    sintomaPrincipal: ['', [Validators.required, Validators.maxLength(500)]],
    nivelAsignado: [0, [Validators.required, Validators.min(1)]],
    justificacionAjuste: ['', Validators.maxLength(500)],
  });

  protected readonly sugerencia = signal<SugerenciaTriage | null>(null);
  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);
  private readonly erroresServidor = signal<Record<string, string>>({});

  ngOnInit(): void {
    const actual = this.episodio().triage;
    if (actual) {
      this.form.setValue({
        signos: { ...actual.signos },
        sintomaPrincipal: actual.sintomaPrincipal,
        nivelAsignado: actual.nivelAsignado,
        justificacionAjuste: actual.justificacionAjuste ?? '',
      });
      // El nivel guardado lo eligió una persona: la sugerencia no debe reemplazarlo al recargar.
      this.form.controls.nivelAsignado.markAsDirty();
    }

    this.signos.valueChanges
      .pipe(
        debounceTime(400),
        filter(() => this.signos.valid),
        map(() => this.signos.getRawValue() as unknown as SignosVitales),
        switchMap((s) => this.service.sugerirTriage(s, this.episodio().paciente.edad).pipe(catchError(() => EMPTY))),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((s) => {
        const anterior = this.sugerencia()?.nivel;
        this.sugerencia.set(s);
        // Propone el nivel sugerido mientras quien clasifica no haya elegido otro a mano.
        const nivel = this.form.controls.nivelAsignado;
        if (!nivel.dirty || nivel.value === anterior || nivel.value === 0) {
          nivel.setValue(s.nivel);
          nivel.markAsPristine();
        }
      });
    if (actual) this.signos.updateValueAndValidity();
  }

  protected get ajustado(): boolean {
    const s = this.sugerencia();
    const nivel = this.form.controls.nivelAsignado.value;
    return !!s && nivel > 0 && nivel !== s.nivel;
  }

  protected invalidoSigno(clave: string): boolean {
    const c = this.signos.get(clave);
    return (!!c && c.invalid && c.touched) || !!this.erroresServidor()['signos.' + clave];
  }

  protected errorServidor(campo: string): string | null {
    return this.erroresServidor()[campo] ?? null;
  }

  protected guardar(): void {
    this.error.set(null);
    this.erroresServidor.set({});
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.ajustado && !this.form.controls.justificacionAjuste.value.trim()) {
      this.form.controls.justificacionAjuste.markAsTouched();
      this.error.set('Justifique por qué asigna un nivel distinto al sugerido.');
      return;
    }
    const v = this.form.getRawValue();
    this.guardando.set(true);
    this.service
      .triage(this.episodio().id, {
        signos: this.signos.getRawValue() as unknown as SignosVitales,
        sintomaPrincipal: v.sintomaPrincipal.trim(),
        nivelAsignado: v.nivelAsignado,
        justificacionAjuste: this.ajustado ? v.justificacionAjuste.trim() : null,
      })
      .subscribe({
        next: (e) => this.guardado.emit(e),
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          this.erroresServidor.set(erroresCampos(e));
          this.error.set(mensajeError(e, 'No fue posible guardar el triage.'));
        },
      });
  }
}
