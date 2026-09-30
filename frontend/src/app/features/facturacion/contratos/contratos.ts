import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { erroresCampos, mensajeError } from '../../../core/api/api';
import { AuthService } from '../../../core/auth/auth.service';
import { Contrato, Servicio, TIPOS_REGLA, TIPOS_SERVICIO } from '../facturacion.models';
import { FacturacionService } from '../facturacion.service';

interface ContratoEdicion {
  id: number | null;
  aseguradora: string;
  nombre: string;
  modalidad: string;
  factorTarifa: number;
  diasPago: number;
  topeCuenta: number | null;
  vigenteDesde: string;
  vigenteHasta: string | null;
  activo: boolean;
}

/** RFRC-005 / RFRC-006: contratos por aseguradora (modalidad, factor, reglas y paquetes) y tarifas base de los servicios. */
@Component({
  selector: 'app-contratos',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, FormsModule, RouterLink],
  template: `
    <a class="volver" routerLink="/facturacion"><span class="icono">arrow_back</span> Cuentas</a>
    <div class="cabecera-pagina">
      <h2>Contratos y tarifas</h2>
      <div class="pildoras">
        <button type="button" [class.activa]="vista() === 'contratos'" (click)="vista.set('contratos')">Contratos</button>
        <button type="button" [class.activa]="vista() === 'servicios'" (click)="vista.set('servicios')">Servicios y tarifas base</button>
      </div>
    </div>
    @if (error()) { <div class="alerta alerta--error" role="alert"><span class="icono">error</span>{{ error() }}</div> }
    @if (aviso()) { <div class="alerta alerta--exito" role="status"><span class="icono">check_circle</span>{{ aviso() }}</div> }

    @if (vista() === 'contratos') {
      @if (puedeEditar()) {
        <button type="button" class="btn btn--secundario btn--pequeno nuevo" (click)="editarContrato(null)"><span class="icono">add</span>Nuevo contrato</button>
      }
      @if (contratoEditado) {
        <section class="tarjeta panel">
          <div class="tarjeta__titulo">{{ contratoEditado.id ? 'Editar contrato' : 'Nuevo contrato' }}</div>
          <div class="form-rejilla">
            <label>Aseguradora<input class="control" [(ngModel)]="contratoEditado.aseguradora" [disabled]="!!contratoEditado.id" /></label>
            <label class="ancho">Nombre<input class="control" [(ngModel)]="contratoEditado.nombre" /></label>
            <label>Modalidad<select class="control" [(ngModel)]="contratoEditado.modalidad"><option value="EVENTO">Por evento</option><option value="CAPITACION">Capitación</option></select></label>
            <label>Factor sobre tarifa base<input class="control" type="number" step="0.01" [(ngModel)]="contratoEditado.factorTarifa" /></label>
            <label>Días de pago<input class="control" type="number" [(ngModel)]="contratoEditado.diasPago" /></label>
            <label>Tope por cuenta (opcional)<input class="control" type="number" [(ngModel)]="contratoEditado.topeCuenta" /></label>
            <label>Vigente desde<input class="control" type="date" [(ngModel)]="contratoEditado.vigenteDesde" /></label>
            <label>Vigente hasta<input class="control" type="date" [(ngModel)]="contratoEditado.vigenteHasta" /></label>
            <label class="check"><input type="checkbox" [(ngModel)]="contratoEditado.activo" /> Activo</label>
          </div>
          <div class="acciones">
            <button type="button" class="btn btn--secundario btn--pequeno" (click)="contratoEditado = null">Cancelar</button>
            <button type="button" class="btn btn--primario btn--pequeno" (click)="guardarContrato()">Guardar</button>
          </div>
        </section>
      }

      @for (c of contratos(); track c.id) {
        <section class="tarjeta contrato" [class.inactivo]="!c.activo">
          <div class="titulo-accion">
            <div>
              <div class="tarjeta__titulo">{{ c.aseguradora }} · {{ c.nombre }}</div>
              <p class="datos">{{ c.modalidadDescripcion }} · factor {{ c.factorTarifa | number: '1.2-3' }} · pago a {{ c.diasPago }} días
                {{ c.topeCuenta ? ' · tope ' + (c.topeCuenta | currency: 'COP' : 'symbol-narrow' : '1.0-0') : '' }}
                · desde {{ c.vigenteDesde | date: 'dd/MM/yyyy' }}{{ c.vigenteHasta ? ' hasta ' + (c.vigenteHasta | date: 'dd/MM/yyyy') : '' }}
                {{ c.activo ? '' : ' · INACTIVO' }}</p>
            </div>
            @if (puedeEditar()) { <button type="button" class="enlace" (click)="editarContrato(c)">Editar</button> }
          </div>

          <h4>Reglas</h4>
          @for (r of c.reglas; track r.id) {
            <p class="regla"><span class="chip chip--info">{{ r.tipoDescripcion }}</span> {{ r.servicioCodigo }} · {{ r.servicioNombre }}
              {{ r.valor !== null ? '· ' + (r.valor | currency: 'COP' : 'symbol-narrow' : '1.0-0') : '' }}{{ r.cantidadMax ? '· máximo ' + r.cantidadMax : '' }}
              @if (puedeEditar()) { <button type="button" class="enlace" (click)="quitarRegla(c, r.id)">quitar</button> }</p>
          } @empty { <p class="vacio">Sin reglas especiales: aplica el factor a todos los servicios.</p> }
          @if (puedeEditar()) {
            @if (reglaEn() === c.id) {
              <div class="form-rejilla fila-form">
                <label>Servicio<select class="control" [(ngModel)]="regla.servicioId">
                  @for (s of servicios(); track s.id) { <option [ngValue]="s.id">{{ s.codigo }} · {{ s.nombre }}</option> }</select></label>
                <label>Tipo<select class="control" [(ngModel)]="regla.tipo">
                  @for (t of tiposRegla; track t.valor) { <option [value]="t.valor">{{ t.etiqueta }}</option> }</select></label>
                @if (regla.tipo === 'TARIFA_PACTADA') { <label>Tarifa<input class="control" type="number" [(ngModel)]="regla.valor" /></label> }
                @if (regla.tipo === 'TOPE_CANTIDAD') { <label>Máximo<input class="control" type="number" [(ngModel)]="regla.cantidadMax" /></label> }
              </div>
              <div class="acciones"><button type="button" class="btn btn--primario btn--pequeno" (click)="agregarRegla(c)" [disabled]="!regla.servicioId">Agregar regla</button></div>
            } @else {
              <button type="button" class="enlace" (click)="nuevaRegla(c)">+ Regla</button>
            }
          }

          @if (c.modalidad === 'EVENTO') {
            <h4>Paquetes</h4>
            @for (p of c.paquetes; track p.id) {
              <p class="regla"><strong>{{ p.codigo }}</strong> · {{ p.nombre }} · {{ p.precio | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}<br />
                <small>Incluye: {{ p.servicios.join(' · ') }}</small>
                @if (puedeEditar()) { <button type="button" class="enlace" (click)="quitarPaquete(c, p.id)">quitar</button> }</p>
            } @empty { <p class="vacio">Sin paquetes.</p> }
            @if (puedeEditar()) {
              @if (paqueteEn() === c.id) {
                <div class="form-rejilla fila-form">
                  <label>Código<input class="control" [(ngModel)]="paquete.codigo" /></label>
                  <label class="ancho">Nombre<input class="control" [(ngModel)]="paquete.nombre" /></label>
                  <label>Precio<input class="control" type="number" [(ngModel)]="paquete.precio" /></label>
                  <label class="ancho">Servicios incluidos (Ctrl + clic para varios)
                    <select class="control" multiple size="6" [(ngModel)]="paquete.servicioIds">
                      @for (s of servicios(); track s.id) { <option [ngValue]="s.id">{{ s.codigo }} · {{ s.nombre }}</option> }
                    </select></label>
                </div>
                <div class="acciones"><button type="button" class="btn btn--primario btn--pequeno" (click)="agregarPaquete(c)">Agregar paquete</button></div>
              } @else {
                <button type="button" class="enlace" (click)="nuevoPaquete(c)">+ Paquete</button>
              }
            }
          }
        </section>
      }
    } @else {
      @if (puedeEditar()) {
        <button type="button" class="btn btn--secundario btn--pequeno nuevo" (click)="editarServicio(null)"><span class="icono">add</span>Nuevo servicio</button>
      }
      @if (servicioEditado) {
        <section class="tarjeta panel">
          <div class="form-rejilla">
            <label>Código<input class="control" [(ngModel)]="servicioEditado.codigo" [disabled]="!!servicioEditado.id" /></label>
            <label class="ancho">Nombre<input class="control" [(ngModel)]="servicioEditado.nombre" /></label>
            <label>Tipo<select class="control" [(ngModel)]="servicioEditado.tipo" [disabled]="!!servicioEditado.id">
              @for (t of tiposServicio; track t.valor) { <option [value]="t.valor">{{ t.etiqueta }}</option> }</select></label>
            <label>Tarifa base<input class="control" type="number" [(ngModel)]="servicioEditado.tarifaBase" /></label>
            <label class="check"><input type="checkbox" [(ngModel)]="servicioEditado.requiereAutorizacion" /> Exige autorización</label>
            <label class="check"><input type="checkbox" [(ngModel)]="servicioEditado.activo" /> Activo</label>
          </div>
          <div class="acciones">
            <button type="button" class="btn btn--secundario btn--pequeno" (click)="servicioEditado = null">Cancelar</button>
            <button type="button" class="btn btn--primario btn--pequeno" (click)="guardarServicio()">Guardar</button>
          </div>
        </section>
      }
      <section class="tarjeta">
        <input class="control buscar" [ngModel]="filtro()" (ngModelChange)="filtro.set($event)" placeholder="Buscar servicio" />
        <div class="tabla-contenedor">
          <table class="tabla">
            <thead><tr><th>Código</th><th>Servicio</th><th>Tipo</th><th class="der">Tarifa base</th><th></th></tr></thead>
            <tbody>
              @for (s of serviciosFiltrados(); track s.id) {
                <tr [class.inactivo]="!s.activo">
                  <td>{{ s.codigo }}</td>
                  <td>{{ s.nombre }}@if (s.requiereAutorizacion) { <span class="chip chip--info">Autorización</span> }
                    @if (s.claveOrigen) { <br /><small>Cargo automático: {{ s.claveOrigen }}</small> }</td>
                  <td>{{ s.tipoDescripcion }}</td>
                  <td class="der">{{ s.tarifaBase | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</td>
                  <td>@if (puedeEditar()) { <button type="button" class="enlace" (click)="editarServicio(s)">Editar</button> }</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    }
  `,
  styles: `
    .alerta, .nuevo, .panel { margin-bottom: 12px; }
    .contrato { margin-bottom: 14px; }
    .inactivo { opacity: 0.6; }
    .datos { margin: 2px 0 0; color: var(--c-texto-2); font-size: 13px; }
    h4 { margin: 14px 0 6px; font-size: 13.5px; }
    .regla { margin: 0 0 6px; font-size: 13.5px; }
    .regla .chip { margin-right: 6px; font-size: 11px; }
    .fila-form { margin: 8px 0; }
    .buscar { max-width: 360px; margin-bottom: 10px; }
    .der { text-align: right; }
    small { color: var(--c-texto-2); }
    .tabla .chip { margin-left: 6px; font-size: 11px; }
  `,
})
export class Contratos {
  private readonly service = inject(FacturacionService);
  private readonly auth = inject(AuthService);

