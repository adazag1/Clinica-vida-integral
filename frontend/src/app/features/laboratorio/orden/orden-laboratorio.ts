import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { mensajeError } from '../../../core/api/api';
import { AuthService } from '../../../core/auth/auth.service';
import {
  AREAS_LAB,
  MOTIVOS_RECHAZO,
  MOTIVOS_REPROCESO,
  Orden,
  ResultadoFila,
  claseEstadoOrden,
  claseInterpretacion,
} from '../laboratorio.models';
import { LaboratorioService } from '../laboratorio.service';

type Accion = null | 'muestra' | 'rechazar' | 'validar' | { repetir: ResultadoFila };

/** Pantalla "Laboratorio Clínico" del mockup: orden, estado de la muestra y resultados. */
@Component({
  selector: 'app-orden-laboratorio',
  imports: [DatePipe, DecimalPipe, FormsModule, RouterLink],
  templateUrl: './orden-laboratorio.html',
  styleUrl: './orden-laboratorio.scss',
})
export class OrdenLaboratorio implements OnInit {
  private readonly service = inject(LaboratorioService);
  private readonly auth = inject(AuthService);

  readonly id = input.required<string>();

  protected readonly orden = signal<Orden | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  protected readonly ocupado = signal(false);
  protected readonly accion = signal<Accion>(null);

  protected readonly motivosRechazo = MOTIVOS_RECHAZO;
  protected readonly motivosReproceso = MOTIVOS_REPROCESO;
  protected readonly claseInterp = claseInterpretacion;
  protected readonly claseEstado = claseEstadoOrden;

  /** El laboratorio procesa y valida; el médico y enfermería ven el estado y los resultados. */
  protected readonly esLaboratorio = computed(() => this.auth.tieneRol('LABORATORIO'));
  protected readonly puedeTomarMuestra = computed(() => this.auth.tieneRol('LABORATORIO', 'ENFERMERIA'));
  protected readonly editable = computed(() => this.esLaboratorio() && ['MUESTRA_RECIBIDA', 'EN_PROCESO'].includes(this.orden()?.estado ?? ''));

  /** Pasos del "Estado de muestra" del mockup. */
  protected readonly pasos = computed(() => {
    const o = this.orden();
    if (!o) return [];
    const rechazada = o.estado === 'RECHAZADA';
    return [
      { titulo: 'Orden', fecha: o.ordenadaEn, hecho: true, icono: 'assignment' },
      { titulo: rechazada ? 'Muestra rechazada' : 'Muestra recibida', fecha: o.muestraRecibidaEn, hecho: !!o.muestraRecibidaEn || rechazada,
        icono: rechazada ? 'cancel' : 'check', rechazo: rechazada },
      { titulo: 'En proceso', fecha: o.procesoEn, hecho: !!o.procesoEn, icono: 'science' },
      { titulo: 'Validada', fecha: o.validadaEn, hecho: !!o.validadaEn, icono: 'verified' },
    ];
  });

  // Formularios
  protected valores: Record<number, number | null> = {};
  protected trazabilidad: Record<number, { equipo: string; loteReactivo: string; calibracion: string }> = {};
  protected muestra = { tomadaPor: '', tomadaEn: '' };
  protected rechazo = { motivo: 'HEMOLIZADA', detalle: '' };
  protected repeticion = { valor: null as number | null, motivo: 'CONFIRMAR_CRITICO', detalle: '' };
  protected validacion = { contacto: '', medio: 'Llamada telefónica' };

  ngOnInit(): void {
    this.cargar();
  }

  private cargar(): void {
    this.service.obtener(+this.id()).subscribe({
      next: (o) => this.aplicar(o),
      error: (e: HttpErrorResponse) => this.error.set(mensajeError(e, 'No fue posible cargar la orden.')),
    });
  }

  private aplicar(o: Orden, mensaje?: string): void {
    this.orden.set(o);
    this.accion.set(null);
    this.ocupado.set(false);
    this.aviso.set(mensaje ?? null);
    this.valores = {};
    for (const e of o.examenes) {
      for (const r of e.resultados) this.valores[r.analitoId] = r.valor;
      this.trazabilidad[e.id] = {
        equipo: e.equipo ?? AREAS_LAB.find((a) => a.valor === e.area)?.equipo ?? '',
        loteReactivo: e.loteReactivo ?? '',
        calibracion: e.calibracion ?? '',
      };
    }
  }

