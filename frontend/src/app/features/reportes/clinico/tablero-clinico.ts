import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval } from 'rxjs';

import { mensajeError } from '../../../core/api/api';
import { GraficoSerie, PuntoSerie, Referencia, mesLargo } from '../componentes/grafico-serie';
import { TableroClinico, horas } from '../reportes.models';
import { ReportesService } from '../reportes.service';

const ESTADOS = {
  META_ALCANZADA: { texto: 'Meta alcanzada', icono: 'verified', clase: 'chip--normal' },
  EN_PROGRESO: { texto: 'Mejorando, aún sobre la meta', icono: 'trending_down', clase: 'chip--alerta' },
  SIN_MEJORA: { texto: 'Sin mejora frente a la línea base', icono: 'warning', clase: 'chip--critico' },
  SIN_DATOS: { texto: 'Sin casos en los últimos 30 días', icono: 'hourglass_empty', clase: 'chip--info' },
} as const;

/** US 5.1: tiempo promedio de diagnóstico de enfermedades críticas contra la línea base y la meta del CEO. */
@Component({
  selector: 'app-tablero-clinico',
  imports: [DatePipe, DecimalPipe, GraficoSerie],
  template: `
    @if (error()) { <div class="alerta alerta--error" role="alert"><span class="icono">error</span>{{ error() }}</div> }
    @if (t(); as t) {
      @if (t.parametrosDePrueba) {
        <div class="alerta alerta--aviso"><span class="icono">science</span>
          La línea base y la meta son valores de prueba. El Product Owner debe registrar los tiempos reales medidos antes de
          la plataforma (pestaña «Metas y costos»).</div>
      }
      <div class="hero-fila">
        <section class="tarjeta hero">
          <span class="hero__rotulo">Tiempo promedio de diagnóstico · últimos 30 días</span>
          <strong class="hero__valor">{{ t.ultimos30Dias.promedio === null ? '—' : (t.ultimos30Dias.promedio | number: '1.0-1') + ' h' }}</strong>
          <span class="chip" [class]="'chip ' + estado().clase"><span class="icono">{{ estado().icono }}</span>{{ estado().texto }}</span>
          <p class="hero__pie">{{ t.ultimos30Dias.casos }} caso(s) crítico(s) · mediana
            {{ t.ultimos30Dias.mediana === null ? '—' : (t.ultimos30Dias.mediana | number: '1.0-1') + ' h' }} · 90 % en menos de
            {{ t.ultimos30Dias.p90 === null ? '—' : (t.ultimos30Dias.p90 | number: '1.0-1') + ' h' }}</p>
        </section>
        <section class="tarjeta dato">
          <span class="dato__rotulo">Línea base</span>
          <strong>{{ t.lineaBaseHoras === null ? '—' : (t.lineaBaseHoras | number: '1.0-1') + ' h' }}</strong>
          <small>antes de la plataforma</small>
        </section>
        <section class="tarjeta dato">
          <span class="dato__rotulo">Meta (−{{ t.metaReduccionPct }} %)</span>
          <strong>{{ t.metaHoras === null ? '—' : (t.metaHoras | number: '1.0-1') + ' h' }}</strong>
          <small>en {{ t.plazoMeses }} meses · van {{ t.mesesTranscurridos }}</small>
        </section>
        <section class="tarjeta dato">
          <span class="dato__rotulo">Reducción lograda</span>
          <strong [class.bien]="(t.reduccionPct ?? 0) > 0" [class.mal]="(t.reduccionPct ?? 0) < 0">
            {{ t.reduccionPct === null ? '—' : (t.reduccionPct | number: '1.0-1') + ' %' }}</strong>
          <small>frente a la línea base</small>
        </section>
      </div>

      <section class="tarjeta">
        <div class="titulo-accion">
          <div class="tarjeta__titulo"><span class="icono">show_chart</span>Tiempo promedio de diagnóstico por mes</div>
          <small class="actualizado">Actualizado {{ t.calculadoEn | date: 'h:mm:ss a' }} · se renueva cada minuto</small>
        </div>
        <app-grafico-serie titulo="Tiempo promedio de diagnóstico por mes, en horas" [puntos]="serie()" [referencias]="referencias()"
          [formato]="formatoHoras" />
        <details class="tabla-datos">
          <summary>Ver los datos en tabla</summary>
          <div class="tabla-contenedor"><table class="tabla">
            <thead><tr><th>Mes</th><th class="num">Casos</th><th class="num">Promedio</th><th class="num">Mediana</th></tr></thead>
            <tbody>
              @for (m of t.serie; track m.mes) {
                <tr><td>{{ mesLargo(m.mes) }}</td><td class="num">{{ m.casos }}</td>
                  <td class="num">{{ m.promedioHoras === null ? '—' : (m.promedioHoras | number: '1.0-1') + ' h' }}</td>
                  <td class="num">{{ m.medianaHoras === null ? '—' : (m.medianaHoras | number: '1.0-1') + ' h' }}</td></tr>
              }
            </tbody>
          </table></div>
        </details>
        <p class="nota">Caso: consulta firmada con un diagnóstico crítico confirmado ({{ t.prefijosCriticos.join(', ') }}). El tiempo
          va desde la llegada del paciente (admisión) hasta la firma del diagnóstico; si llegó con cita, desde el inicio de la consulta.</p>
      </section>

      <div class="rejilla">
        <section class="tarjeta">
          <div class="tarjeta__titulo"><span class="icono">account_tree</span>Dónde se va el tiempo (últimos 30 días)</div>
          @for (c of t.componentes; track c.nombre) {
            <div class="etapa">
              <div class="etapa__texto"><strong>{{ c.nombre }}</strong><small>{{ c.descripcion }} · {{ c.casos }} caso(s)</small></div>
              <span class="fondo"><span class="relleno" [style.width.%]="pctEtapa(c.promedioHoras)"></span></span>
              <span class="etapa__valor">{{ c.promedioHoras === null ? '—' : (c.promedioHoras | number: '1.0-1') + ' h' }}</span>
            </div>
          }
        </section>

        <section class="tarjeta">
          <div class="tarjeta__titulo"><span class="icono">diagnosis</span>Por diagnóstico crítico</div>
          @if (t.porDiagnostico.length) {
            <div class="tabla-contenedor"><table class="tabla">
              <thead><tr><th>Diagnóstico</th><th class="num">Casos</th><th class="num">Promedio</th></tr></thead>
              <tbody>
                @for (d of t.porDiagnostico; track d.codigo) {
                  <tr><td><strong>{{ d.codigo }}</strong> {{ d.nombre }}</td><td class="num">{{ d.casos }}</td>
                    <td class="num nowrap">{{ d.promedioHoras === null ? '—' : (d.promedioHoras | number: '1.0-1') + ' h' }}</td></tr>
                }
              </tbody>
            </table></div>
          } @else {
            <p class="vacio">Aún no hay diagnósticos críticos confirmados desde la puesta en marcha.</p>
          }
        </section>
      </div>

      <section class="tarjeta">
        <div class="tarjeta__titulo"><span class="icono">history</span>Últimos casos</div>
        @if (t.recientes.length) {
          <div class="tabla-contenedor">
            <table class="tabla">
              <thead><tr><th>Diagnosticado</th><th>Diagnóstico</th><th>Llegada</th><th class="num">Tiempo</th></tr></thead>
              <tbody>
                @for (c of t.recientes; track $index) {
                  <tr>
                    <td class="nowrap">{{ c.diagnosticadoEn | date: 'dd/MM/yyyy h:mm a' }}</td>
                    <td><strong>{{ c.codigo }}</strong> {{ c.nombre }}</td>
                    <td>{{ c.desdeAdmision ? 'Admisión / urgencias' : 'Consulta con cita' }}</td>
                    <td class="num nowrap" [class.mal]="t.metaHoras !== null && c.horas > t.metaHoras">{{ c.horas | number: '1.0-1' }} h</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        } @else {
          <p class="vacio">Sin casos todavía.</p>
        }
      </section>
    } @else if (!error()) {
      <p class="vacio">Calculando indicadores…</p>
    }
  `,
  styles: `
    .alerta { margin-bottom: 12px; }
    .hero-fila { display: grid; grid-template-columns: 2fr repeat(3, 1fr); gap: 12px; margin-bottom: 14px; }
    @media (max-width: 900px) { .hero-fila { grid-template-columns: 1fr 1fr; } .hero { grid-column: 1 / -1; } }
    .hero { display: flex; flex-direction: column; align-items: flex-start; gap: 6px; }
    .hero__rotulo, .dato__rotulo { font-size: 13px; color: var(--c-texto-2); }
    .hero__valor { font-size: 40px; line-height: 1.1; }
    .hero__pie { margin: 0; font-size: 12.5px; color: var(--c-texto-2); }
    .dato { display: flex; flex-direction: column; gap: 2px; }
    .dato strong { font-size: 26px; }
    .dato small { color: var(--c-texto-3); font-size: 12px; }
    .bien { color: var(--c-normal); }
    .mal { color: var(--c-critico); }
    .actualizado { color: var(--c-texto-3); font-size: 12px; }
    .nota { font-size: 12.5px; color: var(--c-texto-2); margin: 8px 0 0; }
    .tabla-datos { margin-top: 6px; font-size: 13px; }
    .tabla-datos summary { cursor: pointer; color: var(--c-azul); }
    .rejilla { display: grid; grid-template-columns: repeat(auto-fit, minmax(340px, 1fr)); gap: 14px; margin: 14px 0; }
    .etapa { display: grid; grid-template-columns: minmax(150px, 1.4fr) 1fr 64px; align-items: center; gap: 10px; margin-bottom: 10px; }
    .etapa__texto { display: flex; flex-direction: column; font-size: 13px; }
    .etapa__texto small { color: var(--c-texto-2); font-size: 12px; }
    .etapa__valor { text-align: right; font-weight: 600; font-size: 13.5px; }
    .fondo { height: 10px; border-radius: 999px; background: var(--c-fondo); overflow: hidden; }
    .relleno { display: block; height: 100%; background: var(--c-primario); border-radius: 0 4px 4px 0; }
    .num { text-align: right; }
  `,
})
export class TableroClinicoPanel {
  private readonly service = inject(ReportesService);

