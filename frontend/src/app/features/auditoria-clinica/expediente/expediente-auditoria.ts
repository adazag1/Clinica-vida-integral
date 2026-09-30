import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { erroresCampos, mensajeError } from '../../../core/api/api';
import { AuthService } from '../../../core/auth/auth.service';
import { claseEstadoCuenta } from '../../facturacion/facturacion.models';
import { DESTINOS, Expediente, MOTIVOS_DEVOLUCION, Sugerencia } from '../auditoria.models';
import { AuditoriaClinicaService } from '../auditoria.service';

/** RFAU-002 a RFAU-009: el auditor revisa la atención completa, codifica y aprueba o devuelve, sin cambiar de pantalla. */
@Component({
  selector: 'app-expediente-auditoria',
  imports: [CurrencyPipe, DatePipe, FormsModule, RouterLink],
  templateUrl: './expediente-auditoria.html',
  styleUrl: './expediente-auditoria.scss',
})
export class ExpedienteAuditoria {
  private readonly service = inject(AuditoriaClinicaService);
  private readonly auth = inject(AuthService);

  /** Viene de la ruta /auditoria-clinica/cuentas/:id. */
  readonly id = input.required<string>();

  protected readonly clase = claseEstadoCuenta;
  protected readonly motivos = MOTIVOS_DEVOLUCION;
  protected readonly destinos = DESTINOS;
  protected readonly expediente = signal<Expediente | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  protected readonly ocupado = signal(false);
  protected readonly devolviendo = signal(false);
  protected readonly seccion = signal<'consultas' | 'ordenes' | 'documentos'>('consultas');

  protected readonly esAuditor = computed(() => this.auth.usuario()?.rol === 'AUDITORIA');
  protected readonly enAuditoria = computed(() => ['EN_AUDITORIA', 'DEVUELTA'].includes(this.expediente()?.cuenta.resumen.estado ?? ''));
  protected readonly aprobable = computed(() => (this.expediente()?.verificacion ?? []).every((v) => v.cumple));

  protected principal = '';
  protected relacionados: string[] = [];
  protected nuevoRelacionado = '';
  protected motivoCodificacion = '';
  protected devolucion = { motivo: 'SOPORTE_FALTANTE', detalle: '', dirigidaA: 'FACTURACION' };

  constructor() {
    effect(() => this.cargar(Number(this.id())));
  }

  private cargar(id: number): void {
    this.service.expediente(id).subscribe({
      next: (e) => this.mostrar(e),
      error: (e: HttpErrorResponse) => this.error.set(mensajeError(e, 'No fue posible cargar el expediente.')),
    });
  }

  private mostrar(e: Expediente): void {
    this.expediente.set(e);
    this.principal = e.codificacion.diagnosticoPrincipal ?? '';
    this.relacionados = [...e.codificacion.relacionados];
    this.motivoCodificacion = '';
  }

  protected usar(s: Sugerencia): void {
    if (!this.principal) this.principal = s.codigo;
    else if (s.codigo !== this.principal && !this.relacionados.includes(s.codigo) && this.relacionados.length < 4) this.relacionados.push(s.codigo);
  }

  protected agregarRelacionado(): void {
    const c = this.nuevoRelacionado.trim().toUpperCase();
    if (c && !this.relacionados.includes(c) && c !== this.principal && this.relacionados.length < 4) this.relacionados.push(c);
    this.nuevoRelacionado = '';
  }

  protected quitarRelacionado(c: string): void {
    this.relacionados = this.relacionados.filter((x) => x !== c);
  }

  protected yaCodificada(): boolean {
    return !!this.expediente()?.codificacion.diagnosticoPrincipal;
  }

  protected guardarCodificacion(): void {
    const e = this.expediente();
    if (!e) return;
    this.ocupado.set(true);
    this.error.set(null);
    this.service.codificar(e.cuenta.resumen.id, this.principal.trim().toUpperCase(), this.relacionados, this.motivoCodificacion.trim() || null).subscribe({
      next: () => {
        this.ocupado.set(false);
        this.aviso.set('Codificación guardada.');
        this.cargar(e.cuenta.resumen.id);
      },
      error: (err: HttpErrorResponse) => this.fallo(err, 'No fue posible guardar la codificación.'),
    });
  }

  protected aprobar(): void {
    const e = this.expediente();
    if (!e) return;
    this.ocupado.set(true);
    this.service.aprobar(e.cuenta.resumen.id).subscribe({
      next: (r) => {
        this.ocupado.set(false);
        this.mostrar(r);
        this.aviso.set('Cuenta aprobada. Facturación recibió el aviso para emitir la factura.');
      },
      error: (err: HttpErrorResponse) => this.fallo(err, 'No fue posible aprobar.'),
    });
  }

  protected elegirMotivo(m: string): void {
    this.devolucion.motivo = m;
    this.devolucion.dirigidaA = this.motivos.find((x) => x.valor === m)?.destino ?? 'FACTURACION';
  }

  protected devolver(): void {
    const e = this.expediente();
    if (!e) return;
    const d = this.devolucion;
    this.ocupado.set(true);
    this.service.devolver(e.cuenta.resumen.id, d.motivo, d.detalle.trim(), d.dirigidaA).subscribe({
      next: (r) => {
        this.ocupado.set(false);
        this.devolviendo.set(false);
        this.mostrar(r);
        this.aviso.set('Cuenta devuelta. Quien debe corregirla recibió el aviso.');
      },
      error: (err: HttpErrorResponse) => this.fallo(err, 'No fue posible devolver la cuenta.'),
    });
  }

  private fallo(e: HttpErrorResponse, porDefecto: string): void {
    this.ocupado.set(false);
    const campos = erroresCampos(e);
    this.error.set(Object.keys(campos).length ? Object.values(campos).join('. ') : mensajeError(e, porDefecto));
  }
}
