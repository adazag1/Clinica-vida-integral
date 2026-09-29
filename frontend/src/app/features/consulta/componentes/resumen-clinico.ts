import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ROMANO } from '../../admisiones/admisiones.models';
import { claseInterpretacion } from '../../laboratorio/laboratorio.models';
import { ResumenClinico } from '../consulta.models';

/**
 * RFMT-002: "panel resumen clínico" con lo que el médico pidió ver en los primeros minutos,
 * en el orden de la entrevista: alergias, medicamentos, diagnósticos, signos y triage, cirugías.
 */
@Component({
  selector: 'app-resumen-clinico',
  imports: [DatePipe, DecimalPipe, RouterLink],
  template: `
    @let r = resumen();
    @if (r.alertas.length) {
      <ul class="alertas" role="alert">
        @for (a of r.alertas; track a) {
          <li><span class="icono">error</span>{{ a }}</li>
        }
      </ul>
    }

    <section class="bloque bloque--alergias">
      <h4><span class="icono">warning</span>Alergias</h4>
      @for (a of r.alergias; track a.id) {
        <p class="item"><strong>{{ a.descripcion }}</strong>
          @if (a.severidad) { <span class="chip" [class]="'chip ' + (a.severidad === 'SEVERA' ? 'chip--critico' : 'chip--alerta')">{{ a.severidad.toLowerCase() }}</span> }
          @if (a.detalle) { <small>{{ a.detalle }}</small> }
        </p>
      } @empty {
        <p class="vacio">Sin alergias conocidas registradas</p>
      }
    </section>

    <section class="bloque">
      <h4><span class="icono">medication</span>Medicamentos actuales</h4>
      @for (m of r.medicamentosActuales; track $index) {
        <p class="item"><strong>{{ m.nombre }}</strong>@if (m.detalle) { <small>{{ m.detalle }}</small> }<small class="origen">{{ m.origen }}</small></p>
      } @empty {
        <p class="vacio">Ninguno registrado</p>
      }
    </section>

    <section class="bloque">
      <h4><span class="icono">monitor_heart</span>Diagnósticos activos</h4>
      @for (d of r.diagnosticosActivos; track $index) {
        <p class="item"><strong>{{ d.codigo ? d.codigo + ' · ' : '' }}{{ d.nombre }}</strong><small class="origen">{{ d.origen }} · {{ d.fecha | date: 'dd/MM/yyyy' }}</small></p>
      } @empty {
        <p class="vacio">Ninguno registrado</p>
      }
    </section>

    @if (r.triage; as t) {
      <section class="bloque">
        <h4><span class="icono">emergency</span>Triage actual</h4>
        <p class="item triage-linea"><span class="triage" [class]="'triage triage--' + t.nivel">{{ romano[t.nivel] }}</span>
          <span>{{ t.sintomaPrincipal }}</span></p>
      </section>
    }

    <section class="bloque">
      <h4><span class="icono">surgical</span>Quirúrgicos y otros</h4>
      @for (a of r.antecedentesQuirurgicos.concat(r.otrosAntecedentes); track a.id) {
        <p class="item"><strong>{{ a.descripcion }}</strong><small>{{ a.tipoDescripcion }}{{ a.detalle ? ' · ' + a.detalle : '' }}</small></p>
      } @empty {
        <p class="vacio">Ninguno registrado</p>
      }
    </section>

    <section class="bloque">
      <h4><span class="icono">science</span>Resultados recientes</h4>
      @for (x of r.resultadosRecientes; track $index) {
        <a class="resultado" [routerLink]="['/laboratorio/ordenes', x.ordenId]">
          <span>{{ x.analito }}</span>
          <strong [class]="'nivel--' + x.interpretacion.toLowerCase()">{{ x.valor | number: '1.' + x.decimales + '-' + x.decimales }} {{ x.unidad }}</strong>
          <span class="chip" [class]="'chip ' + clase(x.interpretacion)">{{ x.interpretacionDescripcion }}</span>
          <small>{{ x.rangoTexto }} · {{ x.fecha | date: 'dd/MM' }}</small>
        </a>
      } @empty {
        <p class="vacio">Sin resultados de laboratorio validados. Las imágenes se integran en la Fase 7.</p>
      }
    </section>
  `,
  styles: `
    :host { display: flex; flex-direction: column; gap: 14px; }
    .alertas { list-style: none; margin: 0; padding: 10px 12px; border-radius: var(--radio-sm); background: var(--c-critico-suave);
      color: #991b1b; display: flex; flex-direction: column; gap: 4px; font-size: 13px; font-weight: 600; }
    .alertas li { display: flex; gap: 6px; align-items: center; }
    .alertas .icono { font-size: 18px; }
    h4 { display: flex; align-items: center; gap: 6px; font-size: 13px; margin: 0 0 6px; color: var(--c-texto-2);
      text-transform: uppercase; letter-spacing: 0.03em; }
    h4 .icono { font-size: 18px; }
    .bloque--alergias h4 { color: var(--c-critico); }
    .item { margin: 0 0 6px; display: flex; flex-wrap: wrap; align-items: center; gap: 4px 8px; font-size: 13.5px; }
    .item small { color: var(--c-texto-2); font-size: 12px; width: 100%; }
    .item .chip { padding: 2px 8px; }
    .origen { color: var(--c-texto-3) !important; }
    .triage-linea { flex-wrap: nowrap; }
    .vacio { margin: 0; color: var(--c-texto-3); font-size: 13px; }
    .resultado { display: grid; grid-template-columns: 1fr auto auto; align-items: center; gap: 2px 8px; padding: 6px 0;
      border-bottom: 1px solid var(--c-borde); color: inherit; font-size: 13px; }
    .resultado small { grid-column: 1 / -1; color: var(--c-texto-3); font-size: 11.5px; }
    .resultado .chip { padding: 2px 8px; font-size: 11px; }
    .nivel--alto, .nivel--bajo { color: var(--c-alerta); }
    .nivel--critico_alto, .nivel--critico_bajo { color: var(--c-critico); }
  `,
})
export class ResumenClinicoPanel {
  readonly resumen = input.required<ResumenClinico>();
  protected readonly romano = ROMANO;
  protected readonly clase = claseInterpretacion;
}
