import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { debounceTime, filter } from 'rxjs';

import { codigoError, erroresCampos, mensajeError } from '../../../core/api/api';
import {
  Paciente,
  PacienteRequest,
  PosibleDuplicado,
  REGIMENES,
  Regimen,
  SEXOS,
  Sexo,
  TIPOS_DOCUMENTO,
  TipoDocumento,
} from '../pacientes.models';
import { PacientesService } from '../pacientes.service';

/** Aseguradoras frecuentes, solo como sugerencia: el campo acepta cualquier nombre. */
const ASEGURADORAS = [
  'Nueva EPS', 'EPS Sura', 'EPS Sanitas', 'Compensar EPS', 'Salud Total EPS', 'Famisanar EPS',
  'Coosalud EPS', 'Mutual Ser EPS', 'Aliansalud EPS', 'Asmet Salud EPS', 'Emssanar EPS', 'Savia Salud EPS',
];

/** Registro (RFADM-003 / RFADM-010) y edición con motivo (RFADM-011) de pacientes. */
@Component({
  selector: 'app-paciente-formulario',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './paciente-formulario.html',
  styleUrl: './paciente-formulario.scss',
})
export class PacienteFormulario implements OnInit {
  private readonly service = inject(PacientesService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  /** Id de la ruta /pacientes/:id/editar; vacío al crear. */
  readonly id = input<string>();
  protected readonly editando = computed(() => !!this.id());

  protected readonly tiposDocumento = TIPOS_DOCUMENTO;
  protected readonly sexos = SEXOS;
  protected readonly regimenes = REGIMENES;
  protected readonly aseguradoras = ASEGURADORAS;
  protected readonly hoy = new Date().toISOString().slice(0, 10);

  protected readonly form = inject(NonNullableFormBuilder).group({
    sinDocumento: [false],
    tipoDocumento: ['CC' as TipoDocumento, Validators.required],
    numeroDocumento: [''],
    nombres: ['', [Validators.required, Validators.maxLength(100)]],
    apellidos: ['', [Validators.required, Validators.maxLength(100)]],
    fechaNacimiento: [''],
    sexo: ['' as Sexo | ''],
    telefono: ['', Validators.pattern(/^[0-9+()\s-]{7,20}$/)],
    email: ['', [Validators.email, Validators.maxLength(120)]],
    direccion: ['', Validators.maxLength(200)],
    aseguradora: ['', Validators.maxLength(120)],
    regimen: ['' as Regimen | ''],
    motivo: ['', Validators.maxLength(500)],
    confirmado: [false],
  });

  protected readonly original = signal<Paciente | null>(null);
  protected readonly duplicados = signal<PosibleDuplicado[]>([]);
  protected readonly cargando = signal(false);
  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);
  private readonly erroresServidor = signal<Record<string, string>>({});

  ngOnInit(): void {
    const id = this.id();
    if (id) {
      this.cargando.set(true);
      this.service.obtener(+id).subscribe({
        next: (p) => this.cargar(p),
        error: (e: HttpErrorResponse) => {
          this.error.set(mensajeError(e, 'No fue posible cargar el paciente.'));
          this.cargando.set(false);
        },
      });
    }

    // RFADM-003: mientras se digita, buscar pacientes parecidos
    this.form.valueChanges
      .pipe(
        debounceTime(600),
        filter(() => this.form.controls.nombres.value.trim().length >= 2 && this.form.controls.apellidos.value.trim().length >= 2),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => this.revisarDuplicados());
  }

  private cargar(p: Paciente): void {
    this.original.set(p);
    this.form.reset({
      sinDocumento: p.identificacionTemporal,
      tipoDocumento: p.identificacionTemporal ? 'CC' : p.tipoDocumento,
      numeroDocumento: p.identificacionTemporal ? '' : p.numeroDocumento,
      nombres: p.nombres,
      apellidos: p.apellidos,
      fechaNacimiento: p.fechaNacimiento ?? '',
      sexo: p.sexo ?? '',
      telefono: p.telefono ?? '',
      email: p.email ?? '',
      direccion: p.direccion ?? '',
      aseguradora: p.aseguradora ?? '',
      regimen: p.regimen ?? '',
      motivo: '',
      confirmado: false,
    });
    this.cargando.set(false);
  }

  protected get sinDocumento(): boolean {
    return this.form.controls.sinDocumento.value;
  }

  protected get esParticular(): boolean {
    return this.form.controls.regimen.value === 'PARTICULAR';
  }

  private datos(): PacienteRequest {
    const v = this.form.getRawValue();
    const nulo = (s: string) => (s.trim() ? s.trim() : null);
    return {
      tipoDocumento: v.sinDocumento ? 'TEMPORAL' : v.tipoDocumento,
      numeroDocumento: v.sinDocumento ? null : nulo(v.numeroDocumento),
      nombres: v.nombres.trim(),
      apellidos: v.apellidos.trim(),
      fechaNacimiento: nulo(v.fechaNacimiento),
      sexo: v.sexo || null,
      telefono: nulo(v.telefono),
      email: nulo(v.email),
      direccion: nulo(v.direccion),
      aseguradora: nulo(v.aseguradora),
      regimen: v.regimen || null,
      confirmarNoDuplicado: v.confirmado,
      motivo: this.editando() ? nulo(v.motivo) : null,
    };
  }

  private revisarDuplicados(): void {
    const id = this.id();
    this.service.posiblesDuplicados(this.datos(), id ? +id : undefined).subscribe({
      next: (lista) => this.duplicados.set(lista),
      error: () => this.duplicados.set([]),
    });
  }

  protected invalido(campo: string): boolean {
    const c = this.form.get(campo);
    return (!!c && c.invalid && c.touched) || !!this.erroresServidor()[campo];
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
    if (this.duplicados().length && !this.editando() && !this.form.controls.confirmado.value) {
      this.error.set('Revise los pacientes parecidos y confirme que se trata de otra persona.');
      return;
    }
    const datos = this.datos();
    const id = this.id();
    this.guardando.set(true);
    const peticion = id ? this.service.actualizar(+id, datos) : this.service.crear(datos);
    peticion.subscribe({
      next: (p) => this.router.navigate(['/pacientes', p.id]),
      error: (e: HttpErrorResponse) => {
        this.guardando.set(false);
        this.erroresServidor.set(erroresCampos(e));
        this.error.set(mensajeError(e, 'No fue posible guardar el paciente.'));
        if (codigoError(e) === 'POSIBLE_DUPLICADO') this.revisarDuplicados();
      },
    });
  }
}
