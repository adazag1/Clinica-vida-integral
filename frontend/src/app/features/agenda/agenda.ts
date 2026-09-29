import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { forkJoin, interval } from 'rxjs';

import { mensajeError } from '../../core/api/api';
import { AuthService } from '../../core/auth/auth.service';
import { PacienteResumen } from '../pacientes/pacientes.models';
import { PacientesService } from '../pacientes/pacientes.service';
import {
  AgendaDia,
  Catalogos,
  Cita,
  ColumnaAgenda,
  ESTADOS_CITA,
  Indicadores,
  deFechaLocal,
  fechaLocal,
} from './agenda.models';
import { AgendaService } from './agenda.service';
import { DetalleCita } from './componentes/detalle-cita';
import { MiniCalendario } from './componentes/mini-calendario';
import { NuevaCita } from './componentes/nueva-cita';

/** Alto en píxeles de cada minuto en la grilla del día. */
const PX_POR_MINUTO = 2;
const REFRESCO_MS = 60_000;

type Seleccion =
  | { tipo: 'cita'; cita: Cita }
  | { tipo: 'nueva'; profesionalId: number | null; fecha: string; paciente: PacienteResumen | null }
  | null;

/** Agenda del día por profesional (mockup "Agendamiento y Citas"). */
@Component({
  selector: 'app-agenda',
  imports: [DatePipe, DecimalPipe, FormsModule, RouterLink, MiniCalendario, NuevaCita, DetalleCita],
  templateUrl: './agenda.html',
  styleUrl: './agenda.scss',
})
export class Agenda implements OnInit {
  private readonly service = inject(AgendaService);
  private readonly pacientes = inject(PacientesService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly auth = inject(AuthService);

  /** Parámetros de la URL: ?fecha=AAAA-MM-DD, ?cita=15 (desde avisos) o ?paciente=3 (agendar a ese paciente). */
  readonly fechaParam = input<string>(undefined, { alias: 'fecha' });
  readonly citaParam = input<string>(undefined, { alias: 'cita' });
  readonly pacienteParam = input<string>(undefined, { alias: 'paciente' });

  protected readonly estados = ESTADOS_CITA;
  protected readonly catalogos = signal<Catalogos | null>(null);
  protected readonly fecha = signal(fechaLocal(new Date()));
  protected readonly dia = signal<AgendaDia | null>(null);
  protected readonly indicadores = signal<Indicadores | null>(null);
  protected readonly seleccion = signal<Seleccion>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly cargando = signal(false);

  protected filtros = { sedeId: 0, especialidadId: 0, profesionalId: 0, estado: '', tipoCitaId: 0, canceladas: false };

  protected readonly puedeAgendar = computed(() => this.auth.tieneRol('AGENDAMIENTO', 'ADMISIONES'));
  protected readonly esCoordinacion = computed(() => this.auth.tieneRol('AGENDAMIENTO'));
  protected readonly fechaDate = computed(() => deFechaLocal(this.fecha()));
  protected readonly esHoy = computed(() => this.fecha() === fechaLocal(new Date()));

  /** Rango de horas de la grilla: de la primera a la última franja del día (mínimo 7:00 a 18:00). */
  protected readonly rango = computed(() => {
    let desde = 7 * 60;
    let hasta = 18 * 60;
    for (const col of this.dia()?.profesionales ?? []) {
      for (const f of col.franjas) {
        desde = Math.min(desde, this.minutos(f.inicio));
        hasta = Math.max(hasta, this.minutos(f.fin));
      }
    }
    return { desde: Math.floor(desde / 60) * 60, hasta: Math.ceil(hasta / 60) * 60 };
  });
  protected readonly horas = computed(() => {
    const r = this.rango();
    return Array.from({ length: (r.hasta - r.desde) / 60 }, (_, i) => r.desde / 60 + i);
  });
  protected readonly altoGrilla = computed(() => (this.rango().hasta - this.rango().desde) * PX_POR_MINUTO);
  protected readonly pxHora = 60 * PX_POR_MINUTO;

  ngOnInit(): void {
    if (this.fechaParam()) this.fecha.set(this.fechaParam()!);
    this.service.catalogos().subscribe({
      next: (c) => {
        this.catalogos.set(c);
        // Un médico ve su propia agenda por defecto
        if (c.miProfesionalId && this.auth.usuario()?.rol === 'MEDICO') this.filtros.profesionalId = c.miProfesionalId;
        this.cargar();
        this.abrirDesdeUrl();
      },
      error: (e: HttpErrorResponse) => this.error.set(mensajeError(e, 'No fue posible cargar la agenda.')),
    });
    interval(REFRESCO_MS).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.cargar(true));
  }

  private abrirDesdeUrl(): void {
    const citaId = Number(this.citaParam());
    if (citaId) {
      this.service.cita(citaId).subscribe({
        next: (c) => {
          this.fecha.set(fechaLocal(new Date(c.inicio)));
          this.seleccion.set({ tipo: 'cita', cita: c });
          this.cargar();
        },
      });
    }
    const pacienteId = Number(this.pacienteParam());
    if (pacienteId && this.puedeAgendar()) {
      this.pacientes.obtener(pacienteId).subscribe({
        next: (p) => this.seleccion.set({ tipo: 'nueva', profesionalId: null, fecha: this.fecha(), paciente: p }),
      });
    }
  }

