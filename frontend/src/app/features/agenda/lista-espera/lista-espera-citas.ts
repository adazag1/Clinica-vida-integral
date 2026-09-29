import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { mensajeError } from '../../../core/api/api';
import { AuthService } from '../../../core/auth/auth.service';
import { EntradaListaEspera } from '../agenda.models';
import { AgendaService } from '../agenda.service';

/** RFAGE-007: pacientes esperando cupo, en el orden en que se les asignará. */
@Component({
  selector: 'app-lista-espera-citas',
  imports: [DatePipe, RouterLink],
  template: `
    <a class="volver" routerLink="/agenda"><span class="icono">arrow_back</span> Agenda</a>
    <div class="cabecera-pagina">
      <h2>Lista de espera</h2>
      @if (puedeEditar()) {
        <a class="btn btn--primario" routerLink="/agenda"><span class="icono">add</span>Agregar desde "Nueva cita"</a>
      }
    </div>
    <div class="alerta alerta--info">
      <span class="icono">info</span>
      <span>Cuando se cancela o reprograma una cita, el cupo se asigna automáticamente al primer paciente de esta lista
        que coincida en especialidad (y médico, si pidió uno) y cuya cita quepa en el horario liberado. Agendamiento
        recibe un aviso para llamarlo y confirmar.</span>
    </div>
    @if (error()) {
      <div class="alerta alerta--error" role="alert"><span class="icono">error</span>{{ error() }}</div>
    }
    <section class="tarjeta">
      @if (entradas().length) {
        <div class="tabla-contenedor">
          <table class="tabla">
            <thead>
              <tr><th>#</th><th>Prioridad</th><th>Paciente</th><th>Especialidad</th><th>Profesional</th><th>Tipo</th><th>Desde</th><th>Motivo</th><th></th></tr>
            </thead>
            <tbody>
              @for (e of entradas(); track e.id; let i = $index) {
                <tr>
                  <td>{{ i + 1 }}</td>
                  <td><span class="chip" [class]="'chip ' + clase(e.prioridad)">{{ e.prioridadDescripcion }}</span></td>
                  <td><a [routerLink]="['/pacientes', e.paciente.id]">{{ e.paciente.nombreCompleto }}</a>
                    <br /><small>{{ e.paciente.telefono ?? 'Sin teléfono' }}</small></td>
                  <td>{{ e.especialidad }}</td>
                  <td>{{ e.profesional?.nombre ?? 'Cualquiera' }}</td>
                  <td>{{ e.tipoCita }} ({{ e.duracionMinutos }} min)</td>
                  <td class="nowrap">{{ e.desde | date: 'dd/MM/yyyy h:mm a' }}</td>
                  <td>{{ e.motivo ?? '—' }}</td>
                  <td class="nowrap">
                    @if (puedeEditar()) {
                      <a class="btn btn--secundario btn--pequeno" [routerLink]="['/agenda']" [queryParams]="{ paciente: e.paciente.id }">Buscar cupo</a>
                      <button type="button" class="btn btn--peligro btn--pequeno" (click)="retirar(e)">Retirar</button>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      } @else {
        <p class="vacio">{{ cargando() ? 'Cargando…' : 'No hay pacientes en lista de espera.' }}</p>
      }
    </section>
  `,
  styles: `
    .alerta { margin-bottom: 16px; }
    .nowrap { white-space: nowrap; }
    td .btn + .btn { margin-left: 6px; }
    small { color: var(--c-texto-2); }
    .vacio { color: var(--c-texto-2); margin: 0; }
  `,
})
export class ListaEsperaCitas {
  private readonly service = inject(AgendaService);
  private readonly auth = inject(AuthService);

  protected readonly entradas = signal<EntradaListaEspera[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly puedeEditar = computed(() => this.auth.tieneRol('AGENDAMIENTO', 'ADMISIONES'));

  constructor() {
    this.cargar();
  }

  private cargar(): void {
    this.service.listaEspera().subscribe({
      next: (lista) => {
        this.entradas.set(lista);
        this.cargando.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.error.set(mensajeError(e, 'No fue posible cargar la lista de espera.'));
        this.cargando.set(false);
      },
    });
  }

  protected clase(prioridad: string): string {
    return prioridad === 'URGENTE' ? 'chip--critico' : prioridad === 'PRIORITARIA' ? 'chip--alerta' : 'chip--info';
  }

  protected retirar(e: EntradaListaEspera): void {
    if (!confirm(`¿Retirar a ${e.paciente.nombreCompleto} de la lista de espera?`)) return;
    this.service.retirarListaEspera(e.id).subscribe({
      next: () => this.cargar(),
      error: (err: HttpErrorResponse) => this.error.set(mensajeError(err, 'No fue posible retirarlo.')),
    });
  }
}
