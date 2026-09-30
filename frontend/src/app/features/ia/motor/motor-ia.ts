import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';

import { mensajeError } from '../../../core/api/api';
import { AuthService } from '../../../core/auth/auth.service';
import { EstadoIa, ReglaIa } from '../ia.models';
import { IaService } from '../ia.service';

/**
 * Épica 4: reglas del motor con su referencia y la precisión que reportan los médicos, estado del
 * asistente de lenguaje y exportación del dataset anonimizado (US 4.1).
 */
@Component({
  selector: 'app-motor-ia',
  imports: [DatePipe, DecimalPipe],
  template: `
    <div class="cabecera-pagina">
      <div>
        <h2>Motor de IA clínica</h2>
        <p class="sub">Reglas basadas en guías clínicas publicadas. Apoyan la decisión del médico; no la reemplazan.</p>
      </div>
    </div>
    @if (error()) { <div class="alerta alerta--error" role="alert"><span class="icono">error</span>{{ error() }}</div> }
    @if (aviso()) { <div class="alerta alerta--exito" role="status"><span class="icono">check_circle</span>{{ aviso() }}</div> }

    <div class="indicadores">
      <div class="tarjeta"><strong>{{ activas() }}/{{ reglas().length }}</strong><span>reglas activas</span></div>
      <div class="tarjeta"><strong>{{ totalAlertas() }}</strong><span>alertas generadas</span></div>
      <div class="tarjeta"><strong>{{ calificadas() }}</strong><span>calificadas por médicos</span></div>
      <div class="tarjeta" [class.malo]="precisionGlobal() !== null && precisionGlobal()! < 60">
        <strong>{{ precisionGlobal() === null ? '—' : (precisionGlobal() | number: '1.0-1') + ' %' }}</strong><span>precisión percibida</span></div>
    </div>

    <section class="tarjeta">
      <div class="tarjeta__titulo"><span class="icono">rule</span>Reglas clínicas</div>
      <p class="nota">La precisión es la proporción de alertas que los médicos calificaron como útiles. Las reglas con baja
        precisión deben revisarse con el comité médico antes de ajustar sus umbrales.</p>
      <div class="tabla-contenedor">
        <table class="tabla">
          <thead><tr><th>Regla</th><th>Alertas</th><th>Útil / No útil</th><th>Precisión</th><th>Estado</th></tr></thead>
          <tbody>
            @for (r of reglas(); track r.codigo) {
              <tr [class.inactiva]="!r.activa">
                <td>
                  <strong>{{ r.nombre }}</strong>
                  <p class="descripcion">{{ r.descripcion }}</p>
                  <small class="referencia"><span class="icono">menu_book</span>{{ r.referencia }}</small>
                  @if (r.comentariosRecientes.length) {
                    <details><summary>Comentarios de "no útil" ({{ r.comentariosRecientes.length }})</summary>
                      <ul>@for (c of r.comentariosRecientes; track $index) { <li>{{ c }}</li> }</ul>
                    </details>
                  }
                </td>
                <td class="num">{{ r.alertas }}</td>
                <td class="num nowrap">{{ r.utiles }} / {{ r.noUtiles }}</td>
                <td class="num">
                  @if (r.precision === null) { <span class="sin-dato">Sin calificar</span> }
                  @else {
                    <span class="precision" [class.baja]="r.precision < 60">{{ r.precision | number: '1.0-1' }} %</span>
                    <span class="fondo"><span class="relleno" [class.baja]="r.precision < 60" [style.width.%]="r.precision"></span></span>
                  }
                </td>
                <td class="nowrap">
                  @if (esAdmin()) {
                    <label class="interruptor">
                      <input type="checkbox" [checked]="r.activa" (change)="cambiar(r, $any($event.target).checked)" />
                      {{ r.activa ? 'Activa' : 'Inactiva' }}
                    </label>
                  } @else {
                    <span class="chip" [class]="'chip ' + (r.activa ? 'chip--normal' : 'chip--alerta')">{{ r.activa ? 'Activa' : 'Inactiva' }}</span>
                  }
                  @if (r.modificadoPor) { <small class="cambio">{{ r.modificadoPor }} · {{ r.modificadoEn | date: 'dd/MM/yyyy' }}</small> }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </section>

    <div class="rejilla">
      <section class="tarjeta">
        <div class="tarjeta__titulo"><span class="icono">smart_toy</span>Asistente de lenguaje (Azure OpenAI)</div>
        @if (estado(); as e) {
          <p class="estado" [class.apagado]="!e.asistenteHabilitado">
            <span class="icono">{{ e.asistenteHabilitado && e.asistenteConfigurado ? 'power' : 'power_off' }}</span>
            {{ e.asistenteHabilitado && e.asistenteConfigurado ? 'Encendido' : 'Apagado' }}{{ e.modelo ? ' · ' + e.modelo : '' }}
          </p>
          @if (e.motivo) { <p class="nota">{{ e.motivo }}</p> }
          <dl class="datos">
            <div><dt>Consultas (30 días)</dt><dd>{{ e.consultasAsistente30d }}</dd></div>
            <div><dt>Costo estimado (30 días)</dt><dd>US$ {{ e.costoAsistente30dUsd | number: '1.2-4' }}</dd></div>
          </dl>
          <p class="nota">Solo recibe datos anonimizados (sin nombre, documento, teléfono ni fechas exactas). Cada uso queda
            registrado con usuario, tokens y costo.</p>
        }
      </section>

      @if (puedeExportar()) {
        <section class="tarjeta">
          <div class="tarjeta__titulo"><span class="icono">dataset</span>Dataset anonimizado</div>
          <p class="nota">CSV con diagnósticos CIE-10, resultados de laboratorio, signos vitales, estudios de imágenes y casos de
            patología para entrenar o validar modelos. Cada paciente aparece con un seudónimo, su sexo, su grupo de edad y el
            día relativo a su primer evento; no incluye nombres, documentos, fechas exactas ni texto libre.</p>
          <p class="nota">La descarga queda en el registro de accesos. Antes de compartirlo fuera de la clínica, el oficial de
            protección de datos debe aprobar su uso (Ley 1581 de 2012).</p>
          <button type="button" class="btn btn--primario btn--pequeno" (click)="descargar()" [disabled]="descargando()">
            <span class="icono">download</span>{{ descargando() ? 'Preparando…' : 'Descargar CSV' }}</button>
        </section>
      }
    </div>
  `,
  styles: `
    .alerta { margin-bottom: 12px; }
    .indicadores { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 12px; margin-bottom: 14px; }
    .indicadores .tarjeta { display: flex; flex-direction: column; }
    .indicadores strong { font-size: 24px; }
    .indicadores span { color: var(--c-texto-2); font-size: 13px; }
    .malo strong { color: var(--c-critico); }
    .nota { font-size: 13px; color: var(--c-texto-2); margin: 0 0 10px; }
    .descripcion { margin: 2px 0; font-size: 13px; color: var(--c-texto-2); max-width: 560px; }
    .referencia { display: flex; align-items: center; gap: 4px; color: var(--c-texto-3); }
    .referencia .icono { font-size: 15px; }
    details { margin-top: 4px; font-size: 12.5px; }
    details ul { margin: 4px 0; padding-left: 18px; color: var(--c-texto-2); }
    .num { text-align: right; }
    .inactiva { opacity: 0.6; }
    .sin-dato { color: var(--c-texto-3); font-size: 12.5px; }
    .precision { font-weight: 600; color: var(--c-normal); }
    .precision.baja { color: var(--c-critico); }
    .fondo { display: block; width: 90px; height: 6px; margin: 4px 0 0 auto; border-radius: 999px; background: var(--c-fondo); overflow: hidden; }
    .relleno { display: block; height: 100%; background: var(--c-normal); }
    .relleno.baja { background: var(--c-critico); }
    .interruptor { display: inline-flex; align-items: center; gap: 6px; cursor: pointer; font-size: 13px; }
    .cambio { display: block; color: var(--c-texto-3); font-size: 11.5px; }
    .rejilla { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 14px; margin-top: 14px; }
    .estado { display: flex; align-items: center; gap: 6px; font-weight: 600; color: var(--c-normal); margin: 0 0 6px; }
    .estado.apagado { color: var(--c-texto-2); }
    .datos { display: flex; gap: 24px; margin: 8px 0 12px; }
    .datos dt { font-size: 12px; color: var(--c-texto-2); }
    .datos dd { margin: 0; font-size: 18px; font-weight: 600; }
  `,
})
export class MotorIa {
  private readonly service = inject(IaService);
  private readonly auth = inject(AuthService);

