import { Component, input } from '@angular/core';

/** Marcador para los módulos que se implementan en fases posteriores. */
@Component({
  selector: 'app-en-construccion',
  template: `
    <section class="tarjeta pendiente">
      <span class="icono">construction</span>
      <h2>{{ titulo() }}</h2>
      <p>Este módulo se implementa en la <strong>{{ fase() }}</strong> del proyecto.</p>
    </section>
  `,
  styles: `
    .pendiente { text-align: center; padding: 56px 24px; }
    .pendiente .icono { font-size: 56px; color: var(--c-primario); }
    h2 { margin: 12px 0 8px; font-size: 20px; }
    p { margin: 0; color: var(--c-texto-2); }
  `,
})
export class EnConstruccion {
  readonly titulo = input('Módulo');
  readonly fase = input('siguiente fase');
}
