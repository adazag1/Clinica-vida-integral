import { Component, signal } from '@angular/core';

import { TableroClinicoPanel } from './clinico/tablero-clinico';
import { MetasCostos } from './configuracion/metas-costos';
import { TableroFinancieroPanel } from './financiero/tablero-financiero';

type Pestana = 'clinico' | 'financiero' | 'metas';

/** Épica 5: indicadores de directivos (US 5.1 CEO, US 5.2 CFO). */
@Component({
  selector: 'app-reportes',
  imports: [TableroClinicoPanel, TableroFinancieroPanel, MetasCostos],
  template: `
    <div class="cabecera-pagina">
      <div>
        <h2>Indicadores de dirección</h2>
        <p class="sub">Avance frente a las metas del proyecto: diagnóstico más rápido, menores costos, más capacidad y ROI.</p>
      </div>
    </div>
    <div class="pildoras no-imprimir" role="tablist">
      <button type="button" role="tab" [class.activa]="pestana() === 'clinico'" [attr.aria-selected]="pestana() === 'clinico'"
        (click)="pestana.set('clinico')">Diagnóstico (CEO)</button>
      <button type="button" role="tab" [class.activa]="pestana() === 'financiero'" [attr.aria-selected]="pestana() === 'financiero'"
        (click)="pestana.set('financiero')">Costos, capacidad y ROI (CFO)</button>
      <button type="button" role="tab" [class.activa]="pestana() === 'metas'" [attr.aria-selected]="pestana() === 'metas'"
        (click)="pestana.set('metas')">Metas y costos</button>
    </div>
    <div class="contenido">
      @switch (pestana()) {
        @case ('clinico') { <app-tablero-clinico /> }
        @case ('financiero') { <app-tablero-financiero /> }
        @case ('metas') { <app-metas-costos /> }
      }
    </div>
  `,
  styles: `
    .pildoras { margin-bottom: 14px; }
    @media print { .no-imprimir { display: none !important; } }
  `,
})
export class Reportes {
  protected readonly pestana = signal<Pestana>('clinico');
}
