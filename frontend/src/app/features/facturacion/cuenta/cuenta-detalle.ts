import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { erroresCampos, mensajeError } from '../../../core/api/api';
import { AuthService } from '../../../core/auth/auth.service';
import { CONCEPTOS_GLOSA, Cargo, Cuenta, Glosa, Servicio, claseEstadoCuenta } from '../facturacion.models';
import { FacturacionService } from '../facturacion.service';

type Panel = 'cargo' | 'autorizacion' | 'glosa' | 'pago' | null;

/** Cuenta de una atención: cargos, simulación, factura, glosas, pagos e historial (RFRC-003 a RFRC-014). */
@Component({
  selector: 'app-cuenta-detalle',
  imports: [CurrencyPipe, DatePipe, FormsModule, RouterLink],
  templateUrl: './cuenta-detalle.html',
  styleUrl: './cuenta-detalle.scss',
})
export class CuentaDetalle {
  private readonly service = inject(FacturacionService);
  private readonly auth = inject(AuthService);

  /** Viene de la ruta /facturacion/cuentas/:id. */
  readonly id = input.required<string>();

  protected readonly clase = claseEstadoCuenta;
  protected readonly conceptos = CONCEPTOS_GLOSA;
  protected readonly pasos = ['ABIERTA', 'EN_AUDITORIA', 'AUDITADA', 'FACTURADA', 'PAGADA', 'CERRADA'];
  protected readonly nombresPasos = ['Cargos', 'Auditoría', 'Por facturar', 'Facturada', 'Pagada', 'Cerrada'];

