import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';

import { NivelAlerta, Signos } from '../consulta.models';

/** Tarjetas de signos vitales del mockup, con semáforo según las alertas (RFMT-010). */
@Component({
  selector: 'app-tarjetas-signos',
  imports: [DatePipe],
  template: `
    @if (signos(); as s) {
      <div class="tarjetas">
        @for (t of tarjetas(); track t.campo) {
          <div class="signo" [class]="'signo signo--' + t.nivel.toLowerCase()" [attr.title]="t.mensaje">
            <span class="icono">{{ t.icono }}</span>
            <span class="signo__nombre">{{ t.nombre }}</span>
            <strong>{{ t.valor }}</strong>
            <span class="signo__unidad">{{ t.unidad }}</span>
          </div>
        }
      </div>
      <p class="fecha"><span class="icono">schedule</span>{{ s.fecha | date: 'dd/MM/yyyy h:mm a' }}
        · {{ s.origen === 'TRIAGE' ? 'Triage' : 'Registrado' }} por {{ s.registradoPor }}</p>
    } @else {
      <p class="vacio">Sin signos vitales registrados.</p>
    }
  `,
  styles: `
    .tarjetas { display: grid; grid-template-columns: repeat(auto-fill, minmax(110px, 1fr)); gap: 10px; }
    .signo { display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 12px 8px; border: 1px solid var(--c-borde);
      border-radius: var(--radio-sm); text-align: center; }
    .signo .icono { font-size: 26px; color: var(--c-azul); }
    .signo__nombre { font-size: 12px; color: var(--c-texto-2); }
    .signo strong { font-size: 20px; }
    .signo__unidad { font-size: 11px; color: var(--c-texto-3); }
    .signo--anormal { border-color: #f5cf8f; background: var(--c-alerta-suave); }
    .signo--anormal .icono, .signo--anormal strong { color: var(--c-alerta); }
    .signo--critico { border-color: #f4b4b4; background: var(--c-critico-suave); }
    .signo--critico .icono, .signo--critico strong { color: var(--c-critico); }
    .signo--sin strong { color: var(--c-texto-3); }
    .fecha { display: flex; align-items: center; gap: 6px; margin: 10px 0 0; color: var(--c-texto-2); font-size: 13px; }
    .fecha .icono { font-size: 18px; }
    .vacio { color: var(--c-texto-3); margin: 0; }
  `,
})
export class TarjetasSignos {
  readonly signos = input<Signos | null>(null);

  protected readonly tarjetas = computed(() => {
    const s = this.signos();
    if (!s) return [];
    const nivel = (...campos: string[]): { nivel: NivelAlerta | 'SIN'; mensaje: string } => {
      const alertas = s.alertas.filter((a) => campos.includes(a.campo));
      const critica = alertas.find((a) => a.nivel === 'CRITICO');
      const a = critica ?? alertas[0];
      return { nivel: a ? a.nivel : 'NORMAL', mensaje: alertas.map((x) => x.mensaje).join('\n') };
    };
    const sin = (v: unknown) => v === null || v === undefined;
    const numero = (v: number | null, dec = 0) => (sin(v) ? '—' : new DecimalPipe('es-CO').transform(v, `1.${dec}-${dec}`)!);
    const pa = nivel('presionSistolica', 'presionDiastolica');
    const fc = nivel('frecuenciaCardiaca');
    const t = nivel('temperatura');
    const sat = nivel('saturacionOxigeno');
    const fr = nivel('frecuenciaRespiratoria');
    return [
      { campo: 'pa', icono: 'favorite', nombre: 'Presión arterial', unidad: 'mmHg',
        valor: sin(s.presionSistolica) ? '—' : `${s.presionSistolica}/${s.presionDiastolica ?? '—'}`, ...pa },
      { campo: 'fc', icono: 'ecg_heart', nombre: 'Frecuencia cardiaca', unidad: 'lpm', valor: numero(s.frecuenciaCardiaca), ...fc },
      { campo: 't', icono: 'thermostat', nombre: 'Temperatura', unidad: '°C', valor: numero(s.temperatura, 1), ...t },
      { campo: 'sat', icono: 'spo2', nombre: 'Saturación', unidad: '%', valor: numero(s.saturacionOxigeno), ...sat },
      { campo: 'fr', icono: 'pulmonology', nombre: 'Frec. respiratoria', unidad: 'rpm', valor: numero(s.frecuenciaRespiratoria), ...fr },
      { campo: 'peso', icono: 'monitor_weight', nombre: 'Peso', unidad: 'kg', valor: numero(s.peso, 1), nivel: 'NORMAL', mensaje: '' },
    ].map((x) => ({ ...x, nivel: x.valor === '—' ? 'SIN' : x.nivel }));
  });
}
