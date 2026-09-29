import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { erroresCampos, mensajeError } from '../../../core/api/api';
import { Examen, Rango } from '../laboratorio.models';
import { LaboratorioService } from '../laboratorio.service';

/** RFLC-014: el laboratorio ajusta rangos de referencia y críticos sin desarrollo. */
@Component({
  selector: 'app-rangos-laboratorio',
  imports: [DatePipe, FormsModule, RouterLink],
  template: `
    <a class="volver" routerLink="/laboratorio"><span class="icono">arrow_back</span> Órdenes de laboratorio</a>
    <div class="cabecera-pagina"><h2>Rangos de referencia y valores críticos</h2></div>
    <p class="ayuda">Un campo vacío significa "sin límite" por ese lado. Los cambios aplican a los resultados que se registren
      desde ahora; los ya registrados conservan el rango con que se interpretaron.</p>
    @if (error()) { <div class="alerta alerta--error" role="alert"><span class="icono">error</span>{{ error() }}</div> }
    @if (aviso()) { <div class="alerta alerta--exito" role="status"><span class="icono">check_circle</span>{{ aviso() }}</div> }

    @for (e of examenes(); track e.id) {
      <section class="tarjeta">
        <div class="tarjeta__titulo"><span class="icono">science</span>{{ e.nombre }} <small>· {{ e.areaDescripcion }} · TAT {{ e.tatUrgenteMin }} min urgente / {{ e.tatRutinaMin }} min rutina</small></div>
        <div class="tabla-contenedor">
          <table class="tabla">
            <thead><tr><th>Analito</th><th>Aplica a</th><th>Ref. mín.</th><th>Ref. máx.</th><th>Crítico mín.</th><th>Crítico máx.</th><th>Última modificación</th><th></th></tr></thead>
            <tbody>
              @for (a of e.analitos; track a.id) {
                @for (r of a.rangos; track r.id; let primero = $first) {
                  <tr>
                    <td>@if (primero) { <strong>{{ a.nombre }}</strong> <small>{{ a.unidad }}</small> }</td>
                    <td class="nowrap">{{ r.sexo ? (r.sexo === 'FEMENINO' ? 'Mujeres' : 'Hombres') : 'Todos' }} · {{ r.edadMin }}–{{ r.edadMax === 150 ? '+' : r.edadMax }} años</td>
                    <td><input type="number" step="any" [(ngModel)]="r.refMin" [attr.aria-label]="a.nombre + ' referencia mínima'" /></td>
                    <td><input type="number" step="any" [(ngModel)]="r.refMax" [attr.aria-label]="a.nombre + ' referencia máxima'" /></td>
                    <td><input type="number" step="any" class="critico" [(ngModel)]="r.criticoMin" [attr.aria-label]="a.nombre + ' crítico mínimo'" /></td>
                    <td><input type="number" step="any" class="critico" [(ngModel)]="r.criticoMax" [attr.aria-label]="a.nombre + ' crítico máximo'" /></td>
                    <td><small>{{ r.modificadoPor ? r.modificadoPor + ' · ' + (r.modificadoEn | date: 'dd/MM/yyyy') : 'Valor inicial' }}</small></td>
                    <td><button type="button" class="btn btn--secundario btn--pequeno" (click)="guardar(a.nombre, r)">Guardar</button></td>
                  </tr>
                }
              }
            </tbody>
          </table>
        </div>
      </section>
    }
  `,
  styles: `
    :host { display: flex; flex-direction: column; gap: 14px; }
    .cabecera-pagina { margin-bottom: 0; }
    .ayuda { color: var(--c-texto-2); margin: 0; }
    .alerta { margin: 0; }
    .tarjeta__titulo small { color: var(--c-texto-2); font-weight: 400; font-size: 13px; }
    input { width: 90px; height: 32px; padding: 0 8px; border: 1px solid var(--c-borde); border-radius: 8px; font: inherit; font-size: 13px; }
    input.critico { border-color: #f4b4b4; }
    small { color: var(--c-texto-2); }
    .nowrap { white-space: nowrap; }
  `,
})
export class RangosLaboratorio {
  private readonly service = inject(LaboratorioService);

  protected readonly examenes = signal<Examen[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);

  constructor() {
    // Copia editable del catálogo (no se modifica la versión en caché hasta guardar)
    this.service.catalogo(true).subscribe({
      next: (c) => this.examenes.set(structuredClone(c)),
      error: (e: HttpErrorResponse) => this.error.set(mensajeError(e, 'No fue posible cargar el catálogo.')),
    });
  }

  protected guardar(analito: string, r: Rango): void {
    this.error.set(null);
    this.aviso.set(null);
    const n = (v: number | null) => (v === null || (v as unknown) === '' ? null : +v);
    this.service.actualizarRango(r.id, { refMin: n(r.refMin), refMax: n(r.refMax), criticoMin: n(r.criticoMin), criticoMax: n(r.criticoMax) })
      .subscribe({
        next: (actual) => {
          Object.assign(r, actual);
          this.aviso.set(`Rango de ${analito} actualizado.`);
          this.service.catalogo(true).subscribe();
        },
        error: (e: HttpErrorResponse) => {
          const campos = erroresCampos(e);
          this.error.set(Object.keys(campos).length ? `${analito}: ${Object.values(campos).join('. ')}` : mensajeError(e, 'No fue posible guardar.'));
        },
      });
  }
}