  protected readonly tiposRegla = TIPOS_REGLA;
  protected readonly tiposServicio = TIPOS_SERVICIO;
  protected readonly puedeEditar = computed(() => this.auth.tieneRol('FACTURACION'));
  protected readonly vista = signal<'contratos' | 'servicios'>('contratos');
  protected readonly contratos = signal<Contrato[]>([]);
  protected readonly servicios = signal<Servicio[]>([]);
  protected readonly filtro = signal('');
  protected readonly serviciosFiltrados = computed(() => {
    const t = this.filtro().trim().toLowerCase();
    return t ? this.servicios().filter((s) => (s.codigo + ' ' + s.nombre).toLowerCase().includes(t)) : this.servicios();
  });
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  protected readonly reglaEn = signal<number | null>(null);
  protected readonly paqueteEn = signal<number | null>(null);

  protected contratoEditado: ContratoEdicion | null = null;
  protected servicioEditado: { id: number | null; codigo: string; nombre: string; tipo: string; tarifaBase: number; requiereAutorizacion: boolean; activo: boolean } | null = null;
  protected regla = { servicioId: null as number | null, tipo: 'TARIFA_PACTADA', valor: null as number | null, cantidadMax: null as number | null };
  protected paquete = { codigo: '', nombre: '', precio: null as number | null, servicioIds: [] as number[] };

