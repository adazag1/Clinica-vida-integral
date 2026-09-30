import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { mensajeError } from '../../../core/api/api';
import { duracion } from '../../imagenes/imagenes.models';
import { Conteo, ReporteDocumental } from '../documentos.models';
import { DocumentosService } from '../documentos.service';

/** RFGAC-011: volumen documental, accesos y tiempos de atención de solicitudes. */
@Component({
  selector: 'app-reportes-documentales',
  imports: [DecimalPipe, FormsModule, RouterLink],
  template: `
    <a class="volver" routerLink="/documentos"><span class="icono">arrow_back</span> Gestión documental</a>
    <div class="cabecera-pagina">
      <h2>Reportes documentales</h2>
      <div class="periodo">
        <input class="control" type="date" [(ngModel)]="desde" aria-label="Desde" />
        <input class="control" type="date" [(ngModel)]="hasta" aria-label="Hasta" />
        <button type="button" class="btn btn--primario btn--pequeno" (click)="cargar()">Consultar</button>
      </div>
    </div>
    @if (error()) { <div class="alerta alerta--error" role="alert"><span class="icono">error</span>{{ error() }}</div> }

    @if (reporte(); as r) {
      <div class="indicadores">
        <div class="tarjeta"><strong>{{ r.totalDocumentos | number }}</strong><span>documentos en total</span></div>
        <div class="tarjeta"><strong>{{ r.solicitudesPeriodo }}</strong><span>solicitudes en el periodo</span></div>
        <div class="tarjeta"><strong>{{ duracion(r.minutosPromedioEntrega) }}</strong><span>tiempo promedio de entrega</span></div>
        <div class="tarjeta" [class.alerta-ind]="r.solicitudesPendientes > 0"><strong>{{ r.solicitudesPendientes }}</strong><span>solicitudes pendientes</span></div>
      </div>
      <div class="rejilla">
        @for (b of bloques(r); track b.titulo) {
          <section class="tarjeta">
            <div class="tarjeta__titulo">{{ b.titulo }}</div>
            @for (c of b.datos; track c.clave) {
              <div class="barra">
                <span class="etiqueta">{{ c.etiqueta }}</span>
                <span class="fondo"><span class="relleno" [style.width.%]="porcentaje(c, b.datos)"></span></span>
                <strong>{{ c.cantidad | number }}</strong>
              </div>
            } @empty {
              <p class="vacio">Sin datos.</p>
            }
          </section>
        }
      </div>
    }
  `,
  styles: `
    .periodo { display: flex; gap: 6px; align-items: center; }
    .periodo .control { width: 150px; }
    .alerta { margin-bottom: 12px; }
    .indicadores { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 12px; margin-bottom: 14px; }
    .indicadores .tarjeta { display: flex; flex-direction: column; }
    .indicadores strong { font-size: 24px; }
    .indicadores span { color: var(--c-texto-2); font-size: 13px; }
    .alerta-ind strong { color: var(--c-alerta); }
    .rejilla { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 14px; }
    .barra { display: grid; grid-template-columns: minmax(120px, 42%) 1fr 48px; align-items: center; gap: 8px; margin-bottom: 8px; font-size: 13px; }
    .etiqueta { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .fondo { height: 10px; border-radius: 999px; background: var(--c-fondo); overflow: hidden; }
    .relleno { display: block; height: 100%; background: var(--c-azul); border-radius: 999px; }
    .barra strong { text-align: right; }
  `,
})
export class ReportesDocumentales {
  private readonly service = inject(DocumentosService);

  protected readonly duracion = duracion;
  protected readonly reporte = signal<ReporteDocumental | null>(null);
  protected readonly error = signal<string | null>(null);
  protected desde = new Date(Date.now() - 30 * 86_400_000).toLocaleDateString('en-CA');
  protected hasta = new Date().toLocaleDateString('en-CA');

  constructor() {
    this.cargar();
  }

  protected cargar(): void {
    this.service.reporte(this.desde, this.hasta).subscribe({
      next: (r) => {
        this.reporte.set(r);
        this.error.set(null);
      },
      error: (e: HttpErrorResponse) => this.error.set(mensajeError(e, 'No fue posible generar el reporte.')),
    });
  }

  protected bloques(r: ReporteDocumental): { titulo: string; datos: Conteo[] }[] {
    return [
      { titulo: 'Volumen por tipo de documento', datos: r.porTipo },
      { titulo: 'Documentos por estado', datos: r.porEstado },
      { titulo: 'Origen', datos: r.porOrigen },
      { titulo: 'Actividad en el periodo', datos: r.accionesPeriodo },
      { titulo: 'Quién consultó documentos', datos: r.accesosPorUsuario },
      { titulo: 'Solicitudes por estado', datos: r.solicitudesPorEstado },
    ];
  }

  protected porcentaje(c: Conteo, datos: Conteo[]): number {
    const max = Math.max(...datos.map((d) => d.cantidad), 1);
    return (100 * c.cantidad) / max;
  }
}
