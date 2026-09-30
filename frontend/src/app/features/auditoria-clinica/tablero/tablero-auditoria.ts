import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { mensajeError } from '../../../core/api/api';
import { Conteo, Tablero } from '../auditoria.models';
import { AuditoriaClinicaService } from '../auditoria.service';

/** RFAU-006: devoluciones, tiempos de corrección, historias incompletas y glosas por causa clínica. */
@Component({
  selector: 'app-tablero-auditoria',
  imports: [CurrencyPipe, DecimalPipe, FormsModule, RouterLink],
  template: `
    <a class="volver" routerLink="/auditoria-clinica"><span class="icono">arrow_back</span> Auditoría clínica</a>
    <div class="cabecera-pagina">
      <h2>Indicadores de auditoría</h2>
      <div class="periodo">
        <input class="control" type="date" [(ngModel)]="desde" aria-label="Desde" />
        <input class="control" type="date" [(ngModel)]="hasta" aria-label="Hasta" />
        <button type="button" class="btn btn--primario btn--pequeno" (click)="cargar()">Consultar</button>
      </div>
    </div>
    @if (error()) { <div class="alerta alerta--error" role="alert"><span class="icono">error</span>{{ error() }}</div> }
    @if (t(); as t) {
      <div class="indicadores">
        <div class="tarjeta"><strong>{{ t.cuentasEnAuditoria }}</strong><span>cuentas por auditar</span></div>
        <div class="tarjeta" [class.malo]="t.cuentasDevueltas > 0"><strong>{{ t.cuentasDevueltas }}</strong><span>devueltas sin corregir</span></div>
        <div class="tarjeta"><strong>{{ t.auditadasPeriodo }}</strong><span>auditadas en el periodo</span></div>
        <div class="tarjeta"><strong>{{ t.tasaDevolucionPct === null ? '—' : (t.tasaDevolucionPct | number: '1.0-1') + ' %' }}</strong><span>tasa de devolución</span></div>
        <div class="tarjeta"><strong>{{ t.horasPromedioCorreccion === null ? '—' : (t.horasPromedioCorreccion | number: '1.0-1') + ' h' }}</strong><span>tiempo promedio de corrección</span></div>
        <div class="tarjeta" [class.malo]="t.consultasSinFirmar > 0"><strong>{{ t.consultasSinFirmar }}</strong><span>historias sin firmar (&gt; 24 h)</span></div>
        <div class="tarjeta" [class.malo]="t.cargosSinSoporte > 0"><strong>{{ t.cargosSinSoporte }}</strong><span>cargos sin soporte</span></div>
        <div class="tarjeta"><strong>{{ t.valorGlosasClinicas | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</strong><span>{{ t.glosasClinicas }} glosa(s) por causa clínica</span></div>
      </div>
      <div class="rejilla">
        <section class="tarjeta">
          <div class="tarjeta__titulo">Devoluciones por motivo</div>
          @for (c of t.devolucionesPorMotivo; track c.clave) {
            <div class="barra"><span>{{ c.etiqueta }}</span><span class="fondo"><span class="relleno" [style.width.%]="pct(c, t.devolucionesPorMotivo)"></span></span>
              <strong>{{ c.cantidad }}</strong><small>{{ c.promedioHoras === null ? '' : (c.promedioHoras | number: '1.0-1') + ' h' }}</small></div>
          } @empty { <p class="vacio">Sin devoluciones en el periodo.</p> }
        </section>
        <section class="tarjeta">
          <div class="tarjeta__titulo">Glosas por concepto</div>
          @for (c of t.glosasPorConcepto; track c.clave) {
            <div class="barra"><span>{{ c.etiqueta }}</span><span class="fondo"><span class="relleno relleno--glosa" [style.width.%]="pct(c, t.glosasPorConcepto)"></span></span>
              <strong>{{ c.cantidad }}</strong><small>{{ c.valor | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</small></div>
          } @empty { <p class="vacio">Sin glosas en el periodo.</p> }
        </section>
      </div>
    }
  `,
  styles: `
    .periodo { display: flex; gap: 6px; align-items: center; }
    .periodo .control { width: 150px; }
    .alerta { margin-bottom: 12px; }
    .indicadores { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px; margin-bottom: 14px; }
    .indicadores .tarjeta { display: flex; flex-direction: column; }
    .indicadores strong { font-size: 24px; }
    .indicadores span { color: var(--c-texto-2); font-size: 13px; }
    .malo strong { color: var(--c-critico); }
    .rejilla { display: grid; grid-template-columns: repeat(auto-fit, minmax(340px, 1fr)); gap: 14px; }
    .barra { display: grid; grid-template-columns: minmax(140px, 45%) 1fr 30px 90px; align-items: center; gap: 8px; margin-bottom: 8px; font-size: 13px; }
    .barra strong { text-align: right; }
    .barra small { color: var(--c-texto-2); text-align: right; }
    .fondo { height: 10px; border-radius: 999px; background: var(--c-fondo); overflow: hidden; }
    .relleno { display: block; height: 100%; background: var(--c-alerta); border-radius: 999px; }
    .relleno--glosa { background: var(--c-critico); }
  `,
})
export class TableroAuditoria {
  private readonly service = inject(AuditoriaClinicaService);

  protected readonly t = signal<Tablero | null>(null);
  protected readonly error = signal<string | null>(null);
  protected desde = new Date(Date.now() - 30 * 86_400_000).toLocaleDateString('en-CA');
  protected hasta = new Date().toLocaleDateString('en-CA');

  constructor() {
    this.cargar();
  }

  protected cargar(): void {
    this.service.tablero(this.desde, this.hasta).subscribe({
      next: (t) => {
        this.t.set(t);
        this.error.set(null);
      },
      error: (e: HttpErrorResponse) => this.error.set(mensajeError(e, 'No fue posible calcular los indicadores.')),
    });
  }

  protected pct(c: Conteo, lista: Conteo[]): number {
    return (100 * c.cantidad) / Math.max(1, ...lista.map((x) => x.cantidad));
  }
}
