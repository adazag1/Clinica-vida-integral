import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { mensajeError } from '../../../core/api/api';
import { AuthService } from '../../../core/auth/auth.service';
import { SolicitudArchivo } from '../documentos.models';
import { DocumentosService } from '../documentos.service';

/** RFGAC-008: el archivo atiende las solicitudes (urgentes primero); quien solicita ve el estado de las suyas. */
@Component({
  selector: 'app-solicitudes-archivo',
  imports: [DatePipe, FormsModule, RouterLink],
  template: `
    <a class="volver" routerLink="/documentos"><span class="icono">arrow_back</span> Gestión documental</a>
    <div class="cabecera-pagina"><h2>{{ esArchivo() ? 'Solicitudes al archivo' : 'Mis solicitudes al archivo' }}</h2></div>
    @if (error()) { <div class="alerta alerta--error" role="alert"><span class="icono">error</span>{{ error() }}</div> }

    <section class="tarjeta">
      @for (s of solicitudes(); track s.id) {
        <div class="solicitud" [class.urgente]="s.urgente && s.estado === 'PENDIENTE'">
          <div class="datos">
            <strong>{{ s.documentoTitulo ?? 'Expediente completo' }}</strong>
            <span>{{ s.paciente.nombreCompleto }} · {{ s.paciente.tipoDocumento }} {{ s.paciente.numeroDocumento }}</span>
            <small>{{ s.solicitanteNombre }} · {{ s.solicitadaEn | date: 'dd/MM h:mm a' }} · {{ s.motivo }}</small>
            @if (s.respuesta) { <small>Respuesta: {{ s.respuesta }}</small> }
          </div>
          <div class="estado">
            @if (s.urgente) { <span class="chip chip--critico">Urgente</span> }
            <span class="chip" [class]="'chip ' + (s.estado === 'PENDIENTE' ? 'chip--alerta' : s.estado === 'RECHAZADA' ? 'chip--critico' : 'chip--normal')">{{ s.estadoDescripcion }}</span>
            @if (s.documentoId) { <a class="enlace" [routerLink]="['/documentos', s.documentoId]">Ver documento</a> }
          </div>
          @if (esArchivo()) {
            <div class="atender">
              @if (s.estado === 'PENDIENTE') {
                <input class="control" [(ngModel)]="respuestas[s.id]" placeholder="Respuesta (dónde se entrega o por qué se rechaza)" />
                <button type="button" class="btn btn--primario btn--pequeno" (click)="atender(s, 'entregar')">Entregar</button>
                <button type="button" class="btn btn--secundario btn--pequeno" (click)="atender(s, 'rechazar')">Rechazar</button>
              } @else if (s.estado === 'ENTREGADA') {
                <button type="button" class="btn btn--secundario btn--pequeno" (click)="atender(s, 'devolver')">Registrar devolución</button>
              }
            </div>
          }
        </div>
      } @empty {
        <p class="vacio">{{ cargando() ? 'Cargando…' : 'No hay solicitudes.' }}</p>
      }
    </section>
  `,
  styles: `
    .alerta { margin-bottom: 12px; }
    .solicitud { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; padding: 12px 0; border-bottom: 1px solid var(--c-borde); }
    .solicitud:last-child { border-bottom: 0; }
    .urgente { box-shadow: inset 4px 0 0 var(--c-critico); padding-left: 12px; }
    .datos { flex: 1; min-width: 240px; display: flex; flex-direction: column; gap: 2px; }
    .datos small { color: var(--c-texto-2); }
    .estado { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; }
    .atender { display: flex; gap: 6px; width: 100%; }
    .atender .control { flex: 1; }
  `,
})
export class SolicitudesArchivo {
  private readonly service = inject(DocumentosService);
  private readonly auth = inject(AuthService);

  protected readonly esArchivo = computed(() => this.auth.tieneRol('ARCHIVO'));
  protected readonly solicitudes = signal<SolicitudArchivo[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected respuestas: Record<number, string> = {};

  constructor() {
    this.cargar();
  }

  private cargar(): void {
    (this.esArchivo() ? this.service.solicitudesAbiertas() : this.service.misSolicitudes()).subscribe({
      next: (l) => {
        this.solicitudes.set(l);
        this.cargando.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.cargando.set(false);
        this.error.set(mensajeError(e, 'No fue posible cargar las solicitudes.'));
      },
    });
  }

  protected atender(s: SolicitudArchivo, accion: 'entregar' | 'rechazar' | 'devolver'): void {
    this.service.atender(s.id, accion, this.respuestas[s.id]?.trim() || null).subscribe({
      next: () => {
        this.error.set(null);
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.error.set(mensajeError(e, 'No fue posible atender la solicitud.')),
    });
  }
}
