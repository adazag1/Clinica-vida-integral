import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { mensajeError } from '../../../core/api/api';
import { AuthService } from '../../../core/auth/auth.service';
import { AlertaIa, EstadoIa, RespuestaAsistente, TipoAsistente, claseSeveridad } from '../ia.models';
import { IaService } from '../ia.service';

/**
 * US 4.2: alertas del motor de reglas en la consulta y la historia clínica, con la calificación del
 * médico (útil / no útil) y el asistente de lenguaje (apagado hasta que la clínica lo apruebe).
 */
@Component({
  selector: 'app-alertas-ia',
  imports: [DatePipe, DecimalPipe, FormsModule],
  template: `
    <div class="titulo-accion">
      <div class="tarjeta__titulo"><span class="icono">neurology</span>Apoyo clínico (IA)</div>
      <button type="button" class="icono-btn" (click)="cargar()" [disabled]="cargando()" title="Volver a evaluar" aria-label="Volver a evaluar">
        <span class="icono" [class.girando]="cargando()">refresh</span>
      </button>
    </div>

    @if (error()) { <p class="campo__error">{{ error() }}</p> }

    @for (a of alertas(); track a.id) {
      <article class="alerta-ia" [class]="'alerta-ia alerta-ia--' + a.severidad.toLowerCase()" [class.cerrada]="!a.vigente">
        <div class="alerta-ia__cabecera">
          <span class="chip" [class]="'chip ' + clase(a.severidad)">{{ a.severidadDescripcion }}</span>
          <strong>{{ a.titulo }}</strong>
          @if (!a.vigente) { <small class="cerrada-txt">Resuelta {{ a.cerradaEn | date: 'dd/MM h:mm a' }}</small> }
        </div>
        <p class="detalle">{{ a.detalle }}</p>
        @if (a.escala) {
          <p class="escala"><span class="icono">speed</span>{{ a.escala }}@if (a.puntaje !== null) {: <strong>{{ a.puntaje | number: '1.0-2' }}</strong>}</p>
        }
        <ul class="evidencia">
          @for (e of a.evidencia; track $index) { <li>{{ e }}</li> }
        </ul>
        @if (a.recomendacion) { <p class="recomendacion"><span class="icono">lightbulb</span>{{ a.recomendacion }}</p> }
        <p class="fuente">Regla «{{ a.reglaNombre }}»@if (a.referencia) { · {{ a.referencia }} } · {{ a.generadaEn | date: 'dd/MM h:mm a' }}</p>

        @if (a.retroalimentacion) {
          <p class="retro" [class.retro--no]="a.retroalimentacion === 'NO_UTIL'">
            <span class="icono">{{ a.retroalimentacion === 'UTIL' ? 'thumb_up' : 'thumb_down' }}</span>
            {{ a.retroalimentacion === 'UTIL' ? 'Útil' : 'No útil' }} según {{ a.retroPor }}{{ a.comentario ? ': ' + a.comentario : '' }}
          </p>
        } @else if (puedeCalificar()) {
          @if (calificando() === a.id) {
            <div class="retro-form">
              <input class="control" [(ngModel)]="comentario" placeholder="¿Por qué no fue útil? (falso positivo, ya estaba manejado…)"
                aria-label="Motivo" maxlength="500" />
              <button type="button" class="btn btn--secundario btn--pequeno" (click)="calificando.set(null)">Cancelar</button>
              <button type="button" class="btn btn--primario btn--pequeno" (click)="calificar(a, 'NO_UTIL')" [disabled]="!comentario.trim()">Enviar</button>
            </div>
          } @else {
            <div class="retro-botones">
              <span>¿Le sirvió?</span>
              <button type="button" class="enlace" (click)="calificar(a, 'UTIL')"><span class="icono">thumb_up</span>Útil</button>
              <button type="button" class="enlace" (click)="calificando.set(a.id); comentario = ''"><span class="icono">thumb_down</span>No útil</button>
            </div>
          }
        }
      </article>
    } @empty {
      @if (!cargando()) {
        <p class="vacio">Sin alertas activas. Las reglas se revisan con cada toma de signos, resultado validado o nota firmada.</p>
      }
    }

    <button type="button" class="enlace ver-historial" (click)="alternarHistorial()">
      {{ historial() ? 'Ver solo las activas' : 'Ver también las resueltas' }}
    </button>

    @if (puedeUsarAsistente()) {
      <div class="asistente">
        <div class="asistente__titulo"><span class="icono">smart_toy</span>Asistente de lenguaje</div>
        <div class="asistente__botones">
          <button type="button" class="btn btn--secundario btn--pequeno" [disabled]="!habilitado() || consultando()"
            (click)="consultar('RESUMEN')">Resumir historia</button>
          <button type="button" class="btn btn--secundario btn--pequeno" [disabled]="!habilitado() || consultando()"
            (click)="consultar('DIFERENCIAL')">Diagnóstico diferencial</button>
        </div>
        @if (!habilitado()) {
          <p class="asistente__aviso"><span class="icono">power_off</span>{{ estado()?.motivo ?? 'El asistente está apagado.' }}</p>
        }
        <button type="button" class="enlace" (click)="verVistaPrevia()">Ver qué datos se enviarían (anonimizados)</button>
        @if (consultando()) { <p class="vacio">Consultando al asistente…</p> }
        @if (respuesta(); as r) {
          <div class="respuesta">
            <p class="respuesta__aviso"><span class="icono">info</span>{{ r.advertencia }}</p>
            <pre>{{ r.texto }}</pre>
            <small>{{ r.modelo }} · {{ (r.tokensEntrada ?? 0) + (r.tokensSalida ?? 0) }} tokens
              @if (r.costoUsd !== null) { · US$ {{ r.costoUsd | number: '1.4-6' }} }</small>
          </div>
        }
      </div>
    }

    @if (vistaPrevia() !== null) {
      <div class="velo" (click)="vistaPrevia.set(null)"></div>
      <div class="dialogo tarjeta dialogo--ancho" role="dialog" aria-modal="true" aria-labelledby="titulo-vista">
        <h3 id="titulo-vista"><span class="icono">shield_person</span>Datos que recibiría el modelo</h3>
        <p>Sin nombre, documento, teléfono, correo, dirección ni fechas exactas; la edad va en grupos de 5 años.
          Revise que no quede ningún dato que identifique al paciente.</p>
        <pre class="vista">{{ vistaPrevia() }}</pre>
        <div class="acciones"><button type="button" class="btn btn--primario btn--pequeno" (click)="vistaPrevia.set(null)">Cerrar</button></div>
      </div>
    }
  `,
  styles: `
    .icono-btn { border: 0; background: none; cursor: pointer; color: var(--c-texto-2); padding: 2px; border-radius: 6px; }
    .icono-btn:hover { color: var(--c-azul); background: var(--c-azul-suave); }
    .girando { animation: girar 0.9s linear infinite; }
    @keyframes girar { to { transform: rotate(360deg); } }
    .alerta-ia { border: 1px solid var(--c-borde); border-left: 4px solid var(--c-azul); border-radius: var(--radio-sm);
      padding: 10px 12px; margin-bottom: 10px; font-size: 13.5px; }
    .alerta-ia--critica { border-left-color: var(--c-critico); background: var(--c-critico-suave); }
    .alerta-ia--alerta { border-left-color: var(--c-alerta); background: var(--c-alerta-suave); }
    .alerta-ia.cerrada { opacity: 0.7; background: var(--c-superficie); border-left-color: var(--c-texto-3); }
    .alerta-ia__cabecera { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; }
    .cerrada-txt { color: var(--c-texto-2); }
    .detalle { margin: 6px 0 4px; }
    .escala, .recomendacion, .retro { display: flex; align-items: flex-start; gap: 4px; margin: 4px 0; }
    .escala .icono, .recomendacion .icono, .retro .icono { font-size: 17px; }
    .evidencia { margin: 4px 0; padding-left: 18px; color: var(--c-texto-2); }
    .fuente { margin: 6px 0 0; font-size: 12px; color: var(--c-texto-3); }
    .retro { color: var(--c-normal); font-size: 12.5px; }
    .retro--no { color: var(--c-texto-2); }
    .retro-botones { display: flex; align-items: center; gap: 12px; margin-top: 6px; font-size: 12.5px; color: var(--c-texto-2); }
    .retro-botones .enlace { display: inline-flex; align-items: center; gap: 3px; }
    .retro-botones .icono { font-size: 16px; }
    .retro-form { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; }
    .retro-form .control { flex: 1; min-width: 180px; height: 32px; }
    .ver-historial { font-size: 12.5px; }
    .asistente { margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--c-borde); font-size: 13.5px; }
    .asistente__titulo { display: flex; align-items: center; gap: 6px; font-weight: 600; margin-bottom: 8px; }
    .asistente__botones { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 6px; }
    .asistente__aviso { display: flex; gap: 6px; margin: 4px 0 6px; font-size: 12.5px; color: var(--c-texto-2); }
    .asistente__aviso .icono { font-size: 17px; }
    .respuesta { margin-top: 8px; padding: 10px; border: 1px solid var(--c-borde); border-radius: var(--radio-sm); }
    .respuesta__aviso { display: flex; gap: 6px; margin: 0 0 6px; font-size: 12.5px; color: var(--c-alerta); }
    pre { white-space: pre-wrap; font: inherit; margin: 0 0 6px; }
    .respuesta small { color: var(--c-texto-3); }
    .dialogo--ancho { width: min(640px, calc(100vw - 32px)); }
    .vista { max-height: 50vh; overflow: auto; padding: 10px; background: var(--c-fondo); border-radius: var(--radio-sm);
      font-family: ui-monospace, monospace; font-size: 12.5px; }
    .acciones { display: flex; justify-content: flex-end; margin-top: 10px; }
  `,
})
export class AlertasIaPanel {
  private readonly service = inject(IaService);
  private readonly auth = inject(AuthService);