  protected readonly cuenta = signal<Cuenta | null>(null);
  protected readonly servicios = signal<Servicio[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  protected readonly ocupado = signal(false);
  protected readonly panel = signal<Panel>(null);
  protected readonly cargoEditando = signal<{ id: number; accion: 'soporte' | 'anular' } | null>(null);
  protected readonly glosaRespondiendo = signal<number | null>(null);
  protected readonly verAnulados = signal(false);

  protected readonly esFacturacion = computed(() => this.auth.tieneRol('FACTURACION'));
  protected readonly editable = computed(() => ['ABIERTA', 'DEVUELTA'].includes(this.cuenta()?.resumen.estado ?? ''));
  protected readonly cargosVisibles = computed(() => (this.cuenta()?.cargos ?? []).filter((c) => this.verAnulados() || c.estado === 'ACTIVO'));
  protected readonly anulados = computed(() => (this.cuenta()?.cargos ?? []).filter((c) => c.estado === 'ANULADO').length);
  protected readonly glosasPendientes = computed(() => (this.cuenta()?.glosas ?? []).filter((g) => g.estado === 'RADICADA').length);

  protected cargo = { servicioId: null as number | null, cantidad: 1, soporte: '' };
  protected textoCargo = '';
  protected numeroAutorizacion = '';
  protected respuestaDevolucion = '';
  protected glosa = { concepto: 'SOPORTES', codigo: '', descripcion: '', valor: null as number | null, cargoId: null as number | null, recibidaEn: '' };
  protected respuestaGlosa = { valorAceptado: 0, respuesta: '' };
  protected pago = { valor: null as number | null, fecha: '', medio: 'Transferencia', referencia: '' };

  constructor() {
    effect(() => this.cargar(Number(this.id())));
  }

  private cargar(id: number): void {
    this.service.obtener(id).subscribe({
      next: (c) => this.cuenta.set(c),
      error: (e: HttpErrorResponse) => this.error.set(mensajeError(e, 'No fue posible cargar la cuenta.')),
    });
  }

  protected paso(): number {
    const e = this.cuenta()?.resumen.estado ?? 'ABIERTA';
    const mapa: Record<string, number> = { ABIERTA: 0, DEVUELTA: 0, EN_AUDITORIA: 1, AUDITADA: 2, FACTURADA: 3, GLOSADA: 3, PAGADA: 4, CERRADA: 5 };
    return mapa[e] ?? 0;
  }

  protected hoy(): string {
    return new Date().toLocaleDateString('en-CA');
  }

  protected abrir(p: Panel): void {
    this.error.set(null);
    if (p === 'cargo' && !this.servicios().length) this.service.servicios().subscribe((s) => this.servicios.set(s.filter((x) => x.activo)));
    if (p === 'glosa') this.glosa = { concepto: 'SOPORTES', codigo: '', descripcion: '', valor: null, cargoId: null, recibidaEn: this.hoy() };
    if (p === 'pago') this.pago = { valor: this.cuenta()?.factura?.saldo ?? null, fecha: this.hoy(), medio: 'Transferencia', referencia: '' };
    this.panel.set(this.panel() === p ? null : p);
  }

  protected serviciosFiltrados(): Servicio[] {
    const t = this.textoCargo.trim().toLowerCase();
    return (t ? this.servicios().filter((s) => (s.codigo + ' ' + s.nombre).toLowerCase().includes(t)) : this.servicios()).slice(0, 40);
  }

  protected agregarCargo(): void {
    const c = this.cuenta();
    if (!c || !this.cargo.servicioId) return;
    this.ejecutar(this.service.agregarCargo(c.resumen.id, { servicioId: this.cargo.servicioId, cantidad: Number(this.cargo.cantidad),
      soporte: this.cargo.soporte.trim() || null, soporteEnlace: null }), 'Cargo agregado.', () => {
      this.cargo = { servicioId: null, cantidad: 1, soporte: '' };
      this.textoCargo = '';
    });
  }

  protected editarCargo(c: Cargo, accion: 'soporte' | 'anular'): void {
    this.textoAccionCargo = '';
    this.cargoEditando.set(this.cargoEditando()?.id === c.id && this.cargoEditando()?.accion === accion ? null : { id: c.id, accion });
  }

  protected textoAccionCargo = '';

  protected confirmarCargo(): void {
    const c = this.cuenta();
    const e = this.cargoEditando();
    if (!c || !e || !this.textoAccionCargo.trim()) return;
    const obs = e.accion === 'soporte'
      ? this.service.registrarSoporte(c.resumen.id, e.id, this.textoAccionCargo.trim())
      : this.service.anularCargo(c.resumen.id, e.id, this.textoAccionCargo.trim());
    this.ejecutar(obs, e.accion === 'soporte' ? 'Soporte registrado.' : 'Cargo anulado (queda en el historial).', () => this.cargoEditando.set(null));
  }

  protected registrarAutorizacion(): void {
    const c = this.cuenta();
    if (c) this.ejecutar(this.service.autorizacion(c.resumen.id, this.numeroAutorizacion.trim()), 'Autorización registrada.');
  }

  protected enviarAAuditoria(): void {
    const c = this.cuenta();
    if (c) this.ejecutar(this.service.enviarAAuditoria(c.resumen.id, this.respuestaDevolucion.trim() || null), 'Cuenta enviada a auditoría clínica.',
      () => (this.respuestaDevolucion = ''));
  }

  protected facturar(): void {
    const c = this.cuenta();
    if (c && confirm('¿Emitir la factura? Después ya no se modifican los cargos.')) {
      this.ejecutar(this.service.facturar(c.resumen.id), 'Factura emitida.');
    }
  }

  protected descargarXml(): void {
    const c = this.cuenta();
    if (!c) return;
    this.service.xml(c.resumen.id).subscribe((blob) => {
      const a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = (c.factura?.numero ?? 'factura') + '.xml';
      a.click();
      setTimeout(() => URL.revokeObjectURL(a.href), 10_000);
    });
  }

  protected registrarGlosa(): void {
    const c = this.cuenta();
    const g = this.glosa;
    if (!c || !g.valor) return;
    this.ejecutar(this.service.registrarGlosa(c.resumen.id, { concepto: g.concepto, codigo: g.codigo.trim(), descripcion: g.descripcion.trim(),
      valor: Number(g.valor), cargoId: g.cargoId, recibidaEn: g.recibidaEn }), 'Glosa registrada.');
  }

  protected responder(g: Glosa): void {
    this.respuestaGlosa = { valorAceptado: 0, respuesta: '' };
    this.glosaRespondiendo.set(this.glosaRespondiendo() === g.id ? null : g.id);
  }

  protected confirmarRespuesta(g: Glosa): void {
    const c = this.cuenta();
    if (c) this.ejecutar(this.service.responderGlosa(c.resumen.id, g.id, Number(this.respuestaGlosa.valorAceptado), this.respuestaGlosa.respuesta.trim()),
      'Respuesta registrada.', () => this.glosaRespondiendo.set(null));
  }

  protected registrarPago(): void {
    const c = this.cuenta();
    const p = this.pago;
    if (c && p.valor) this.ejecutar(this.service.registrarPago(c.resumen.id, { valor: Number(p.valor), fecha: p.fecha, medio: p.medio.trim(),
      referencia: p.referencia.trim() || null }), 'Pago registrado.');
  }

  protected cerrar(): void {
    const c = this.cuenta();
    if (c) this.ejecutar(this.service.cerrar(c.resumen.id), 'Cuenta cerrada.');
  }

  private ejecutar(obs: Observable<Cuenta>, exito: string, despues?: () => void): void {
    this.ocupado.set(true);
    this.aviso.set(null);
    this.error.set(null);
    obs.subscribe({
      next: (c) => {
        this.ocupado.set(false);
        this.cuenta.set(c);
        this.panel.set(null);
        this.aviso.set(exito);
        despues?.();
      },
      error: (e: HttpErrorResponse) => {
        this.ocupado.set(false);
        const campos = erroresCampos(e);
        this.error.set(Object.keys(campos).length ? Object.values(campos).join('. ') : mensajeError(e, 'No fue posible completar la acción.'));
      },
    });
  }
}
