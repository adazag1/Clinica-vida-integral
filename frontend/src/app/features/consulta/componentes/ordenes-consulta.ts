import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { mensajeError } from '../../../core/api/api';
import { Examen, OrdenResumen, PRIORIDADES_LAB, PrioridadLab, claseEstadoOrden } from '../../laboratorio/laboratorio.models';
import { LaboratorioService } from '../../laboratorio/laboratorio.service';

/** RFMT-006: órdenes de laboratorio del paciente con su estado, y formulario para ordenar desde la consulta. */
@Component({
  selector: 'app-ordenes-consulta',
  imports: [DatePipe, FormsModule, RouterLink],
  template: `
    <div class="titulo-accion">
      <div class="tarjeta__titulo"><span class="icono">science</span>Exámenes de laboratorio</div>
      @if (editable() && !ordenando()) {
        <button type="button" class="btn btn--secundario btn--pequeno" (click)="abrirFormulario()"><span class="icono">add</span>Ordenar exámenes</button>
      }
    </div>

    @if (ordenando()) {
      <div class="formulario">
        <div class="examenes">
          @for (e of catalogo(); track e.id) {
            <label class="check" [title]="e.tipoMuestra">
              <input type="checkbox" [checked]="elegidos.has(e.id)" (change)="marcar(e.id, $any($event.target).checked)" /> {{ e.nombre }}
            </label>
          }
        </div>
        <div class="linea">
          <select [(ngModel)]="prioridad" aria-label="Prioridad">
            @for (p of prioridades; track p.valor) { <option [value]="p.valor">{{ p.etiqueta }}</option> }
          </select>
          <input [(ngModel)]="indicaciones" placeholder="Indicaciones al laboratorio (opcional)" aria-label="Indicaciones" />
        </div>
        @if (error()) { <p class="campo__error">{{ error() }}</p> }
        <div class="acciones">
          <button type="button" class="btn btn--secundario btn--pequeno" (click)="ordenando.set(false)">Cancelar</button>
          <button type="button" class="btn btn--primario btn--pequeno" (click)="ordenar()" [disabled]="ocupado() || !elegidos.size">
            Enviar al laboratorio ({{ elegidos.size }})</button>
        </div>
      </div>
    }

    @for (o of ordenes(); track o.id) {
      <a class="orden" [routerLink]="['/laboratorio/ordenes', o.id]" [class.orden--critica]="o.tieneCriticos && o.estado === 'VALIDADA'">
        <span>
          <strong>{{ o.examenes.join(', ') }}</strong>
          <small>{{ o.numero }} · {{ o.ordenadaEn | date: 'dd/MM h:mm a' }} · {{ o.prioridadDescripcion }}</small>
        </span>
        <span class="chip" [class]="'chip ' + clase(o.estado)">{{ o.estadoDescripcion }}</span>
        @if (o.tieneCriticos && o.estado === 'VALIDADA') { <span class="chip chip--critico">Crítico</span> }
      </a>
    } @empty {
      @if (!ordenando()) { <p class="vacio">Sin órdenes de laboratorio.</p> }
    }
  `,
  styles: `
    .titulo-accion { display: flex; justify-content: space-between; align-items: flex-start; }
    .formulario { padding: 12px; border: 1px dashed var(--c-borde); border-radius: var(--radio-sm); margin-bottom: 12px; }
    .examenes { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 8px; margin-bottom: 10px; font-size: 13.5px; }
    .linea { display: flex; flex-wrap: wrap; gap: 8px; }
    .linea select, .linea input { height: 36px; padding: 0 10px; border: 1px solid var(--c-borde); border-radius: 8px; font: inherit; font-size: 13.5px; }
    .linea input { flex: 1; min-width: 200px; }
    .acciones { margin-top: 10px; }
    .orden { display: flex; align-items: center; gap: 8px; padding: 10px 12px; border: 1px solid var(--c-borde); border-radius: var(--radio-sm);
      margin-bottom: 8px; color: inherit; }
    .orden:hover { border-color: var(--c-azul); }
    .orden > span:first-child { flex: 1; display: flex; flex-direction: column; min-width: 0; }
    .orden small { color: var(--c-texto-2); }
    .orden--critica { border-color: var(--c-critico); background: var(--c-critico-suave); }
    .vacio { margin: 0; color: var(--c-texto-3); font-size: 13.5px; }
  `,
})
export class OrdenesConsulta implements OnInit {
  private readonly service = inject(LaboratorioService);

  readonly pacienteId = input.required<number>();
  readonly consultaId = input<number | null>(null);
  readonly editable = input(false);

  protected readonly prioridades = PRIORIDADES_LAB;
  protected readonly clase = claseEstadoOrden;
  protected readonly ordenes = signal<OrdenResumen[]>([]);
  protected readonly catalogo = signal<Examen[]>([]);
  protected readonly ordenando = signal(false);
  protected readonly ocupado = signal(false);
  protected readonly error = signal<string | null>(null);

  protected elegidos = new Set<number>();
  protected prioridad: PrioridadLab = 'AMBULATORIO';
  protected indicaciones = '';

  ngOnInit(): void {
    this.cargar();
  }

  private cargar(): void {
    this.service.dePaciente(this.pacienteId()).subscribe({ next: (o) => this.ordenes.set(o) });
  }

  protected abrirFormulario(): void {
    this.service.catalogo().subscribe((c) => this.catalogo.set(c));
    this.elegidos = new Set();
    this.indicaciones = '';
    this.error.set(null);
    this.ordenando.set(true);
  }

  /** Toma el estado real de la casilla, para que la selección nunca se desincronice de lo que se ve. */
  protected marcar(id: number, marcado: boolean): void {
    const nuevo = new Set(this.elegidos);
    if (marcado) nuevo.add(id);
    else nuevo.delete(id);
    this.elegidos = nuevo;
  }

  protected ordenar(): void {
    this.ocupado.set(true);
    this.service
      .crearOrden({ pacienteId: this.pacienteId(), consultaId: this.consultaId(), prioridad: this.prioridad,
        examenIds: [...this.elegidos], indicaciones: this.indicaciones.trim() || null })
      .subscribe({
        next: () => {
          this.ocupado.set(false);
          this.ordenando.set(false);
          this.cargar();
        },
        error: (e: HttpErrorResponse) => {
          this.ocupado.set(false);
          this.error.set(mensajeError(e, 'No fue posible enviar la orden.'));
        },
      });
  }
}