  readonly pacienteId = input.required<number>();
  /** Cambie este número para forzar una nueva evaluación (p. ej. después de registrar signos). */
  readonly version = input(0);

  protected readonly clase = claseSeveridad;
  protected readonly alertas = signal<AlertaIa[]>([]);
  protected readonly cargando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly historial = signal(false);
  protected readonly calificando = signal<number | null>(null);
  protected comentario = '';

  protected readonly estado = signal<EstadoIa | null>(null);
  protected readonly habilitado = computed(() => this.estado()?.asistenteHabilitado === true && this.estado()?.asistenteConfigurado === true);
  protected readonly consultando = signal(false);
  protected readonly respuesta = signal<RespuestaAsistente | null>(null);
  protected readonly vistaPrevia = signal<string | null>(null);

  protected readonly puedeCalificar = computed(() => this.auth.tieneRol('MEDICO'));
  protected readonly puedeUsarAsistente = computed(() => this.auth.tieneRol('MEDICO'));

  constructor() {
    effect(() => {
      this.pacienteId();
      this.version();
      untracked(() => this.cargar());
    });
    this.service.estado().subscribe({ next: (e) => this.estado.set(e), error: () => undefined });
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.service.alertas(this.pacienteId(), this.historial()).subscribe({
      next: (a) => {
        this.alertas.set(a);
        this.error.set(null);
        this.cargando.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.cargando.set(false);
        this.error.set(mensajeError(e, 'No fue posible evaluar las alertas.'));
      },
    });
  }