  protected readonly t = signal<TableroClinico | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly mesLargo = mesLargo;
  protected readonly formatoHoras = horas;

  protected readonly estado = computed(() => ESTADOS[this.t()?.estado ?? 'SIN_DATOS']);

  protected readonly serie = computed<PuntoSerie[]>(() =>
    (this.t()?.serie ?? []).map((m) => ({
      etiqueta: m.mes,
      valor: m.promedioHoras,
      detalle: m.casos ? `${m.casos} caso(s) · mediana ${m.medianaHoras === null ? '—' : horas(m.medianaHoras)}` : undefined,
    })),
  );

  protected readonly referencias = computed<Referencia[]>(() => {
    const t = this.t();
    const r: Referencia[] = [];
    if (t?.lineaBaseHoras != null) r.push({ valor: t.lineaBaseHoras, etiqueta: `Base ${horas(t.lineaBaseHoras)}`, tono: 'base' });
    if (t?.metaHoras != null) r.push({ valor: t.metaHoras, etiqueta: `Meta ${horas(t.metaHoras)}`, tono: 'meta' });
    return r;
  });

  private readonly maxEtapa = computed(() => Math.max(1, ...(this.t()?.componentes ?? []).map((c) => c.promedioHoras ?? 0)));

  constructor() {
    this.cargar();
    // "Tiempo real": se recalcula cada minuto mientras el tablero está abierto
    interval(60_000).pipe(takeUntilDestroyed(inject(DestroyRef))).subscribe(() => this.cargar());
  }

  private cargar(): void {
    this.service.clinico().subscribe({
      next: (t) => {
        this.t.set(t);
        this.error.set(null);
      },
      error: (e: HttpErrorResponse) => this.error.set(mensajeError(e, 'No fue posible calcular el tablero clínico.')),
    });
  }

  protected pctEtapa(v: number | null): number {
    return v === null ? 0 : (100 * v) / this.maxEtapa();
  }
}