  constructor() {
    this.service.contratos().subscribe((c) => this.contratos.set(c));
    this.service.servicios().subscribe((s) => this.servicios.set(s));
  }

  protected editarContrato(c: Contrato | null): void {
    this.contratoEditado = c
      ? { id: c.id, aseguradora: c.aseguradora, nombre: c.nombre, modalidad: c.modalidad, factorTarifa: c.factorTarifa, diasPago: c.diasPago,
        topeCuenta: c.topeCuenta, vigenteDesde: c.vigenteDesde, vigenteHasta: c.vigenteHasta, activo: c.activo }
      : { id: null, aseguradora: '', nombre: '', modalidad: 'EVENTO', factorTarifa: 1, diasPago: 30, topeCuenta: null,
        vigenteDesde: new Date().toLocaleDateString('en-CA'), vigenteHasta: null, activo: true };
  }

  protected guardarContrato(): void {
    const e = this.contratoEditado;
    if (!e) return;
    const { id, ...datos } = e;
    this.actualizar(this.service.guardarContrato(id, { ...datos, vigenteHasta: datos.vigenteHasta || null, topeCuenta: datos.topeCuenta || null }),
      'Contrato guardado.', () => (this.contratoEditado = null));
  }

  protected nuevaRegla(c: Contrato): void {
    this.regla = { servicioId: null, tipo: 'TARIFA_PACTADA', valor: null, cantidadMax: null };
    this.reglaEn.set(c.id);
  }

