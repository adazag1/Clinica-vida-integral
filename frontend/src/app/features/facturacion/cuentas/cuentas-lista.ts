import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { mensajeError } from '../../../core/api/api';
import { SelectorPaciente } from '../../documentos/componentes/selector-paciente';
import { PacienteResumen } from '../../pacientes/pacientes.models';
import { CuentaResumen, ESTADOS_CUENTA, EstadoCuenta, claseEstadoCuenta } from '../facturacion.models';
import { FacturacionService } from '../facturacion.service';

/** RFRC-007: cuentas por estado, del primer cargo al cierre. */
@Component({
  selector: 'app-cuentas-lista',
  imports: [CurrencyPipe, DatePipe, FormsModule, RouterLink, SelectorPaciente],
  template: `
    <div class="cabecera-pagina">
      <h2>Facturación</h2>
      <div class="enlaces">
        <a class="enlace" routerLink="/facturacion/cartera"><span class="icono">account_balance_wallet</span> Cartera</a>
        <a class="enlace" routerLink="/facturacion/contratos"><span class="icono">handshake</span> Contratos y tarifas</a>
        <button type="button" class="btn btn--primario btn--pequeno" (click)="abriendo.set(!abriendo())"><span class="icono">add</span>Abrir cuenta</button>
      </div>
    </div>

    @if (abriendo()) {
      <section class="tarjeta apertura">
        <p class="ayuda">Las cuentas se abren solas con el primer cargo clínico. Ábrala aquí si necesita registrar cargos antes (p. ej. un paciente particular).</p>
        <app-selector-paciente (seleccion)="paciente.set($event)" />
        <div class="acciones">
          <button type="button" class="btn btn--primario btn--pequeno" (click)="abrir()" [disabled]="!paciente()">Abrir cuenta</button>
        </div>
      </section>
    }

    <div class="filtros">
      <div class="pildoras">
        <button type="button" [class.activa]="!estado()" (click)="estado.set(null); cargar()">Todas las activas</button>
        @for (e of estados; track e.valor) {
          <button type="button" [class.activa]="estado() === e.valor" (click)="estado.set(e.valor); cargar()">{{ e.etiqueta }}</button>
        }
      </div>
      <input class="control buscar" [(ngModel)]="texto" (keyup.enter)="cargar()" placeholder="Cuenta, paciente, documento o aseguradora" />
    </div>
    @if (error()) { <div class="alerta alerta--error" role="alert"><span class="icono">error</span>{{ error() }}</div> }

    <section class="tarjeta">
      @if (cuentas().length) {
        <div class="tabla-contenedor">
          <table class="tabla">
            <thead><tr><th>Cuenta</th><th>Paciente</th><th>Aseguradora</th><th>Cargos</th><th class="der">Valor</th><th>Estado</th><th></th></tr></thead>
            <tbody>
              @for (c of cuentas(); track c.id) {
                <tr>
                  <td><strong>{{ c.numero }}</strong><br /><small>{{ c.abiertaEn | date: 'dd/MM/yyyy' }}</small></td>
                  <td>{{ c.paciente.nombreCompleto }}<br /><small>{{ c.paciente.tipoDocumento }} {{ c.paciente.numeroDocumento }}</small></td>
                  <td>{{ c.aseguradora }}<br /><small>{{ c.modalidad }}</small></td>
                  <td>{{ c.cargos }}@if (c.sinSoporte) { <span class="chip chip--critico">{{ c.sinSoporte }} sin soporte</span> }</td>
                  <td class="der">{{ c.valorCargos | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}
                    @if (c.saldo !== null) { <br /><small>Saldo {{ c.saldo | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</small> }</td>
                  <td><span class="chip" [class]="'chip ' + clase(c.estado)">{{ c.estadoDescripcion }}</span>
                    @if (c.factura) { <br /><small>{{ c.factura }}</small> }</td>
                  <td><a class="btn btn--secundario btn--pequeno" [routerLink]="['/facturacion/cuentas', c.id]">Abrir</a></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      } @else {
        <p class="vacio">{{ cargando() ? 'Cargando…' : 'No hay cuentas con estos filtros.' }}</p>
      }
    </section>
  `,
  styles: `
    .enlaces { display: flex; flex-wrap: wrap; align-items: center; gap: 14px; }
    .apertura, .alerta { margin-bottom: 14px; }
    .ayuda { color: var(--c-texto-2); font-size: 13px; margin-top: 0; }
    .filtros { display: flex; flex-direction: column; gap: 8px; margin-bottom: 14px; }
    .buscar { max-width: 420px; }
    small { color: var(--c-texto-2); }
    .der { text-align: right; white-space: nowrap; }
    .chip { margin-left: 4px; }
  `,
})
export class CuentasLista {
  private readonly service = inject(FacturacionService);
  private readonly router = inject(Router);

  protected readonly estados = ESTADOS_CUENTA;
  protected readonly clase = claseEstadoCuenta;
  protected readonly estado = signal<EstadoCuenta | null>(null);
  protected readonly cuentas = signal<CuentaResumen[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly abriendo = signal(false);
  protected readonly paciente = signal<PacienteResumen | null>(null);
  protected texto = '';

  constructor() {
    this.cargar();
  }

  protected cargar(): void {
    this.service.cuentas(this.estado(), this.texto).subscribe({
      next: (l) => {
        this.cuentas.set(l);
        this.cargando.set(false);
        this.error.set(null);
      },
      error: (e: HttpErrorResponse) => {
        this.cargando.set(false);
        this.error.set(mensajeError(e, 'No fue posible cargar las cuentas.'));
      },
    });
  }

  protected abrir(): void {
    const p = this.paciente();
    if (!p) return;
    this.service.abrir(p.id).subscribe({
      next: (c) => this.router.navigate(['/facturacion/cuentas', c.resumen.id]),
      error: (e: HttpErrorResponse) => this.error.set(mensajeError(e, 'No fue posible abrir la cuenta.')),
    });
  }
}