  protected cargar(silencioso = false): void {
    if (!silencioso) this.cargando.set(true);
    const f = this.filtros;
    forkJoin({
      dia: this.service.dia(this.fecha(), {
        sedeId: f.sedeId || null,
        especialidadId: f.especialidadId || null,
        profesionalId: f.profesionalId || null,
      }),
      indicadores: this.service.indicadores(this.fecha(), f.sedeId || null),
    }).subscribe({
      next: ({ dia, indicadores }) => {
        this.dia.set(dia);
        this.indicadores.set(indicadores);
        this.error.set(null);
        this.cargando.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.error.set(mensajeError(e, 'No fue posible cargar la agenda.'));
        this.cargando.set(false);
      },
    });
  }

  protected irA(fecha: string): void {
    this.fecha.set(fecha);
    this.router.navigate([], { queryParams: { fecha }, replaceUrl: true });
    this.cargar();
  }

  protected moverDia(dias: number): void {
    const d = this.fechaDate();
    this.irA(fechaLocal(new Date(d.getFullYear(), d.getMonth(), d.getDate() + dias)));
  }

  protected limpiarFiltros(): void {
    this.filtros = { sedeId: 0, especialidadId: 0, profesionalId: 0, estado: '', tipoCitaId: 0, canceladas: false };
    this.cargar();
  }

  protected profesionalesFiltro() {
    const c = this.catalogos();
    if (!c) return [];
    return c.profesionales.filter(
      (p) => (!+this.filtros.sedeId || p.sedeId === +this.filtros.sedeId)
        && (!+this.filtros.especialidadId || p.especialidadId === +this.filtros.especialidadId),
    );
  }

  /** Estado y tipo se filtran en pantalla; sede, especialidad y profesional en el servidor. */
  protected citasVisibles(col: ColumnaAgenda): Cita[] {
    const f = this.filtros;
    return col.citas.filter(
      (c) => (f.canceladas || f.estado === 'CANCELADA' || c.estado !== 'CANCELADA')
        && (!f.estado || c.estado === f.estado)
        && (!+f.tipoCitaId || c.tipo.id === +f.tipoCitaId),
    );
  }

  protected ocupacion(profesionalId: number): number | null {
    return this.indicadores()?.carga.find((c) => c.profesionalId === profesionalId)?.ocupacion ?? null;
  }

  // ---------- posiciones en la grilla ----------

  private minutos(iso: string): number {
    const d = new Date(iso);
    const inicioDia = this.fechaDate();
    return Math.round((d.getTime() - inicioDia.getTime()) / 60_000);
  }

  protected topHora(hora: number): number {
    return (hora * 60 - this.rango().desde) * PX_POR_MINUTO;
  }

  protected hoy(): string {
    return fechaLocal(new Date());
  }

  protected top(iso: string): number {
    return (this.minutos(iso) - this.rango().desde) * PX_POR_MINUTO;
  }

  protected alto(inicio: string, fin: string): number {
    return Math.max(18, (this.minutos(fin) - this.minutos(inicio)) * PX_POR_MINUTO - 2);
  }

  protected claseCita(c: Cita): string {
    return 'cita cita--' + c.estado.toLowerCase()
      + (c.prioridad !== 'NORMAL' ? ' cita--prioridad' : '')
      // Citas cortas: solo hora y paciente, para que el nombre no quede cortado
      + (this.alto(c.inicio, c.fin) < 56 ? ' cita--compacta' : '');
  }

  protected iconoEstado(estado: string): string {
    return (
      { CONFIRMADA: 'check_circle', PROGRAMADA: 'schedule', ATENDIDA: 'how_to_reg', NO_ASISTIO: 'person_off', CANCELADA: 'cancel' } as Record<string, string>
    )[estado] ?? 'schedule';
  }

  // ---------- selección ----------

  protected verCita(c: Cita): void {
    this.service.cita(c.id).subscribe({ next: (detalle) => this.seleccion.set({ tipo: 'cita', cita: detalle }) });
  }

  protected nuevaCita(profesionalId: number | null): void {
    if (!this.puedeAgendar()) return;
    const actual = this.seleccion();
    const paciente = actual?.tipo === 'nueva' ? actual.paciente : null;
    this.seleccion.set({ tipo: 'nueva', profesionalId, fecha: this.fecha(), paciente });
  }

  protected citaCreada(c: Cita): void {
    this.seleccion.set({ tipo: 'cita', cita: c });
    if (fechaLocal(new Date(c.inicio)) !== this.fecha()) {
      this.irA(fechaLocal(new Date(c.inicio)));
    } else {
      this.cargar(true);
    }
  }

  protected citaCambio(c: Cita): void {
    this.seleccion.set({ tipo: 'cita', cita: c });
    this.cargar(true);
  }

  protected cerrarPanel(): void {
    this.seleccion.set(null);
    this.router.navigate([], { queryParams: { fecha: this.fecha() }, replaceUrl: true });
  }

  protected seleccionadaId(): number | null {
    const s = this.seleccion();
    return s?.tipo === 'cita' ? s.cita.id : null;
  }
}
