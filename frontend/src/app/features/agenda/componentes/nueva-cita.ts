import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, filter, switchMap, catchError, of, startWith } from 'rxjs';

import { mensajeError } from '../../../core/api/api';
import { PacienteResumen } from '../../pacientes/pacientes.models';
import { PacientesService } from '../../pacientes/pacientes.service';
import { Catalogos, Cita, Cupo, PRIORIDADES, PrioridadCita, fechaLocal } from '../agenda.models';
import { AgendaService } from '../agenda.service';
import { SelectorCupo } from './selector-cupo';

/** Agendar en un solo panel: paciente → tipo → cupo → confirmar (RNFAGE-003). */
@Component({
  selector: 'app-nueva-cita',
  imports: [ReactiveFormsModule, RouterLink, DatePipe, SelectorCupo],
  templateUrl: './nueva-cita.html',
  styleUrl: './panel.scss',
})
export class NuevaCita implements OnInit {
  private readonly agenda = inject(AgendaService);
  private readonly pacientes = inject(PacientesService);
  private readonly destroyRef = inject(DestroyRef);

  readonly catalogos = input.required<Catalogos>();
  readonly profesionalInicial = input<number | null>(null);
  readonly fechaInicial = input<string>();
  readonly paciente = input<PacienteResumen | null>(null);
  readonly creada = output<Cita>();
  readonly cerrar = output<void>();

  protected readonly prioridades = PRIORIDADES;
  protected readonly hoy = fechaLocal(new Date());
  protected readonly form = inject(NonNullableFormBuilder).group({
    busqueda: [''],
    tipoCitaId: [0, Validators.min(1)],
    prioridad: ['NORMAL' as PrioridadCita],
    especialidadId: [0],
    profesionalId: [0],
    motivo: ['', Validators.maxLength(500)],
  });

  protected readonly pacienteElegido = signal<PacienteResumen | null>(null);
  protected readonly resultados = signal<PacienteResumen[]>([]);
  protected readonly cupo = signal<Cupo | null>(null);
  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);

  private readonly valores = toSignal(this.form.valueChanges.pipe(startWith(this.form.getRawValue())), {
    initialValue: this.form.getRawValue(),
  });
  protected readonly tipo = computed(() => this.catalogos().tipos.find((t) => t.id === +this.valores().tipoCitaId!) ?? null);
  protected readonly profesionalesFiltrados = computed(() => {
    const esp = +this.valores().especialidadId!;
    return this.catalogos().profesionales.filter((p) => !esp || p.especialidadId === esp);
  });
  protected readonly profesionalId = computed(() => +this.valores().profesionalId! || null);
  protected readonly especialidadId = computed(() => +this.valores().especialidadId! || null);

  ngOnInit(): void {
    const inicial = this.paciente();
    if (inicial) this.pacienteElegido.set(inicial);
    const prof = this.catalogos().profesionales.find((p) => p.id === this.profesionalInicial());
    const primerTipo = this.catalogos().tipos.find((t) => t.codigo === 'PRIMERA_VEZ') ?? this.catalogos().tipos[0];
    this.form.patchValue({
      tipoCitaId: primerTipo?.id ?? 0,
      especialidadId: prof?.especialidadId ?? 0,
      profesionalId: prof?.id ?? 0,
    });

    // RFAGE-002: búsqueda del paciente mientras se escribe
    this.form.controls.busqueda.valueChanges
      .pipe(
        debounceTime(350),
        distinctUntilChanged(),
        filter((t) => t.trim().length >= 3),
        switchMap((texto) => this.pacientes.buscar({ texto }, 0, 6).pipe(catchError(() => of(null)))),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((res) => this.resultados.set(res?.content ?? []));

    // Cambiar criterios invalida el cupo elegido
    this.form.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      const c = this.cupo();
      const v = this.form.getRawValue();
      if (c && ((v.profesionalId && c.profesionalId !== +v.profesionalId) || this.tipoCambio(c))) this.cupo.set(null);
    });
    this.form.controls.especialidadId.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((esp) => {
      const prof = this.catalogos().profesionales.find((p) => p.id === +this.form.controls.profesionalId.value);
      if (prof && +esp && prof.especialidadId !== +esp) this.form.controls.profesionalId.setValue(0);
    });
  }

  private tipoCambio(c: Cupo): boolean {
    const dur = this.tipo()?.duracionMinutos;
    return !!dur && new Date(c.fin).getTime() - new Date(c.inicio).getTime() !== dur * 60_000;
  }

  protected elegirPaciente(p: PacienteResumen): void {
    if (p.identificacionTemporal) {
      this.error.set('Registre el documento del paciente antes de agendarle una cita.');
      return;
    }
    this.error.set(null);
    this.pacienteElegido.set(p);
    this.resultados.set([]);
    this.form.controls.busqueda.setValue('', { emitEvent: false });
  }

  protected elegirCupo(c: Cupo): void {
    this.cupo.set(c);
    this.error.set(null);
  }

  protected agendar(): void {
    const p = this.pacienteElegido();
    const c = this.cupo();
    const v = this.form.getRawValue();
    if (!p || !c || !+v.tipoCitaId) {
      this.error.set(!p ? 'Seleccione el paciente.' : 'Seleccione un cupo.');
      return;
    }
    this.guardando.set(true);
    this.error.set(null);
    this.agenda
      .crear({
        pacienteId: p.id,
        profesionalId: c.profesionalId,
        tipoCitaId: +v.tipoCitaId,
        inicio: c.inicio,
        prioridad: v.prioridad,
        motivo: v.motivo.trim() || null,
      })
      .subscribe({
        next: (cita) => this.creada.emit(cita),
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          this.cupo.set(null);
          this.error.set(mensajeError(e, 'No fue posible agendar la cita.'));
        },
      });
  }

  /** RFAGE-007: sin cupo que le sirva, el paciente queda en espera y recibe el primero que se libere. */
  protected listaEspera(): void {
    const p = this.pacienteElegido();
    const v = this.form.getRawValue();
    const prof = this.catalogos().profesionales.find((x) => x.id === +v.profesionalId);
    const especialidadId = +v.especialidadId || prof?.especialidadId;
    if (!p || !especialidadId || !+v.tipoCitaId) {
      this.error.set('Seleccione el paciente, el tipo de cita y la especialidad o el profesional.');
      return;
    }
    this.guardando.set(true);
    this.error.set(null);
    this.agenda
      .agregarListaEspera({
        pacienteId: p.id,
        especialidadId,
        profesionalId: prof?.id ?? null,
        tipoCitaId: +v.tipoCitaId,
        prioridad: v.prioridad,
        motivo: v.motivo.trim() || null,
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.aviso.set(`${p.nombreCompleto} quedó en la lista de espera. Recibirá el primer cupo que se libere.`);
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          this.error.set(mensajeError(e, 'No fue posible agregar a la lista de espera.'));
        },
      });
  }
}
