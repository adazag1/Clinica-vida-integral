import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';

import { mensajeError } from '../../../core/api/api';
import { GraficoSerie, PuntoSerie, Referencia, compacto, mesLargo } from '../componentes/grafico-serie';
import { MesFinanciero, TableroFinanciero, pesos } from '../reportes.models';
import { ReportesService } from '../reportes.service';

/** US 5.2: reporte mensual que cruza costos operativos con capacidad de atención, y proyección del ROI a 36 meses. */
@Component({
  selector: 'app-tablero-financiero',
  imports: [DatePipe, DecimalPipe, GraficoSerie],
  template: `
    @if (error()) { <div class="alerta alerta--error" role="alert"><span class="icono">error</span>{{ error() }}</div> }
    @if (t(); as t) {
      <div class="barra-acciones no-imprimir">
        <small>Calculado {{ t.calculadoEn | date: "dd/MM/yyyy 'a las' h:mm a" }}</small>
        <button type="button" class="btn btn--secundario btn--pequeno" (click)="imprimir()"><span class="icono">picture_as_pdf</span>Imprimir / PDF</button>
        <button type="button" class="btn btn--primario btn--pequeno" (click)="exportar()" [disabled]="exportando()">
          <span class="icono">download</span>Exportar a Excel (CSV)</button>
      </div>
      @if (t.parametrosDePrueba) {
        <div class="alerta alerta--aviso"><span class="icono">science</span>
          Algunas líneas base o supuestos del ROI son valores de prueba. La dirección financiera debe registrarlos en «Metas y costos».</div>
      }
      @if (!ultimoConCostos()) {
        <div class="alerta alerta--info"><span class="icono">info</span>
          Aún no hay costos operativos registrados. Regístrelos por mes en «Metas y costos» para calcular el ahorro y el ROI.</div>
      }

      <div class="indicadores">
        <section class="tarjeta">
          <span class="rotulo">Costo administrativo {{ ultimoConCostos() ? '· ' + mesLargo(ultimoConCostos()!.mes) : '' }}</span>
          <strong>{{ ultimoConCostos() ? pesosCortos(ultimoConCostos()!.costoAdministrativo!) : '—' }}</strong>
          <small>Meta {{ pesosCortos(t.costosMeta) }} (−{{ t.costosMetaReduccionPct }} %) ·
            @if (ultimoConCostos()?.variacionCostosPct != null) {
              <span [class.bien]="ultimoConCostos()!.variacionCostosPct! < 0" [class.mal]="ultimoConCostos()!.variacionCostosPct! > 0">
                {{ ultimoConCostos()!.variacionCostosPct! | number: '1.0-1' }} % vs. base</span>
            } @else { sin datos }
          </small>
        </section>
        <section class="tarjeta">
          <span class="rotulo">Atenciones por médico · {{ mesLargo(ultimo().mes) }}</span>
          <strong>{{ ultimo().atencionesPorMedico === null ? '—' : (ultimo().atencionesPorMedico! | number: '1.0-1') }}</strong>
          <small>Meta {{ t.capacidadMeta | number: '1.0-0' }} (+{{ t.capacidadMetaAumentoPct }} %) · {{ ultimo().atenciones }} atenciones,
            {{ ultimo().medicos }} médico(s)</small>
        </section>
        <section class="tarjeta">
          <span class="rotulo">ROI proyectado a {{ t.roi.plazoMeses }} meses</span>
          <strong [class.bien]="t.roi.positivoEnPlazo" [class.mal]="t.roi.roiAlPlazoPct !== null && !t.roi.positivoEnPlazo">
            {{ t.roi.roiAlPlazoPct === null ? '—' : (t.roi.roiAlPlazoPct | number: '1.0-1') + ' %' }}</strong>
          <small>
            @if (t.roi.roiAlPlazoPct === null) { Se calcula al registrar costos }
            @else { <span class="icono icono--peq">{{ t.roi.positivoEnPlazo ? 'check_circle' : 'error' }}</span>
              {{ t.roi.positivoEnPlazo ? 'Positivo dentro del plazo' : 'No alcanza a ser positivo en el plazo' }} }
          </small>
        </section>
        <section class="tarjeta">
          <span class="rotulo">Recuperación de la inversión</span>
          <strong>{{ t.roi.mesRecuperacion === null ? '—' : 'Mes ' + t.roi.mesRecuperacion }}</strong>
          <small>Inversión {{ pesosCortos(t.roi.inversion) }} (US$ {{ t.roi.inversionUsd | number: '1.0-0' }}) · van {{ t.roi.mesesTranscurridos }} meses</small>
        </section>
      </div>

      <div class="rejilla">
        <section class="tarjeta">
          <div class="tarjeta__titulo"><span class="icono">savings</span>Costo administrativo mensual</div>
          <app-grafico-serie titulo="Costo administrativo mensual en pesos" tipo="barras" [puntos]="serieCostos()" [referencias]="refCostos()"
            [formato]="pesos" />
        </section>
        <section class="tarjeta">
          <div class="tarjeta__titulo"><span class="icono">groups</span>Atenciones por médico al mes</div>
          <app-grafico-serie titulo="Atenciones por médico al mes" [puntos]="serieCapacidad()" [referencias]="refCapacidad()" />
        </section>
      </div>

      <section class="tarjeta">
        <div class="tarjeta__titulo"><span class="icono">trending_up</span>Posición neta de la inversión (beneficio acumulado − inversión)</div>
        <app-grafico-serie titulo="Beneficio acumulado menos inversión, real y proyectado" [puntos]="serieRoi()" [formato]="pesos" [desdeCero]="true" />
        <p class="nota">Meses 1 a {{ t.roi.mesesTranscurridos }}: real. Después: proyección con el beneficio promedio de los últimos
          tres meses con costos ({{ t.roi.promedioReciente === null ? '—' : pesos(t.roi.promedioReciente) }} al mes). El ROI es
          positivo cuando la línea cruza el cero. Beneficio neto = ahorro administrativo frente a la línea base + margen de las
          atenciones adicionales − costo de la plataforma.</p>
      </section>

      <section class="tarjeta">
        <div class="tarjeta__titulo"><span class="icono">table_chart</span>Reporte mensual</div>
        <div class="tabla-contenedor">
          <table class="tabla">
            <thead>
              <tr><th>Mes</th><th class="num">Costo admin.</th><th class="num">Plataforma</th><th class="num">Ahorro</th>
                <th class="num">Atenciones</th><th class="num">Por médico</th><th class="num">Facturado</th><th class="num">Recaudado</th>
                <th class="num">Cargos autom.</th><th class="num">Beneficio neto</th></tr>
            </thead>
            <tbody>
              @for (m of mesesDesc(); track m.mes) {
                <tr>
                  <td class="nowrap">{{ mesLargo(m.mes) }}</td>
                  <td class="num">{{ m.costoAdministrativo === null ? '—' : pesos(m.costoAdministrativo) }}</td>
                  <td class="num">{{ m.costoPlataforma === null ? '—' : pesos(m.costoPlataforma) }}</td>
                  <td class="num" [class.bien]="(m.ahorro ?? 0) > 0" [class.mal]="(m.ahorro ?? 0) < 0">{{ m.ahorro === null ? '—' : pesos(m.ahorro) }}</td>
                  <td class="num">{{ m.atenciones }}</td>
                  <td class="num">{{ m.atencionesPorMedico === null ? '—' : (m.atencionesPorMedico | number: '1.0-1') }}</td>
                  <td class="num">{{ pesos(m.facturado) }}</td>
                  <td class="num">{{ pesos(m.recaudado) }}</td>
                  <td class="num">{{ m.cargosAutomaticosPct === null ? '—' : (m.cargosAutomaticosPct | number: '1.0-0') + ' %' }}</td>
                  <td class="num" [class.bien]="(m.beneficioNeto ?? 0) > 0" [class.mal]="(m.beneficioNeto ?? 0) < 0">
                    {{ m.beneficioNeto === null ? '—' : pesos(m.beneficioNeto) }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <p class="nota">«Cargos autom.»: porcentaje de cargos generados solos por el acto clínico (sin digitación en facturación).</p>
      </section>
    } @else if (!error()) {
      <p class="vacio">Calculando el reporte…</p>
    }
  `,
  styles: `
    .alerta { margin-bottom: 12px; }
    .barra-acciones { display: flex; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: 8px; margin-bottom: 12px; }
    .barra-acciones small { margin-right: auto; color: var(--c-texto-3); }
    .barra-acciones .btn { white-space: nowrap; }
    @media (max-width: 600px) { .barra-acciones small { flex-basis: 100%; } }
    .indicadores { display: grid; grid-template-columns: repeat(auto-fit, minmax(210px, 1fr)); gap: 12px; margin-bottom: 14px; }
    .indicadores .tarjeta { display: flex; flex-direction: column; gap: 3px; }
    .rotulo { font-size: 13px; color: var(--c-texto-2); }
    .indicadores strong { font-size: 26px; }
    .indicadores small { font-size: 12px; color: var(--c-texto-2); }
    .icono--peq { font-size: 15px; vertical-align: -3px; }
    .bien { color: var(--c-normal); }
    .mal { color: var(--c-critico); }
    /* Los gráficos necesitan ancho para que sus rótulos se lean: dos columnas solo en pantallas muy anchas */
    .rejilla { display: grid; grid-template-columns: repeat(auto-fit, minmax(620px, 1fr)); gap: 14px; margin-bottom: 14px; }
    @media (max-width: 700px) { .rejilla { grid-template-columns: 1fr; } }
    .nota { font-size: 12.5px; color: var(--c-texto-2); margin: 8px 0 0; }
    .num { text-align: right; white-space: nowrap; }
    section.tarjeta + section.tarjeta { margin-top: 14px; }
    @media print {
      .no-imprimir { display: none !important; }
      .rejilla { grid-template-columns: 1fr 1fr; }
      .tarjeta { break-inside: avoid; box-shadow: none; }
    }
  `,
})
export class TableroFinancieroPanel {
  private readonly service = inject(ReportesService);

