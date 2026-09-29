import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { erroresCampos, mensajeError } from '../../../core/api/api';
import { MetricaImagenes, TipoEstudio, duracion } from '../imagenes.models';
import { ImagenesService } from '../imagenes.service';

const hoy = () => new Date().toLocaleDateString('en-CA');
const haceDias = (d: number) => new Date(Date.now() - d * 86_400_000).toLocaleDateString('en-CA');

/** RFRAD-004 / 006 / 012: tiempos objetivo, plantillas de informe y métricas de respuesta. */
@Component({
  selector: 'app-configuracion-imagenes',
  imports: [DecimalPipe, FormsModule, RouterLink],
  template: `
    <a class="volver" routerLink="/imagenes"><span class="icono">arrow_back</span> Lista de trabajo</a>
    <div class="cabecera-pagina"><h2>Plantillas, tiempos y métricas</h2></div>

    <section class="tarjeta">
      <div class="titulo-accion">
        <div class="tarjeta__titulo"><span class="icono">monitoring</span>Tiempos de respuesta por tipo de estudio</div>
        <div class="periodo">
          <input class="control" type="date" [(ngModel)]="desde" aria-label="Desde" />
          <input class="control" type="date" [(ngModel)]="hasta" aria-label="Hasta" />
          <button type="button" class="btn btn--secundario btn--pequeno" (click)="cargarMetricas()">Consultar</button>
        </div>
      </div>
      <div class="tabla-contenedor">
        <table class="tabla">
          <thead><tr><th>Estudio</th><th>Informados</th><th>Promedio orden → informe</th><th>Mediana</th><th>Lectura (imágenes → informe)</th>
            <th>Dentro del objetivo</th><th>Pendientes</th><th>Vencidos</th></tr></thead>
          <tbody>
            @for (m of metricas(); track m.estudio) {
              <tr>
                <td><strong>{{ m.estudio }}</strong><br /><small>{{ m.modalidad }}</small></td>
                <td>{{ m.informados }}</td>
                <td>{{ duracion(m.promedioMin) }}</td>
                <td>{{ duracion(m.medianaMin) }}</td>
                <td>{{ duracion(m.promedioLecturaMin) }}</td>
                <td>{{ m.dentroDeTatPct === null ? '—' : (m.dentroDeTatPct | number: '1.0-1') + ' %' }}</td>
                <td>{{ m.pendientes }}</td>
                <td [class.malo]="m.pendientesVencidos > 0">{{ m.pendientesVencidos }}</td>
              </tr>
            } @empty {
              <tr><td colspan="8" class="vacio">Sin estudios en el periodo.</td></tr>
            }
          </tbody>
        </table>
      </div>
    </section>

    <section class="tarjeta">
      <div class="tarjeta__titulo"><span class="icono">tune</span>Tipos de estudio</div>
      <p class="ayuda">El tiempo objetivo (en minutos) define la prioridad en la lista y la alerta de tiempo excedido. La plantilla precarga el informe.</p>
      @for (t of tipos(); track t.id) {
        <div class="tipo">
          <button type="button" class="tipo__cabecera" (click)="abierto.set(abierto() === t.id ? null : t.id)">
            <span><strong>{{ t.nombre }}</strong> <small>{{ t.modalidadDescripcion }} · {{ t.region }}</small></span>
            <small>Urgente {{ duracion(t.tatUrgenteMin) }} · Hospitalizado {{ duracion(t.tatHospitalizadoMin) }} · Ambulatorio {{ duracion(t.tatAmbulatorioMin) }}</small>
            <span class="icono">{{ abierto() === t.id ? 'expand_less' : 'expand_more' }}</span>
          </button>
          @if (abierto() === t.id) {
            <div class="form-rejilla">
              <label>Urgente (min)<input class="control" type="number" min="5" [(ngModel)]="t.tatUrgenteMin" /></label>
              <label>Hospitalizado (min)<input class="control" type="number" min="5" [(ngModel)]="t.tatHospitalizadoMin" /></label>
              <label>Ambulatorio (min)<input class="control" type="number" min="5" [(ngModel)]="t.tatAmbulatorioMin" /></label>
              <label class="ancho">Plantilla de hallazgos<textarea class="control" rows="5" [(ngModel)]="t.plantillaHallazgos"></textarea></label>
              <label class="ancho">Plantilla de impresión<textarea class="control" rows="2" [(ngModel)]="t.plantillaImpresion"></textarea></label>
              <label class="ancho">Plantilla de recomendaciones<textarea class="control" rows="2" [(ngModel)]="t.plantillaRecomendaciones"></textarea></label>
            </div>
            @if (errores()[t.id]) { <p class="campo__error">{{ errores()[t.id] }}</p> }
            <div class="acciones">
              @if (t.modificadoPor) { <small>Modificado por {{ t.modificadoPor }}</small> }
              <button type="button" class="btn btn--primario btn--pequeno" (click)="guardar(t)">Guardar</button>
            </div>
          }
        </div>
      }
    </section>
  `,
  styles: `
    .tarjeta { margin-bottom: 14px; }
    .periodo { display: flex; gap: 6px; align-items: center; }
    .periodo .control { width: 150px; }
    .ayuda { color: var(--c-texto-2); font-size: 13px; margin-top: 0; }
    .tipo { border: 1px solid var(--c-borde); border-radius: var(--radio-sm); margin-bottom: 8px; }
    .tipo .form-rejilla, .tipo .acciones, .tipo .campo__error { padding: 0 12px 12px; }
    .tipo__cabecera { width: 100%; display: flex; align-items: center; gap: 12px; padding: 10px 12px; border: 0; background: none; font: inherit;
      color: inherit; text-align: left; cursor: pointer; flex-wrap: wrap; }
    .tipo__cabecera > span:first-child { flex: 1; }
    small { color: var(--c-texto-2); }
    .malo { color: var(--c-critico); font-weight: 600; }
    .acciones { align-items: center; }
  `,
})
export class ConfiguracionImagenes {
  private readonly service = inject(ImagenesService);

