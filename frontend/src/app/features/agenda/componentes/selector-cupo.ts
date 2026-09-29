import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';

import { mensajeError } from '../../../core/api/api';
import { Cupo, PrioridadCita, deFechaLocal, fechaLocal } from '../agenda.models';
import { AgendaService } from '../agenda.service';

const DIAS_POR_PAGINA = 7;

/** RFAGE-003: cupos libres agrupados por día, para elegir con un clic (RNFAGE-003 pocos pasos). */
@Component({
  selector: 'app-selector-cupo',
  imports: [DatePipe],
  template: `
    <div class="navegacion">
      <button type="button" class="btn btn--secundario btn--pequeno" (click)="mover(-1)" [disabled]="!puedeRetroceder()"
        aria-label="Semana anterior"><span class="icono">chevron_left</span></button>
      <span>{{ desde() | date: "d 'de' MMMM" }} – {{ hasta() | date: "d 'de' MMMM" }}</span>
      <button type="button" class="btn btn--secundario btn--pequeno" (click)="mover(1)" aria-label="Semana siguiente">
        <span class="icono">chevron_right</span></button>
    </div>

    @if (cargando()) {
      <p class="nota">Buscando cupos…</p>
    } @else if (error()) {
      <p class="campo__error">{{ error() }}</p>
    } @else if (!dias().length) {
      <p class="nota">No hay cupos libres en estos días. Pruebe la semana siguiente o agregue al paciente a la lista de espera.</p>
    } @else {
      @for (d of dias(); track d.fecha) {
        <div class="dia">
          <strong>{{ d.fecha | date: "EEEE d 'de' MMMM" }}</strong>
          <div class="cupos">
            @for (c of d.cupos; track c.inicio + c.profesionalId) {
              <button type="button" class="cupo" [class.cupo--reservado]="c.reservado"
                [class.cupo--activo]="seleccion()?.inicio === c.inicio && seleccion()?.profesionalId === c.profesionalId"
                [attr.title]="c.profesional + (c.reservado ? ' · cupo reservado para prioridades' : '')"
                (click)="elegir(c)">
                {{ c.inicio | date: 'h:mm a' }}
                @if (mostrarProfesional()) {
                  <small>{{ c.profesional }}</small>
                }
              </button>
            }
          </div>
        </div>
      }
    }
  `,
  styles: `
    .navegacion { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 12px; font-weight: 600; }
    .dia { margin-bottom: 14px; }
    .dia strong::first-letter { text-transform: uppercase; }
    .cupos { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; }
    .cupo {
      display: inline-flex; flex-direction: column; align-items: center; min-width: 76px; padding: 6px 10px;
      border: 1px solid var(--c-borde); border-radius: 8px; background: var(--c-superficie); font: inherit;
      font-size: 13px; font-weight: 600; cursor: pointer; color: var(--c-texto);
      small { font-weight: 400; font-size: 11px; color: var(--c-texto-2); }
      &:hover { border-color: var(--c-primario); }
    }
    .cupo--reservado { background: var(--c-alerta-suave); border-color: #f5cf8f; }
    .cupo--activo { background: var(--c-primario); border-color: var(--c-primario); color: #fff; small { color: #e6fffd; } }
    .nota { color: var(--c-texto-2); font-size: 13px; }
  `,
})
export class SelectorCupo {
  private readonly service = inject(AgendaService);

  readonly tipoCitaId = input.required<number | null>();
  readonly prioridad = input<PrioridadCita>('NORMAL');
  readonly profesionalId = input<number | null>(null);
  readonly especialidadId = input<number | null>(null);
  /** Primer día a mostrar (YYYY-MM-DD). */
  readonly fechaInicial = input<string>(fechaLocal(new Date()));
  readonly seleccion = input<Cupo | null>(null);
  readonly elegido = output<Cupo>();

  protected readonly desde = signal<Date>(new Date());
  protected readonly hasta = computed(() => {
    const d = new Date(this.desde());
    d.setDate(d.getDate() + DIAS_POR_PAGINA - 1);
    return d;
  });
  private readonly cupos = signal<Cupo[]>([]);
  protected readonly cargando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly mostrarProfesional = computed(() => !this.profesionalId());

  protected readonly dias = computed(() => {
    const grupos = new Map<string, Cupo[]>();
    for (const c of this.cupos()) {
      const dia = fechaLocal(new Date(c.inicio));
      grupos.set(dia, [...(grupos.get(dia) ?? []), c]);
    }
    return [...grupos.entries()].map(([fecha, cupos]) => ({ fecha: deFechaLocal(fecha), cupos }));
  });

  constructor() {
    effect(() => {
      const hoy = new Date();
      hoy.setHours(0, 0, 0, 0);
      const inicial = deFechaLocal(this.fechaInicial());
      untracked(() => this.desde.set(inicial < hoy ? hoy : inicial));
    });
    effect(() => {
      const tipo = this.tipoCitaId();
      const profesional = this.profesionalId();
      const especialidad = this.especialidadId();
      const prioridad = this.prioridad();
      const desde = this.desde();
      untracked(() => this.cargar(tipo, profesional, especialidad, prioridad, desde));
    });
  }

  protected puedeRetroceder(): boolean {
    const hoy = new Date();
    hoy.setHours(0, 0, 0, 0);
    return this.desde() > hoy;
  }

  protected mover(semanas: number): void {
    const d = new Date(this.desde());
    d.setDate(d.getDate() + semanas * DIAS_POR_PAGINA);
    const hoy = new Date();
    hoy.setHours(0, 0, 0, 0);
    this.desde.set(d < hoy ? hoy : d);
  }

  protected elegir(c: Cupo): void {
    this.elegido.emit(c);
  }

  private cargar(tipo: number | null, profesionalId: number | null, especialidadId: number | null,
    prioridad: PrioridadCita, desde: Date): void {
    if (!tipo || (!profesionalId && !especialidadId)) {
      this.cupos.set([]);
      this.error.set('Seleccione el tipo de cita y un profesional o especialidad.');
      return;
    }
    this.cargando.set(true);
    this.error.set(null);
    this.service
      .disponibilidad({ profesionalId, especialidadId: profesionalId ? null : especialidadId, tipoCitaId: tipo, prioridad,
        desde: fechaLocal(desde), dias: DIAS_POR_PAGINA })
      .subscribe({
        next: (cupos) => {
          this.cupos.set(cupos);
          this.cargando.set(false);
        },
        error: (e: HttpErrorResponse) => {
          this.error.set(mensajeError(e, 'No fue posible consultar la disponibilidad.'));
          this.cargando.set(false);
        },
      });
  }
}
