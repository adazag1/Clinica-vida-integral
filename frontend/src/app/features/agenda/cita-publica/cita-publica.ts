import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { Observable } from 'rxjs';

import { CitaPublica as Datos } from '../agenda.models';
import { AgendaService } from '../agenda.service';

/** RFAGE-010: página que abre el paciente desde el recordatorio. No requiere cuenta. */
@Component({
  selector: 'app-cita-publica',
  imports: [DatePipe],
  template: `
    <main class="pagina">
      <section class="tarjeta">
        <div class="marca"><span class="cruz"><span class="icono">add</span></span>Clínica Vida Integral</div>

        @if (cita(); as c) {
          <h1>Hola, {{ c.paciente }}</h1>
          <p class="sub">Esta es su cita:</p>
          <dl>
            <div><dt>Fecha</dt><dd class="capital">{{ c.inicio | date: "EEEE d 'de' MMMM 'de' yyyy" }}</dd></div>
            <div><dt>Hora</dt><dd>{{ c.inicio | date: 'h:mm a' }}</dd></div>
            <div><dt>Profesional</dt><dd>{{ c.profesional }} · {{ c.especialidad }}</dd></div>
            <div><dt>Tipo</dt><dd>{{ c.tipo }}</dd></div>
            <div><dt>Lugar</dt><dd>{{ c.sede }}{{ c.direccion ? ', ' + c.direccion : '' }}{{ c.consultorio ? ' · ' + c.consultorio : '' }}</dd></div>
          </dl>
          @if (c.preparacion) {
            <div class="alerta alerta--aviso"><span class="icono">info</span><span><strong>Preparación:</strong> {{ c.preparacion }}</span></div>
          }

          <p class="estado">Estado: <strong>{{ c.estadoDescripcion }}</strong></p>

          @if (mensaje()) {
            <div class="alerta alerta--exito" role="status"><span class="icono">check_circle</span>{{ mensaje() }}</div>
          }
          @if (error()) {
            <div class="alerta alerta--error" role="alert"><span class="icono">error</span>{{ error() }}</div>
          }

          @if (c.puedeResponder) {
            @if (confirmandoCancelacion()) {
              <p>¿Seguro que desea cancelar? El horario quedará disponible para otro paciente.</p>
              <div class="botones">
                <button type="button" class="btn btn--secundario" (click)="confirmandoCancelacion.set(false)">No, volver</button>
                <button type="button" class="btn btn--peligro" (click)="cancelar()" [disabled]="ocupado()">Sí, cancelar mi cita</button>
              </div>
            } @else {
              <div class="botones">
                @if (c.estado === 'PROGRAMADA') {
                  <button type="button" class="btn btn--primario" (click)="confirmar()" [disabled]="ocupado()">
                    <span class="icono">task_alt</span>Confirmar asistencia</button>
                }
                <button type="button" class="btn btn--secundario" (click)="confirmandoCancelacion.set(true)">
                  <span class="icono">event_busy</span>No puedo asistir</button>
              </div>
            }
          } @else {
            <p class="nota">Si necesita cambiar esta cita, comuníquese con la clínica.</p>
          }
        } @else if (error()) {
          <h1>Enlace no válido</h1>
          <p class="sub">{{ error() }}</p>
        } @else {
          <p class="sub">Cargando…</p>
        }
      </section>
      <p class="legal">Sus datos están protegidos conforme a la Ley 1581 de 2012.</p>
    </main>
  `,
  styles: `
    .pagina { min-height: 100vh; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 24px 16px; }
    .tarjeta { width: 100%; max-width: 520px; padding: 28px; }
    .marca { display: flex; align-items: center; gap: 10px; font-weight: 700; color: var(--c-azul); margin-bottom: 20px; }
    .cruz { display: grid; place-items: center; width: 32px; height: 32px; border-radius: 10px; background: var(--c-primario); }
    .cruz .icono { color: #fff; }
    h1 { font-size: 22px; }
    .sub { color: var(--c-texto-2); margin: 4px 0 16px; }
    dl { display: flex; flex-direction: column; gap: 10px; margin: 0 0 16px; }
    dt { color: var(--c-texto-2); font-size: 13px; }
    dd { margin: 2px 0 0; font-weight: 600; }
    .capital::first-letter { text-transform: uppercase; }
    .estado { margin: 0 0 16px; }
    .botones { display: flex; flex-wrap: wrap; gap: 10px; }
    .botones .btn { flex: 1; }
    .nota, .legal { color: var(--c-texto-2); font-size: 13px; }
    .legal { margin-top: 16px; }
  `,
})
export class CitaPublica implements OnInit {
  private readonly service = inject(AgendaService);

  readonly token = input.required<string>();

  protected readonly cita = signal<Datos | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly mensaje = signal<string | null>(null);
  protected readonly ocupado = signal(false);
  protected readonly confirmandoCancelacion = signal(false);

  ngOnInit(): void {
    this.service.publica(this.token()).subscribe({
      next: (c) => this.cita.set(c),
      error: (e: HttpErrorResponse) =>
        this.error.set(e.status === 404 ? 'No encontramos la cita de este enlace.' : 'No fue posible cargar la cita. Intente más tarde.'),
    });
  }

  protected confirmar(): void {
    this.responder(this.service.confirmarPublica(this.token()), '¡Gracias! Su asistencia quedó confirmada.');
  }

  protected cancelar(): void {
    this.responder(this.service.cancelarPublica(this.token()), 'Su cita fue cancelada. Gracias por avisarnos.');
  }

  private responder(peticion: Observable<Datos>, mensaje: string): void {
    this.ocupado.set(true);
    this.error.set(null);
    peticion.subscribe({
      next: (c) => {
        this.cita.set(c);
        this.mensaje.set(mensaje);
        this.ocupado.set(false);
        this.confirmandoCancelacion.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.error.set((e.error as { mensaje?: string } | null)?.mensaje ?? 'No fue posible registrar su respuesta.');
        this.ocupado.set(false);
      },
    });
  }
}