  protected agregarRegla(c: Contrato): void {
    this.actualizar(this.service.agregarRegla(c.id, { ...this.regla, servicioId: this.regla.servicioId! }), 'Regla agregada.', () => this.reglaEn.set(null));
  }

  protected quitarRegla(c: Contrato, id: number): void {
    this.actualizar(this.service.quitarRegla(c.id, id), 'Regla quitada.');
  }

  protected nuevoPaquete(c: Contrato): void {
    this.paquete = { codigo: '', nombre: '', precio: null, servicioIds: [] };
    this.paqueteEn.set(c.id);
  }

  protected agregarPaquete(c: Contrato): void {
    const p = this.paquete;
    this.actualizar(this.service.agregarPaquete(c.id, { codigo: p.codigo.trim(), nombre: p.nombre.trim(), precio: Number(p.precio), servicioIds: p.servicioIds }),
      'Paquete agregado.', () => this.paqueteEn.set(null));
  }

  protected quitarPaquete(c: Contrato, id: number): void {
    this.actualizar(this.service.quitarPaquete(c.id, id), 'Paquete quitado.');
  }

  private actualizar(obs: Observable<Contrato>, exito: string, despues?: () => void): void {
    this.error.set(null);
    obs.subscribe({
      next: (c) => {
        this.contratos.update((l) => (l.some((x) => x.id === c.id) ? l.map((x) => (x.id === c.id ? c : x)) : [...l, c]));
        this.aviso.set(exito);
        despues?.();
      },
      error: (e: HttpErrorResponse) => {
        const campos = erroresCampos(e);
        this.error.set(Object.keys(campos).length ? Object.values(campos).join('. ') : mensajeError(e, 'No se pudo guardar.'));
      },
    });
  }

  protected editarServicio(s: Servicio | null): void {
    this.servicioEditado = s
      ? { id: s.id, codigo: s.codigo, nombre: s.nombre, tipo: s.tipo, tarifaBase: s.tarifaBase, requiereAutorizacion: s.requiereAutorizacion, activo: s.activo }
      : { id: null, codigo: '', nombre: '', tipo: 'PROCEDIMIENTO', tarifaBase: 0, requiereAutorizacion: false, activo: true };
  }

  protected guardarServicio(): void {
    const s = this.servicioEditado;
    if (!s) return;
    const { id, ...datos } = s;
    this.service.guardarServicio(id, { ...datos, tarifaBase: Number(datos.tarifaBase) }).subscribe({
      next: (r) => {
        this.servicios.update((l) => (l.some((x) => x.id === r.id) ? l.map((x) => (x.id === r.id ? r : x)) : [...l, r]));
        this.servicioEditado = null;
        this.aviso.set('Servicio guardado; el cambio quedó en el historial del catálogo.');
      },
      error: (e: HttpErrorResponse) => {
        const campos = erroresCampos(e);
        this.error.set(Object.keys(campos).length ? Object.values(campos).join('. ') : mensajeError(e, 'No se pudo guardar.'));
      },
    });
  }
}