  protected readonly duracion = duracion;
  protected readonly tipos = signal<TipoEstudio[]>([]);
  protected readonly metricas = signal<MetricaImagenes[]>([]);
  protected readonly abierto = signal<number | null>(null);
  protected readonly errores = signal<Record<number, string>>({});
  protected desde = haceDias(30);
  protected hasta = hoy();

  constructor() {
    this.service.tipos(true).subscribe((t) => this.tipos.set(t.map((x) => ({ ...x }))));
    this.cargarMetricas();
  }

  protected cargarMetricas(): void {
    this.service.metricas(this.desde, this.hasta).subscribe({ next: (m) => this.metricas.set(m) });
  }

  protected guardar(t: TipoEstudio): void {
    this.service
      .configurarTipo(t.id, {
        tatUrgenteMin: Number(t.tatUrgenteMin), tatHospitalizadoMin: Number(t.tatHospitalizadoMin),
        tatAmbulatorioMin: Number(t.tatAmbulatorioMin), plantillaHallazgos: t.plantillaHallazgos,
        plantillaImpresion: t.plantillaImpresion, plantillaRecomendaciones: t.plantillaRecomendaciones,
      })
      .subscribe({
        next: (actualizado) => {
          this.tipos.update((l) => l.map((x) => (x.id === t.id ? actualizado : x)));
          this.errores.update((e) => ({ ...e, [t.id]: '' }));
          this.abierto.set(null);
          this.service.tipos(true);
        },
        error: (e: HttpErrorResponse) => {
          const campos = erroresCampos(e);
          this.errores.update((x) => ({ ...x, [t.id]: Object.keys(campos).length ? Object.values(campos).join('. ') : mensajeError(e, 'No se pudo guardar.') }));
        },
      });
  }
}