  protected readonly t = signal<TableroFinanciero | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly exportando = signal(false);
  protected readonly mesLargo = mesLargo;
  protected readonly pesos = pesos;

  protected readonly ultimo = computed(() => this.t()!.meses[this.t()!.meses.length - 1]);
  protected readonly ultimoConCostos = computed<MesFinanciero | null>(
    () => [...(this.t()?.meses ?? [])].reverse().find((m) => m.costosRegistrados) ?? null,
  );
  protected readonly mesesDesc = computed(() => [...(this.t()?.meses ?? [])].reverse());

  protected readonly serieCostos = computed<PuntoSerie[]>(() =>
    (this.t()?.meses ?? []).map((m) => ({
      etiqueta: m.mes,
      valor: m.costoAdministrativo,
      detalle: m.variacionCostosPct === null ? undefined : `${m.variacionCostosPct.toLocaleString('es-CO')} % frente a la línea base`,
    })),
  );
  protected readonly refCostos = computed<Referencia[]>(() => {
    const t = this.t();
    return t ? [
      { valor: t.costosLineaBase, etiqueta: `Base ${compacto(t.costosLineaBase)}`, tono: 'base' },
      { valor: t.costosMeta, etiqueta: `Meta ${compacto(t.costosMeta)}`, tono: 'meta' },
    ] : [];
  });

