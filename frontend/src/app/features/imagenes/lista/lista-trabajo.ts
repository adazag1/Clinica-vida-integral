import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { interval } from 'rxjs';

import { mensajeError } from '../../../core/api/api';
import {
  EstudioResumen,
  MODALIDADES,
  PRIORIDADES_ESTUDIO,
  VistaTrabajo,
  claseEstadoEstudio,
  clasePrioridad,
} from '../imagenes.models';
import { ImagenesService } from '../imagenes.service';

/** RFRAD-002 / RFRAD-004: lista de trabajo del radiólogo, priorizada por urgencia y tiempo. */
@Component({
  selector: 'app-lista-trabajo',
  imports: [DatePipe, RouterLink],
  template: `
    <div class="cabecera-pagina">
      <h2>Imágenes diagnósticas</h2>
      <a class="enlace" routerLink="/imagenes/configuracion"><span class="icono">tune</span> Plantillas, tiempos y métricas</a>
    </div>

    <div class="filtros">
      <div class="pildoras" role="group" aria-label="Vista">
        @for (v of vistas; track v.valor) {
          <button type="button" [class.activa]="vista() === v.valor" (click)="vista.set(v.valor); cargar()">{{ v.etiqueta }}</button>
        }
      </div>
      <div class="pildoras" role="group" aria-label="Modalidad">
        <button type="button" [class.activa]="!modalidad()" (click)="modalidad.set(null); cargar()">Todas</button>
        @for (m of modalidades; track m.valor) {
          <button type="button" [class.activa]="modalidad() === m.valor" (click)="modalidad.set(m.valor); cargar()">{{ m.etiqueta }}</button>
        }
      </div>
      <div class="pildoras" role="group" aria-label="Prioridad">
        <button type="button" [class.activa]="!prioridad()" (click)="prioridad.set(null); cargar()">Cualquier prioridad</button>
        @for (p of prioridades; track p.valor) {
          <button type="button" [class.activa]="prioridad() === p.valor" (click)="prioridad.set(p.valor); cargar()">{{ p.etiqueta }}</button>
        }
      </div>
    </div>

    @if (error()) {
      <div class="alerta alerta--error" role="alert"><span class="icono">error</span>{{ error() }}</div>
    }

    <section class="tarjeta">
      @if (estudios().length) {
        <div class="tabla-contenedor">
          <table class="tabla">
            <thead>
              <tr><th>Prioridad</th><th>Estudio</th><th>Paciente</th><th>Estado</th><th>Tiempo</th><th>Lectura</th><th></th></tr>
            </thead>
            <tbody>
              @for (e of estudios(); track e.id) {
                <tr [class.fila--vencida]="e.tatVencido" [class.fila--critica]="e.critico">
                  <td><span class="chip" [class]="'chip ' + clasePrioridad(e.prioridad)">{{ e.prioridadDescripcion }}</span></td>
                  <td><strong>{{ e.estudio }}</strong><br /><small>{{ e.numero }} · {{ e.ordenadoEn | date: 'dd/MM h:mm a' }}</small></td>
                  <td>{{ e.paciente.nombreCompleto }}<br /><small>{{ e.paciente.edad !== null ? e.paciente.edad + ' años · ' : '' }}{{ e.medicoNombre }}</small></td>
                  <td>
                    <span class="chip" [class]="'chip ' + claseEstado(e.estado)">{{ e.estadoDescripcion }}</span>
                    @if (e.critico) { <span class="chip chip--critico">Crítico</span> }
                    @if (e.segundaOpinionPendiente) { <span class="chip chip--primario">2.ª lectura para usted</span> }
                  </td>
                  <td class="nowrap">
                    @if (e.informadoEn) {
                      <small>Informado {{ e.informadoEn | date: 'dd/MM h:mm a' }}</small>
                    } @else if (e.tatVencido) {
                      <span class="vencido"><span class="icono">timer_off</span>Tiempo excedido</span>
                    } @else {
                      <small>Antes de {{ e.limiteTat | date: 'dd/MM h:mm a' }}</small>
                    }
                  </td>
                  <td><small>{{ e.asignadoA ?? 'Sin asignar' }} · {{ e.imagenes }} img.</small></td>
                  <td><a class="btn btn--secundario btn--pequeno" [routerLink]="['/imagenes/estudios', e.id]">Abrir</a></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      } @else {
        <p class="vacio">{{ cargando() ? 'Cargando…' : 'No hay estudios con estos filtros.' }}</p>
      }
    </section>
  `,
  styles: `
    .filtros { display: flex; flex-direction: column; gap: 8px; margin-bottom: 14px; }
    .fila--critica td:first-child { box-shadow: inset 4px 0 0 var(--c-critico); }
    .fila--vencida { background: #fff8f0; }
    .vencido { display: inline-flex; align-items: center; gap: 4px; color: var(--c-critico); font-weight: 600; font-size: 13px; }
    .vencido .icono { font-size: 17px; }
    small { color: var(--c-texto-2); }
    .nowrap { white-space: nowrap; }
    .chip + .chip { margin-left: 4px; }
    .alerta { margin-bottom: 14px; }
  `,
})
export class ListaTrabajo {
  private readonly service = inject(ImagenesService);

  protected readonly vistas: { valor: VistaTrabajo; etiqueta: string }[] = [
    { valor: 'PENDIENTES', etiqueta: 'Pendientes' },
    { valor: 'MIOS', etiqueta: 'Mis estudios' },
    { valor: 'INFORMADOS', etiqueta: 'Informados (3 días)' },
  ];
  protected readonly modalidades = MODALIDADES;
  protected readonly prioridades = PRIORIDADES_ESTUDIO;
  protected readonly clasePrioridad = clasePrioridad;
  protected readonly claseEstado = claseEstadoEstudio;

  protected readonly vista = signal<VistaTrabajo>('PENDIENTES');
  protected readonly modalidad = signal<string | null>(null);
  protected readonly prioridad = signal<string | null>(null);
  protected readonly estudios = signal<EstudioResumen[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.cargar();
    interval(30_000).pipe(takeUntilDestroyed(inject(DestroyRef))).subscribe(() => this.cargar());
  }

  protected cargar(): void {
    this.service.listaTrabajo(this.vista(), this.modalidad(), this.prioridad()).subscribe({
      next: (l) => {
        this.estudios.set(l);
        this.cargando.set(false);
        this.error.set(null);
      },
      error: (e: HttpErrorResponse) => {
        this.cargando.set(false);
        this.error.set(mensajeError(e, 'No fue posible cargar la lista de trabajo.'));
      },
    });
  }
}
