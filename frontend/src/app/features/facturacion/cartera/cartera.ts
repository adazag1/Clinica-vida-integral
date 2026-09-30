import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { mensajeError } from '../../../core/api/api';
import { Cartera as CarteraDatos } from '../facturacion.models';
import { FacturacionService } from '../facturacion.service';

/** RFRC-010: cartera por edades y por aseguradora; las alertas llegan a la campana al cruzar 30, 60 y 90 días. */
@Component({
  selector: 'app-cartera',
  imports: [CurrencyPipe, DatePipe, RouterLink],
  template: `
    <a class="volver" routerLink="/facturacion"><span class="icono">arrow_back</span> Cuentas</a>
    <div class="cabecera-pagina"><h2>Cartera</h2></div>
    @if (error()) { <div class="alerta alerta--error" role="alert"><span class="icono">error</span>{{ error() }}</div> }

    @if (cartera(); as c) {
      <div class="indicadores">
        <div class="tarjeta"><strong>{{ c.porCobrar | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</strong><span>por cobrar</span></div>
        <div class="tarjeta" [class.malo]="c.vencido > 0"><strong>{{ c.vencido | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</strong><span>vencido</span></div>
        <div class="tarjeta"><strong>{{ c.facturas.length }}</strong><span>facturas con saldo</span></div>
      </div>

      <div class="rejilla">
        <section class="tarjeta">
          <div class="tarjeta__titulo"><span class="icono">hourglass_bottom</span>Por edad</div>
          @for (r of c.rangos; track r.rango; let i = $index) {
            <div class="barra">
              <span>{{ r.rango }} <small>({{ r.facturas }})</small></span>
              <span class="fondo"><span class="relleno" [class]="'relleno relleno--' + i" [style.width.%]="porcentaje(r.saldo, c.porCobrar)"></span></span>
              <strong>{{ r.saldo | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</strong>
            </div>
          }
        </section>
        <section class="tarjeta">
          <div class="tarjeta__titulo"><span class="icono">apartment</span>Por aseguradora</div>
          @for (a of c.porAseguradora; track a.aseguradora) {
            <div class="aseguradora">
              <span><strong>{{ a.aseguradora }}</strong><small>{{ a.facturas }} factura(s)</small></span>
              <span class="der">{{ a.saldo | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}
                @if (a.vencido > 0) { <small class="mal">{{ a.vencido | currency: 'COP' : 'symbol-narrow' : '1.0-0' }} vencido</small> }</span>
            </div>
          } @empty { <p class="vacio">Sin cartera pendiente.</p> }
        </section>
      </div>

      <section class="tarjeta">
        <div class="tarjeta__titulo"><span class="icono">list</span>Facturas con saldo</div>
        <div class="tabla-contenedor">
          <table class="tabla">
            <thead><tr><th>Factura</th><th>Aseguradora</th><th>Paciente</th><th>Vence</th><th class="der">Saldo</th><th>Mora</th><th></th></tr></thead>
            <tbody>
              @for (f of c.facturas; track f.id) {
                <tr [class.fila--vencida]="f.diasVencida > 0">
                  <td><strong>{{ f.numero }}</strong><br /><small>{{ f.emitida | date: 'dd/MM/yyyy' }}</small></td>
                  <td>{{ f.aseguradora }}</td>
                  <td>{{ f.paciente }}</td>
                  <td>{{ f.vence | date: 'dd/MM/yyyy' }}</td>
                  <td class="der">{{ f.saldo | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}<br /><small>de {{ f.total | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</small></td>
                  <td>
                    @if (f.diasVencida > 0) { <span class="chip" [class]="'chip ' + (f.diasVencida > 60 ? 'chip--critico' : 'chip--alerta')">{{ f.diasVencida }} días</span> }
                    @else { <span class="chip chip--normal">Al día</span> }
                    @if (f.glosasPendientes) { <span class="chip chip--info">{{ f.glosasPendientes }} glosa(s)</span> }
                  </td>
                  <td><a class="btn btn--secundario btn--pequeno" [routerLink]="['/facturacion/cuentas', f.cuentaId]">Ver</a></td>
                </tr>
              } @empty {
                <tr><td colspan="7" class="vacio">No hay facturas con saldo.</td></tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    }
  `,
  styles: `
    .alerta { margin-bottom: 12px; }
    .indicadores { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 12px; margin-bottom: 14px; }
    .indicadores .tarjeta { display: flex; flex-direction: column; }
    .indicadores strong { font-size: 24px; }
    .indicadores span { color: var(--c-texto-2); font-size: 13px; }
    .malo strong { color: var(--c-critico); }
    .rejilla { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 14px; margin-bottom: 14px; }
    .barra { display: grid; grid-template-columns: 150px 1fr 120px; align-items: center; gap: 10px; margin-bottom: 10px; font-size: 13px; }
    .barra strong { text-align: right; }
    .fondo { height: 12px; border-radius: 999px; background: var(--c-fondo); overflow: hidden; }
    .relleno { display: block; height: 100%; border-radius: 999px; background: var(--c-normal); }
    .relleno--1 { background: #84cc16; } .relleno--2 { background: var(--c-alerta); } .relleno--3 { background: #ea580c; } .relleno--4 { background: var(--c-critico); }
    .aseguradora { display: flex; justify-content: space-between; gap: 10px; padding: 8px 0; border-bottom: 1px solid var(--c-borde); }
    .aseguradora > span { display: flex; flex-direction: column; }
    .der { text-align: right; white-space: nowrap; }
    small { color: var(--c-texto-2); }
    .mal { color: var(--c-critico) !important; }
    .fila--vencida { background: #fff8f0; }
    .chip + .chip { margin-left: 4px; }
  `,
})
export class Cartera {
  private readonly service = inject(FacturacionService);

  protected readonly cartera = signal<CarteraDatos | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.service.cartera().subscribe({
      next: (c) => this.cartera.set(c),
      error: (e: HttpErrorResponse) => this.error.set(mensajeError(e, 'No fue posible cargar la cartera.')),
    });
  }

  protected porcentaje(v: number, total: number): number {
    return total > 0 ? (100 * v) / total : 0;
  }
}
