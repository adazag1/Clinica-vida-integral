import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { mensajeError } from '../../../core/api/api';
import { AuthService } from '../../../core/auth/auth.service';
import {
  EstadoCobertura,
  Episodio,
  RESULTADOS_COBERTURA,
  ROMANO,
  TIPOS_CONSENTIMIENTO,
  TipoConsentimiento,
} from '../admisiones.models';
import { AdmisionesService } from '../admisiones.service';
import { TriageFormulario } from './triage-formulario';

type Seccion = 'triage' | 'cobertura' | 'excepcion' | 'consentimiento' | 'anular' | null;

/** Pantalla de admisión de un episodio: requisitos, triage, cobertura y consentimientos. */
@Component({
  selector: 'app-episodio-detalle',
  imports: [DatePipe, DecimalPipe, RouterLink, ReactiveFormsModule, TriageFormulario],
  templateUrl: './episodio-detalle.html',
  styleUrl: './episodio-detalle.scss',
})
export class EpisodioDetalle implements OnInit {
  private readonly service = inject(AdmisionesService);
  private readonly fb = inject(NonNullableFormBuilder);
  protected readonly auth = inject(AuthService);

  readonly id = input.required<string>();

  protected readonly episodio = signal<Episodio | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  protected readonly abierta = signal<Seccion>(null);
  protected readonly enviando = signal(false);

  protected readonly romano = ROMANO;
  protected readonly resultadosCobertura = RESULTADOS_COBERTURA;
  protected readonly tiposConsentimiento = TIPOS_CONSENTIMIENTO;

  protected readonly esAdmisiones = computed(() => this.auth.tieneRol('ADMISIONES'));
  protected readonly esClinico = computed(() => this.auth.tieneRol('ADMISIONES', 'ENFERMERIA', 'MEDICO'));
  protected readonly editable = computed(() => this.episodio()?.estado === 'EN_ADMISION');
  protected readonly urgencias = computed(() => this.episodio()?.tipoAtencion === 'URGENCIAS');

  /** Lista de requisitos para pasar a consulta, marcando los que ya se cumplen. */
  protected readonly requisitos = computed(() => {
    const e = this.episodio();
    if (!e) return [];
    const falta = (prefijo: string) => e.pendientes.find((p) => p.codigo.startsWith(prefijo));
    const lista = [];
    if (e.tipoAtencion === 'URGENCIAS') {
      lista.push({ titulo: 'Clasificación de triage', pendiente: falta('TRIAGE') });
    }
    lista.push({ titulo: 'Cobertura o autorización', pendiente: falta('COBERTURA') });
    lista.push({ titulo: 'Consentimiento informado', pendiente: falta('CONSENTIMIENTO') });
    return lista;
  });

  protected readonly cobertura = this.fb.group({
    resultado: ['VERIFICADA' as EstadoCobertura, Validators.required],
    aseguradora: ['', Validators.maxLength(120)],
    plan: ['', Validators.maxLength(120)],
    numeroAutorizacion: ['', Validators.maxLength(60)],
    observacion: ['', Validators.maxLength(500)],
  });

  protected readonly justificacion = this.fb.control('', [Validators.required, Validators.maxLength(500)]);

  protected readonly consentimiento = this.fb.group({
    tipo: ['CONSENTIMIENTO_INFORMADO' as TipoConsentimiento, Validators.required],
    otorgadoPor: ['', [Validators.required, Validators.maxLength(150)]],
    parentesco: ['', Validators.maxLength(60)],
    aceptado: [true],
    observaciones: ['', Validators.maxLength(500)],
  });

  ngOnInit(): void {
    this.service.obtener(+this.id()).subscribe({
      next: (e) => this.episodio.set(e),
      error: (e: HttpErrorResponse) => this.error.set(mensajeError(e, 'No fue posible cargar la atención.')),
    });
  }

  protected abrir(seccion: Seccion): void {
    const e = this.episodio();
    this.error.set(null);
    this.aviso.set(null);
    this.justificacion.reset('');
    if (seccion === 'cobertura' && e) {
      this.cobertura.reset({
        resultado: 'VERIFICADA',
        aseguradora: e.aseguradora ?? '',
        plan: '',
        numeroAutorizacion: '',
        observacion: '',
      });
    }
    if (seccion === 'consentimiento' && e) {
      const registrados = new Set(e.consentimientos.map((c) => c.tipo));
      const siguiente = this.tiposConsentimiento.find((t) => !registrados.has(t.valor))?.valor ?? 'CONSENTIMIENTO_INFORMADO';
      this.consentimiento.reset({
        tipo: siguiente,
        otorgadoPor: e.paciente.identificacionTemporal ? '' : e.paciente.nombreCompleto,
        parentesco: '',
        aceptado: true,
        observaciones: '',
      });
    }
    this.abierta.set(seccion);
  }

