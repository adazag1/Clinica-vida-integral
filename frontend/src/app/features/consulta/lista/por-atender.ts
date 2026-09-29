import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { EMPTY, catchError, switchMap, timer } from 'rxjs';

import { mensajeError } from '../../../core/api/api';
import { ROMANO } from '../../admisiones/admisiones.models';
import { PacientePorAtender } from '../consulta.models';
import { ConsultaService } from '../consulta.service';

const ORIGEN: Record<string, string> = {
  EN_CURSO: 'Consulta en curso',
  URGENCIAS: 'Urgencias',
  CONSULTA_EXTERNA: 'Consulta externa',
  HOSPITALIZACION: 'Hospitalización',
  CITA: 'Cita de hoy',
};

/** RFMT-012: pacientes del médico, los críticos primero y destacados. Se actualiza sola. */
@Component({
  selector: 'app-por-atender',
  imports: [DatePipe, RouterLink],
  template: `
    <div class="cabecera-pagina">
      <h2>Pacientes por atender</h2>
      <a class="enlace" routerLink="/admisiones"><span class="icono">list</span> Ver lista de espera de admisiones</a>
    </div>
    @if (error()) {
      <div class="alerta alerta--error" role="alert"><span class="icono">error</span>{{ error() }}</div>
    }
    <div class="lista">
      @for (p of lista(); track (p.consultaId ?? 0) + '-' + (p.episodioId ?? 0) + '-' + (p.citaId ?? 0)) {
        <article class="tarjeta paciente" [class]="'tarjeta paciente paciente--' + p.prioridad.toLowerCase()">
          <div class="paciente__estado">
            @if (p.nivelTriage) {
              <span class="triage" [class]="'triage triage--' + p.nivelTriage">{{ romano[p.nivelTriage] }}</span>
            } @else {
              <span class="icono">{{ p.origen === 'CITA' ? 'event' : 'person' }}</span>
            }
          </div>
          <div class="paciente__datos">
            <div class="paciente__nombre">
              <strong>{{ p.paciente.nombreCompleto }}</strong>
              <span class="chip chip--info">{{ origen[p.origen] }}</span>
              @if (p.prioridad === 'CRITICO') { <span class="chip chip--critico"><span class="icono">priority_high</span>Crítico</span> }
              @if (p.alergias) { <span class="chip chip--alerta"><span class="icono">warning</span>{{ p.alergias }} alergia{{ p.alergias > 1 ? 's' : '' }}</span> }
            </div>
            <span class="meta">{{ p.paciente.edad !== null ? p.paciente.edad + ' años · ' : '' }}{{ p.paciente.tipoDocumento }} {{ p.paciente.numeroDocumento }}
              @if (p.desde) { · {{ p.origen === 'CITA' ? 'Cita ' : 'Desde ' }}{{ p.desde | date: 'h:mm a' }} }</span>
            @if (p.motivo) { <span class="motivo">{{ p.motivo }}</span> }
            @if (p.motivosPrioridad.length) {
              <span class="razones"><span class="icono">monitor_heart</span>{{ p.motivosPrioridad.join(' · ') }}</span>
            }
          </div>
          <button type="button" class="btn" [class.btn--primario]="p.origen !== 'EN_CURSO'" [class.btn--secundario]="p.origen === 'EN_CURSO'"
            (click)="atender(p)" [disabled]="abriendo()">
            <span class="icono">stethoscope</span>{{ p.origen === 'EN_CURSO' ? 'Continuar' : 'Atender' }}
          </button>
        </article>
      } @empty {
        <p class="vacio">{{ cargando() ? 'Cargando…' : 'No hay pacientes esperando consulta.' }}</p>
      }
    </div>
  `,
  styles: `
    .lista { display: flex; flex-direction: column; gap: 10px; }
    .paciente { display: flex; align-items: center; gap: 16px; padding: 14px 18px; border-left: 5px solid var(--c-borde); }
    .paciente--critico { border-left-color: var(--c-critico); background: linear-gradient(90deg, #fff5f5, #fff 40%); }
    .paciente--alerta { border-left-color: #f59e0b; }
    .paciente__estado { width: 48px; display: grid; place-items: center; }
    .paciente__estado .triage { min-width: 44px; height: 36px; font-size: 16px; }
    .paciente__estado > .icono { font-size: 30px; color: var(--c-texto-3); }
    .paciente__datos { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px; }
    .paciente__nombre { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
    .paciente__nombre strong { font-size: 15px; }
    .chip .icono { font-size: 15px; }
    .meta { color: var(--c-texto-2); font-size: 13px; }
    .motivo { font-size: 13.5px; }
    .razones { display: inline-flex; align-items: center; gap: 4px; color: var(--c-critico); font-size: 13px; font-weight: 600; }
    .razones .icono { font-size: 17px; }
    .vacio { color: var(--c-texto-2); }
    .alerta { margin-bottom: 14px; }
    @media (max-width: 640px) { .paciente { flex-wrap: wrap; } .paciente .btn { width: 100%; } }
  `,
})
export class PorAtender {
  private readonly service = inject(ConsultaService);
  private readonly router = inject(Router);

  protected readonly romano = ROMANO;
  protected readonly origen = ORIGEN;
  protected readonly lista = signal<PacientePorAtender[]>([]);
  protected readonly cargando = signal(true);
  protected readonly abriendo = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    timer(0, 30_000)
      .pipe(
        switchMap(() => this.service.porAtender().pipe(catchError((e: HttpErrorResponse) => {
          this.error.set(mensajeError(e, 'No fue posible cargar los pacientes.'));
          this.cargando.set(false);
          return EMPTY;
        }))),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((l) => {
        this.lista.set(l);
        this.cargando.set(false);
        this.error.set(null);
      });
  }

  protected atender(p: PacientePorAtender): void {
    if (p.consultaId) {
      this.router.navigate(['/consulta', p.consultaId]);
      return;
    }
    this.abriendo.set(true);
    this.service.iniciar(p.paciente.id, p.episodioId, p.citaId).subscribe({
      next: (c) => this.router.navigate(['/consulta', c.id]),
      error: (e: HttpErrorResponse) => {
        this.abriendo.set(false);
        this.error.set(mensajeError(e, 'No fue posible abrir la consulta.'));
      },
    });
  }
}
