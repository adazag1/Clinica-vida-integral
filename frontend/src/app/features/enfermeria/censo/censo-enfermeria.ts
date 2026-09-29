import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { interval } from 'rxjs';

import { mensajeError } from '../../../core/api/api';
import { PacienteCenso } from '../enfermeria.models';
import { EnfermeriaService } from '../enfermeria.service';

const sinTildes = (s: string) => s.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim();

/** Pacientes a cargo de enfermería: lo atrasado y lo crítico primero. Se refresca solo cada minuto. */
@Component({
  selector: 'app-censo-enfermeria',
  imports: [FormsModule, RouterLink],
  template: `
    <div class="cabecera-pagina">
      <h2>Enfermería · pacientes a cargo</h2>
      <input class="buscar" [ngModel]="filtro()" (ngModelChange)="filtro.set($event)" placeholder="Buscar paciente o documento"
        aria-label="Buscar paciente" />
    </div>

    <div class="resumen">
      <div><strong>{{ pacientes().length }}</strong><span>pacientes</span></div>
      <div class="resumen--critico"><strong>{{ totalAtrasadas() }}</strong><span>dosis o cuidados atrasados</span></div>
      <div><strong>{{ totalProximas() }}</strong><span>en las próximas 2 horas</span></div>
    </div>

    @if (error()) {
      <div class="alerta alerta--error" role="alert"><span class="icono">error</span>{{ error() }}</div>
    }

    <div class="lista">
      @for (p of visibles(); track p.paciente.id) {
        <a class="paciente tarjeta" [class]="'paciente tarjeta paciente--' + p.prioridad.toLowerCase()"
          [routerLink]="['/enfermeria/pacientes', p.paciente.id]">
          <div class="paciente__cabecera">
            <div>
              <strong>{{ p.paciente.nombreCompleto }}</strong>
              <small>{{ p.paciente.tipoDocumento }} {{ p.paciente.numeroDocumento }}{{ p.paciente.edad !== null ? ' · ' + p.paciente.edad + ' años' : '' }}</small>
            </div>
            <span class="chip chip--info">{{ p.ubicacion }}</span>
          </div>
          <div class="contadores">
            <span [class.malo]="p.dosisAtrasadas > 0"><span class="icono">alarm</span>{{ p.dosisAtrasadas }} atrasadas</span>
            <span><span class="icono">schedule</span>{{ p.proximasDosHoras }} próximas</span>
            @if (p.alergias > 0) {
              <span class="alergia"><span class="icono">warning</span>{{ p.alergias }} {{ p.alergias === 1 ? 'alergia' : 'alergias' }}</span>
            }
          </div>
          @if (p.alertas.length) {
            <ul class="alertas">
              @for (a of p.alertas; track $index) { <li>{{ a }}</li> }
            </ul>
          }
        </a>
      } @empty {
        <p class="vacio">{{ cargando() ? 'Cargando…' : 'No hay pacientes con órdenes activas ni en urgencias u hospitalización.' }}</p>
      }
    </div>
  `,
  styles: `
    .cabecera-pagina { flex-wrap: wrap; gap: 10px; }
    .buscar { height: 38px; min-width: 260px; padding: 0 12px; border: 1px solid var(--c-borde); border-radius: 8px; font: inherit; }
    .resumen { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 10px; margin-bottom: 16px; }
    .resumen > div { display: flex; flex-direction: column; padding: 12px 14px; border: 1px solid var(--c-borde); border-radius: var(--radio-sm);
      background: var(--c-superficie); }
    .resumen strong { font-size: 24px; }
    .resumen span { color: var(--c-texto-2); font-size: 13px; }
    .resumen--critico strong { color: var(--c-critico); }
    .lista { display: grid; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); gap: 12px; }
    .paciente { display: flex; flex-direction: column; gap: 10px; color: inherit; border-left: 5px solid var(--c-normal); }
    .paciente:hover { border-color: var(--c-azul); }
    .paciente--critico { border-left-color: var(--c-critico); }
    .paciente--alerta { border-left-color: var(--c-alerta); }
    .paciente__cabecera { display: flex; justify-content: space-between; gap: 8px; align-items: flex-start; }
    .paciente__cabecera div { display: flex; flex-direction: column; }
    .paciente__cabecera small { color: var(--c-texto-2); }
    .contadores { display: flex; flex-wrap: wrap; gap: 14px; font-size: 13.5px; color: var(--c-texto-2); }
    .contadores > span { display: inline-flex; align-items: center; gap: 4px; }
    .contadores .icono { font-size: 18px; }
    .malo { color: var(--c-critico); font-weight: 600; }
    .alergia { color: var(--c-alerta); }
    .alertas { margin: 0; padding-left: 18px; font-size: 13px; color: var(--c-critico); }
    .vacio { color: var(--c-texto-2); }
    .alerta { margin-bottom: 14px; }
    @media (max-width: 640px) { .lista { grid-template-columns: 1fr; } .buscar { min-width: 0; width: 100%; } }
  `,
})
export class CensoEnfermeria {
  private readonly service = inject(EnfermeriaService);

  protected readonly pacientes = signal<PacienteCenso[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly filtro = signal('');

  protected readonly visibles = computed(() => {
    const f = sinTildes(this.filtro());
    return f
      ? this.pacientes().filter((p) => sinTildes(p.paciente.nombreCompleto + ' ' + p.paciente.numeroDocumento).includes(f))
      : this.pacientes();
  });
  protected readonly totalAtrasadas = computed(() => this.pacientes().reduce((s, p) => s + p.dosisAtrasadas, 0));
  protected readonly totalProximas = computed(() => this.pacientes().reduce((s, p) => s + p.proximasDosHoras, 0));

  constructor() {
    this.cargar();
    interval(60_000).pipe(takeUntilDestroyed(inject(DestroyRef))).subscribe(() => this.cargar());
  }

  private cargar(): void {
    this.service.censo().subscribe({
      next: (l) => {
        this.pacientes.set(l);
        this.cargando.set(false);
        this.error.set(null);
      },
      error: (e: HttpErrorResponse) => {
        this.cargando.set(false);
        this.error.set(mensajeError(e, 'No fue posible cargar los pacientes.'));
      },
    });
  }
}