  protected readonly reglas = signal<ReglaIa[]>([]);
  protected readonly estado = signal<EstadoIa | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  protected readonly descargando = signal(false);

  protected readonly esAdmin = computed(() => this.auth.usuario()?.rol === 'ADMIN');
  protected readonly puedeExportar = computed(() => this.auth.tieneRol('DIRECTIVO'));
  protected readonly activas = computed(() => this.reglas().filter((r) => r.activa).length);
  protected readonly totalAlertas = computed(() => this.reglas().reduce((s, r) => s + r.alertas, 0));
  protected readonly calificadas = computed(() => this.reglas().reduce((s, r) => s + r.utiles + r.noUtiles, 0));
  protected readonly precisionGlobal = computed(() => {
    const utiles = this.reglas().reduce((s, r) => s + r.utiles, 0);
    return this.calificadas() ? (100 * utiles) / this.calificadas() : null;
  });

  constructor() {
    this.service.reglas().subscribe({
      next: (r) => this.reglas.set(r),
      error: (e: HttpErrorResponse) => this.error.set(mensajeError(e, 'No fue posible cargar las reglas.')),
    });
    this.service.estado().subscribe({ next: (e) => this.estado.set(e), error: () => undefined });
  }

  protected cambiar(r: ReglaIa, activa: boolean): void {
    this.service.cambiarRegla(r.codigo, activa).subscribe({
      next: (actualizada) => {
        this.reglas.update((lista) => lista.map((x) => (x.codigo === actualizada.codigo ? actualizada : x)));
        this.aviso.set(`Regla «${actualizada.nombre}» ${activa ? 'activada' : 'desactivada'}.`);
      },
      error: (e: HttpErrorResponse) => {
        this.error.set(mensajeError(e, 'No fue posible cambiar la regla.'));
        this.reglas.update((lista) => [...lista]);
      },
    });
  }

  protected descargar(): void {
    this.descargando.set(true);
    this.service.dataset().subscribe({
      next: (res) => {
        this.descargando.set(false);
        const nombre = /filename="([^"]+)"/.exec(res.headers.get('Content-Disposition') ?? '')?.[1] ?? 'dataset-clinico-anonimizado.csv';
        const url = URL.createObjectURL(res.body!);
        const a = document.createElement('a');
        a.href = url;
        a.download = nombre;
        a.click();
        URL.revokeObjectURL(url);
        this.aviso.set(`Dataset descargado: ${res.headers.get('X-Filas') ?? '?'} filas de ${res.headers.get('X-Pacientes') ?? '?'} pacientes.`);
      },
      error: (e: HttpErrorResponse) => {
        this.descargando.set(false);
        this.error.set(mensajeError(e, 'No fue posible exportar el dataset.'));
      },
    });
  }
}