  private ejecutar(peticion: Observable<Orden>, mensaje: string): void {
    this.ocupado.set(true);
    this.error.set(null);
    peticion.subscribe({
      next: (o) => this.aplicar(o, mensaje),
      error: (e: HttpErrorResponse) => {
        this.ocupado.set(false);
        this.error.set(mensajeError(e, 'No fue posible completar la acción.'));
      },
    });
  }

  protected abrir(a: Accion): void {
    this.error.set(null);
    this.aviso.set(null);
    if (a && typeof a === 'object') {
      this.repeticion = { valor: a.repetir.valor, motivo: a.repetir.interpretacion?.startsWith('CRITICO') ? 'CONFIRMAR_CRITICO' : 'OTRO', detalle: '' };
    }
    this.accion.set(a);
  }

  protected esRepeticion(r: ResultadoFila): boolean {
    const a = this.accion();
    return !!a && typeof a === 'object' && a.repetir.analitoId === r.analitoId;
  }

  protected recibirMuestra(): void {
    if (!this.muestra.tomadaPor.trim()) {
      this.error.set('Indique quién tomó la muestra.');
      return;
    }
    const en = this.muestra.tomadaEn ? new Date(this.muestra.tomadaEn).toISOString() : null;
    this.ejecutar(this.service.recibirMuestra(this.orden()!.id, this.muestra.tomadaPor.trim(), en), 'Muestra recibida.');
  }

  protected rechazar(): void {
    this.ejecutar(this.service.rechazar(this.orden()!.id, this.rechazo.motivo, this.rechazo.detalle.trim() || null),
      'Muestra rechazada. Se avisó al médico.');
  }

  protected nuevaMuestra(): void {
    this.ejecutar(this.service.nuevaMuestra(this.orden()!.id), 'La orden quedó pendiente de una nueva muestra.');
  }

  /** "Guardar" del mockup: solo envía los valores nuevos; cambiar uno existente es una repetición. */
  protected guardar(): void {
    const o = this.orden()!;
    const existentes = new Set(o.examenes.flatMap((e) => e.resultados.filter((r) => r.valor !== null).map((r) => r.analitoId)));
    const valores = Object.entries(this.valores)
      .filter(([id, v]) => v !== null && v !== undefined && (v as unknown) !== '' && !existentes.has(+id))
      .map(([id, v]) => ({ analitoId: +id, valor: +v! }));
    const trazabilidad = o.examenes.map((e) => {
      const t = this.trazabilidad[e.id];
      return { ordenExamenId: e.id, equipo: t.equipo.trim() || null, loteReactivo: t.loteReactivo.trim() || null, calibracion: t.calibracion.trim() || null };
    });
    this.ejecutar(this.service.guardarResultados(o.id, { trazabilidad, valores }), 'Resultados guardados.');
  }

  protected repetir(r: ResultadoFila): void {
    if (this.repeticion.valor === null || (this.repeticion.valor as unknown) === '') {
      this.error.set('Ingrese el nuevo valor.');
      return;
    }
    this.ejecutar(this.service.repetir(this.orden()!.id, r.analitoId, +this.repeticion.valor, this.repeticion.motivo,
      this.repeticion.detalle.trim() || null), `${r.nombre}: repetición registrada.`);
  }

  protected validar(): void {
    const o = this.orden()!;
    if (o.tieneCriticos && !this.validacion.contacto.trim()) {
      this.error.set('Registre a quién informó el valor crítico.');
      return;
    }
    this.ejecutar(this.service.validar(o.id, o.tieneCriticos ? this.validacion.contacto.trim() : null,
      o.tieneCriticos ? this.validacion.medio : null), 'Resultados validados y publicados. Se avisó al médico.');
  }

  protected flecha(r: ResultadoFila): string {
    if (!r.interpretacion || r.interpretacion === 'NORMAL') return '';
    return r.interpretacion.endsWith('BAJO') ? 'arrow_downward' : 'arrow_upward';
  }
}
