import { DatePipe } from '@angular/common';
import { Component, computed, effect, input, output, signal } from '@angular/core';

import { deFechaLocal, fechaLocal } from '../agenda.models';

/** Calendario mensual pequeño del panel de filtros (mockup). Semana de lunes a domingo. */
@Component({
  selector: 'app-mini-calendario',
  imports: [DatePipe],
  template: `
    <div class="cabecera">
      <button type="button" (click)="mover(-1)" aria-label="Mes anterior"><span class="icono">chevron_left</span></button>
      <strong>{{ mes() | date: 'MMMM yyyy' }}</strong>
      <button type="button" (click)="mover(1)" aria-label="Mes siguiente"><span class="icono">chevron_right</span></button>
    </div>
    <div class="grilla" role="grid">
      @for (d of ['L', 'M', 'M', 'J', 'V', 'S', 'D']; track $index) {
        <span class="dia-semana">{{ d }}</span>
      }
      @for (d of dias(); track d.fecha) {
        <button type="button" class="dia" [class.dia--fuera]="!d.delMes" [class.dia--hoy]="d.fecha === hoy"
          [class.dia--activo]="d.fecha === fecha()" (click)="elegida.emit(d.fecha)" [attr.aria-label]="d.fecha">
          {{ d.numero }}
        </button>
      }
    </div>
  `,
  styles: `
    .cabecera { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
    .cabecera strong::first-letter { text-transform: uppercase; }
    .cabecera button { border: 0; background: none; cursor: pointer; color: var(--c-texto-2); display: grid; }
    .grilla { display: grid; grid-template-columns: repeat(7, 1fr); gap: 2px; text-align: center; }
    .dia-semana { font-size: 11px; color: var(--c-texto-3); padding: 4px 0; }
    .dia { border: 0; background: none; font: inherit; font-size: 13px; height: 30px; border-radius: 50%; cursor: pointer; color: var(--c-texto); }
    .dia:hover { background: var(--c-azul-suave); }
    .dia--fuera { color: var(--c-texto-3); }
    .dia--hoy { font-weight: 700; color: var(--c-primario); }
    .dia--activo { background: var(--c-primario); color: #fff; }
    .dia--activo:hover { background: var(--c-primario-hover); }
  `,
})
export class MiniCalendario {
  /** Día seleccionado (YYYY-MM-DD). */
  readonly fecha = input.required<string>();
  readonly elegida = output<string>();

  protected readonly hoy = fechaLocal(new Date());
  protected readonly mes = signal(new Date());

  constructor() {
    effect(() => {
      const d = deFechaLocal(this.fecha());
      this.mes.set(new Date(d.getFullYear(), d.getMonth(), 1));
    });
  }

  protected readonly dias = computed(() => {
    const primero = this.mes();
    const desplazamiento = (primero.getDay() + 6) % 7; // lunes = 0
    const inicio = new Date(primero.getFullYear(), primero.getMonth(), 1 - desplazamiento);
    return Array.from({ length: 42 }, (_, i) => {
      const d = new Date(inicio.getFullYear(), inicio.getMonth(), inicio.getDate() + i);
      return { fecha: fechaLocal(d), numero: d.getDate(), delMes: d.getMonth() === primero.getMonth() };
    });
  });

  protected mover(meses: number): void {
    const m = this.mes();
    this.mes.set(new Date(m.getFullYear(), m.getMonth() + meses, 1));
  }
}
