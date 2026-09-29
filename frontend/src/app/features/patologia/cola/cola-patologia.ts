import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { interval } from 'rxjs';

import { mensajeError } from '../../../core/api/api';
import { clasePrioridad } from '../../imagenes/imagenes.models';
import { CasoResumen, TIPOS_CASO, claseEstadoCaso } from '../patologia.models';
import { PatologiaService } from '../patologia.service';

/** RFPAT-003: los intraoperatorios en una cola aparte, arriba y en rojo; luego urgentes y rutina. */
@Component({
  selector: 'app-cola-patologia',
  imports: [DatePipe, RouterLink],
  template: `
    <div class="cabecera-pagina">
      <h2>Patología</h2>
      <a class="enlace" routerLink="/patologia/plantillas"><span class="icono">tune</span> Plantillas de informe</a>
    </div>
    <div class="filtros">
      <div class="pildoras">
        <button type="button" [class.activa]="vista() === 'PENDIENTES'" (click)="vista.set('PENDIENTES'); cargar()">Pendientes</button>
        <button type="button" [class.activa]="vista() === 'INFORMADOS'" (click)="vista.set('INFORMADOS'); cargar()">Informados (7 días)</button>
      </div>
      <div class="pildoras">
        <button type="button" [class.activa]="!tipo()" (click)="tipo.set(null); cargar()">Todos</button>
        @for (t of tipos; track t.valor) {
          <button type="button" [class.activa]="tipo() === t.valor" (click)="tipo.set(t.valor); cargar()">{{ t.etiqueta }}</button>
        }
      </div>
    </div>
    @if (error()) { <div class="alerta alerta--error" role="alert"><span class="icono">error</span>{{ error() }}</div> }

    @if (intraoperatorios().length) {
      <section class="tarjeta intraop">
        <div class="tarjeta__titulo"><span class="icono">emergency</span>Intraoperatorios: el cirujano espera el resultado</div>
        @for (c of intraoperatorios(); track c.id) {
          <a class="fila" [routerLink]="['/patologia/casos', c.id]">
            <strong>{{ c.numero }}</strong><span>{{ c.paciente.nombreCompleto }} · {{ c.sitioAnatomico }}</span>
            <span class="chip" [class]="'chip ' + claseEstado(c.estado)">{{ c.estadoDescripcion }}</span>
            <small [class.malo]="c.vencido">{{ c.vencido ? 'Tiempo excedido' : 'Antes de ' + (c.limiteRespuesta | date: 'h:mm a') }}</small>
          </a>
        }
      </section>
    }

    <section class="tarjeta">
      @if (resto().length) {
        <div class="tabla-contenedor">
          <table class="tabla">
            <thead><tr><th>Prioridad</th><th>Caso</th><th>Paciente</th><th>Muestra</th><th>Estado</th><th>Tiempo</th><th></th></tr></thead>
            <tbody>
              @for (c of resto(); track c.id) {
                <tr [class.fila--vencida]="c.vencido">
                  <td><span class="chip" [class]="'chip ' + clasePrioridad(c.prioridad)">{{ c.prioridadDescripcion }}</span></td>
                  <td><strong>{{ c.numero }}</strong><br /><small>{{ c.solicitadoEn | date: 'dd/MM h:mm a' }}</small></td>
                  <td>{{ c.paciente.nombreCompleto }}<br /><small>{{ c.medicoNombre }}</small></td>
                  <td>{{ c.tipoDescripcion }}<br /><small>{{ c.sitioAnatomico }}</small></td>
                  <td><span class="chip" [class]="'chip ' + claseEstado(c.estado)">{{ c.estadoDescripcion }}</span>
                    @if (c.diagnostico) { <br /><small>{{ c.diagnostico }}</small> }</td>
                  <td class="nowrap"><small [class.malo]="c.vencido">
                    {{ c.validadoEn ? 'Informado ' + (c.validadoEn | date: 'dd/MM h:mm a') : c.vencido ? 'Tiempo excedido' : 'Antes de ' + (c.limiteRespuesta | date: 'dd/MM h:mm a') }}</small></td>
                  <td><a class="btn btn--secundario btn--pequeno" [routerLink]="['/patologia/casos', c.id]">Abrir</a></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      } @else if (!intraoperatorios().length) {
        <p class="vacio">{{ cargando() ? 'Cargando…' : 'No hay casos con estos filtros.' }}</p>
      }
    </section>
  `,
  styles: `
    .filtros { display: flex; flex-direction: column; gap: 8px; margin-bottom: 14px; }
    .intraop { border: 2px solid var(--c-critico); margin-bottom: 14px; }
    .intraop .tarjeta__titulo { color: var(--c-critico); }
    .fila { display: flex; align-items: center; gap: 12px; padding: 10px 12px; margin-top: 8px; border-radius: var(--radio-sm);
      background: var(--c-critico-suave); color: inherit; flex-wrap: wrap; }
    .fila > span:nth-child(2) { flex: 1; }
    .fila--vencida { background: #fff8f0; }
    small { color: var(--c-texto-2); }
    .malo { color: var(--c-critico); font-weight: 600; }
    .nowrap { white-space: nowrap; }
    .alerta { margin-bottom: 14px; }
  `,
})
export class ColaPatologia {
  private readonly service = inject(PatologiaService);

  protected readonly tipos = TIPOS_CASO;
  protected readonly clasePrioridad = clasePrioridad;
  protected readonly claseEstado = claseEstadoCaso;
  protected readonly vista = signal<'PENDIENTES' | 'INFORMADOS'>('PENDIENTES');
  protected readonly tipo = signal<string | null>(null);
  protected readonly casos = signal<CasoResumen[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly intraoperatorios = computed(() =>
    this.vista() === 'PENDIENTES' ? this.casos().filter((c) => c.prioridad === 'INTRAOPERATORIO') : [],
  );
  protected readonly resto = computed(() =>
    this.vista() === 'PENDIENTES' ? this.casos().filter((c) => c.prioridad !== 'INTRAOPERATORIO') : this.casos(),
  );

  constructor() {
    this.cargar();
    interval(20_000).pipe(takeUntilDestroyed(inject(DestroyRef))).subscribe(() => this.cargar());
  }

  protected cargar(): void {
    this.service.cola(this.vista(), this.tipo()).subscribe({
      next: (l) => {
        this.casos.set(l);
        this.cargando.set(false);
        this.error.set(null);
      },
      error: (e: HttpErrorResponse) => {
        this.cargando.set(false);
        this.error.set(mensajeError(e, 'No fue posible cargar los casos.'));
      },
    });
  }
}