  protected alternarHistorial(): void {
    this.historial.set(!this.historial());
    this.cargar();
  }

  protected calificar(a: AlertaIa, valor: 'UTIL' | 'NO_UTIL'): void {
    this.service.retroalimentar(a.id, valor, valor === 'NO_UTIL' ? this.comentario.trim() : null).subscribe({
      next: (actualizada) => {
        this.calificando.set(null);
        this.alertas.update((lista) => lista.map((x) => (x.id === actualizada.id ? actualizada : x)));
      },
      error: (e: HttpErrorResponse) => this.error.set(mensajeError(e, 'No fue posible guardar su calificación.')),
    });
  }

  protected consultar(tipo: TipoAsistente): void {
    this.consultando.set(true);
    this.respuesta.set(null);
    this.service.asistente(this.pacienteId(), tipo).subscribe({
      next: (r) => {
        this.consultando.set(false);
        this.respuesta.set(r);
      },
      error: (e: HttpErrorResponse) => {
        this.consultando.set(false);
        this.error.set(mensajeError(e, 'El asistente no respondió.'));
      },
    });
  }

  protected verVistaPrevia(): void {
    this.service.vistaPrevia(this.pacienteId()).subscribe({
      next: (v) => this.vistaPrevia.set(v.texto),
      error: (e: HttpErrorResponse) => this.error.set(mensajeError(e, 'No fue posible preparar la vista previa.')),
    });
  }
}