  protected readonly serieCapacidad = computed<PuntoSerie[]>(() =>
    (this.t()?.meses ?? []).map((m) => ({
      etiqueta: m.mes,
      valor: m.atencionesPorMedico,
      detalle: m.medicos ? `${m.atenciones} atenciones · ${m.medicos} médico(s)` : undefined,
    })),
  );
  protected readonly refCapacidad = computed<Referencia[]>(() => {
    const t = this.t();
    return t ? [
      { valor: t.capacidadLineaBase, etiqueta: `Base ${compacto(t.capacidadLineaBase)}`, tono: 'base' },
      { valor: t.capacidadMeta, etiqueta: `Meta ${compacto(t.capacidadMeta)}`, tono: 'meta' },
    ] : [];
  });

  protected readonly serieRoi = computed<PuntoSerie[]>(() => {
    const r = this.t()?.roi;
    if (!r) return [];
    return r.curva.map((v, i) => ({
      etiqueta: `Mes ${i + 1}`,
      valor: v,
      detalle: i < r.mesesTranscurridos ? 'Real' : 'Proyectado',
    }));
  });

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.service.financiero().subscribe({
      next: (t) => {
        this.t.set(t);
        this.error.set(null);
      },
      error: (e: HttpErrorResponse) => this.error.set(mensajeError(e, 'No fue posible calcular el reporte financiero.')),
    });
  }

  protected pesosCortos(v: number): string {
    return `$ ${compacto(v)}`;
  }

  protected imprimir(): void {
    window.print();
  }

  protected exportar(): void {
    this.exportando.set(true);
    this.service.financieroCsv().subscribe({
      next: (res) => {
        this.exportando.set(false);
        const nombre = /filename="([^"]+)"/.exec(res.headers.get('Content-Disposition') ?? '')?.[1] ?? 'reporte-financiero.csv';
        const url = URL.createObjectURL(res.body!);
        const a = document.createElement('a');
        a.href = url;
        a.download = nombre;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (e: HttpErrorResponse) => {
        this.exportando.set(false);
        this.error.set(mensajeError(e, 'No fue posible exportar el reporte.'));
      },
    });
  }
}
