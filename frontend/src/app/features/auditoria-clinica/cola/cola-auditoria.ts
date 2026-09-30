import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { mensajeError } from '../../../core/api/api';
import { CuentaResumen, claseEstadoCuenta } from '../../facturacion/facturacion.models';
import { AuditoriaClinicaService } from '../auditoria.service';

/** Cuentas que esperan auditoría clínica, las devueltas y las ya auditadas. */
@Component({
  selector: 'app-cola-auditoria',
  imports: [CurrencyPipe, DatePipe, RouterLink],
  template: `
    <div class="cabecera-pagina">
      <h2>Auditoría clínica</h2>
      <div class="enlaces">
        <a class="enlace" routerLink="/auditoria-clinica/tablero"><span class="icono">monitoring</span> Indicadores</a>
        <a class="enlace" routerLink="/auditoria-clinica/catalogo"><span class="icono">menu_book</span> Catálogo CIE-10</a>
      </div>
    </div>
    <div class="pildoras filtros">
      @for (v of vistas; track v.valor) {
        <button type="button" [class.activa]="vista() === v.valor" (click)="vista.set(v.valor); cargar()">{{ v.etiqueta }}</button>
      }
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
                  <td>{{ c.aseguradora }}</td>
                  <td>{{ c.cargos }}@if (c.sinSoporte) { <span class="chip chip--critico">{{ c.sinSoporte }} sin soporte</span> }</td>
                  <td class="der">{{ c.valorCargos | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</td>
                  <td><span class="chip" [class]="'chip ' + clase(c.estado)">{{ c.estadoDescripcion }}</span></td>
                  <td><a class="btn btn--secundario btn--pequeno" [routerLink]="['/auditoria-clinica/cuentas', c.id]">Auditar</a></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      } @else {
        <p class="vacio">{{ cargando() ? 'Cargando…' : 'No hay cuentas en esta lista.' }}</p>
      }
    </section>
  `,
  styles: `
    .enlaces { display: flex; gap: 14px; }
    .filtros, .alerta { margin-bottom: 14px; }
    small { color: var(--c-texto-2); }
    .der { text-align: right; }
    .chip { margin-left: 4px; }
  `,
})
export class ColaAuditoria {
  private readonly service = inject(AuditoriaClinicaService);

  protected readonly clase = claseEstadoCuenta;
  protected readonly vistas = [
    { valor: 'EN_AUDITORIA', etiqueta: 'Por auditar' },
    { valor: 'DEVUELTA', etiqueta: 'Devueltas' },
    { valor: 'AUDITADA', etiqueta: 'Auditadas, por facturar' },
  ];
  protected readonly vista = signal('EN_AUDITORIA');
  protected readonly cuentas = signal<CuentaResumen[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.cargar();
  }

  protected cargar(): void {
    this.service.cola(this.vista()).subscribe({
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
}