  protected tiposDisponibles() {
    const registrados = new Set(this.episodio()?.consentimientos.map((c) => c.tipo));
    return this.tiposConsentimiento.filter((t) => !registrados.has(t.valor));
  }

  protected actualizado(e: Episodio, mensaje?: string): void {
    this.episodio.set(e);
    this.abierta.set(null);
    this.enviando.set(false);
    this.aviso.set(mensaje ?? null);
  }

  private fallo(e: HttpErrorResponse, porDefecto: string): void {
    this.enviando.set(false);
    this.error.set(mensajeError(e, porDefecto));
  }

  protected guardarCobertura(): void {
    if (this.cobertura.invalid) {
      this.cobertura.markAllAsTouched();
      return;
    }
    const v = this.cobertura.getRawValue();
    if (v.resultado === 'VERIFICADA' && !v.aseguradora.trim()) {
      this.error.set('Ingrese la aseguradora que autorizó.');
      return;
    }
    const nulo = (s: string) => (s.trim() ? s.trim() : null);
    this.enviando.set(true);
    this.service
      .cobertura(this.episodio()!.id, {
        resultado: v.resultado,
        aseguradora: nulo(v.aseguradora),
        plan: nulo(v.plan),
        numeroAutorizacion: nulo(v.numeroAutorizacion),
        observacion: nulo(v.observacion),
      })
      .subscribe({
        next: (e) => this.actualizado(e),
        error: (e: HttpErrorResponse) => this.fallo(e, 'No fue posible registrar la cobertura.'),
      });
  }

  protected guardarExcepcion(): void {
    if (this.justificacion.invalid || !this.justificacion.value.trim()) {
      this.justificacion.markAsTouched();
      return;
    }
    this.enviando.set(true);
    this.service.excepcionUrgencia(this.episodio()!.id, this.justificacion.value.trim()).subscribe({
      next: (e) => this.actualizado(e),
      error: (e: HttpErrorResponse) => this.fallo(e, 'No fue posible registrar la excepción.'),
    });
  }

  protected guardarConsentimiento(): void {
    if (this.consentimiento.invalid) {
      this.consentimiento.markAllAsTouched();
      return;
    }
    const v = this.consentimiento.getRawValue();
    this.enviando.set(true);
    this.service
      .consentimiento(this.episodio()!.id, {
        tipo: v.tipo,
        otorgadoPor: v.otorgadoPor.trim(),
        parentesco: v.parentesco.trim() || null,
        aceptado: v.aceptado,
        observaciones: v.observaciones.trim() || null,
      })
      .subscribe({
        next: (e) => this.actualizado(e),
        error: (e: HttpErrorResponse) => this.fallo(e, 'No fue posible registrar el consentimiento.'),
      });
  }

  protected enviarAConsulta(): void {
    this.enviando.set(true);
    this.error.set(null);
    this.service.enviarAConsulta(this.episodio()!.id).subscribe({
      next: (e) => this.actualizado(e, 'El paciente quedó en la lista de espera de consulta.'),
      error: (e: HttpErrorResponse) => this.fallo(e, 'No fue posible enviar a consulta.'),
    });
  }

  protected anular(): void {
    if (this.justificacion.invalid || !this.justificacion.value.trim()) {
      this.justificacion.markAsTouched();
      return;
    }
    this.enviando.set(true);
    this.service.anular(this.episodio()!.id, this.justificacion.value.trim()).subscribe({
      next: (e) => this.actualizado(e, 'La atención fue anulada.'),
      error: (e: HttpErrorResponse) => this.fallo(e, 'No fue posible anular la atención.'),
    });
  }

  protected claseEstado(estado: string): string {
    if (estado === 'EN_ESPERA_CONSULTA') return 'chip--normal';
    if (estado === 'ANULADO') return 'chip--critico';
    return 'chip--info';
  }

  protected claseCobertura(estado: EstadoCobertura): string {
    if (estado === 'PENDIENTE') return 'chip--alerta';
    if (estado === 'NO_CUBIERTA') return 'chip--critico';
    if (estado === 'EXCEPCION_URGENCIA') return 'chip--info';
    return 'chip--normal';
  }
}
