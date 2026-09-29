import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged, switchMap } from 'rxjs';

import { erroresCampos, mensajeError } from '../../../core/api/api';
import { Medicamento } from '../../consulta/consulta.models';
import { ConsultaService } from '../../consulta/consulta.service';
import { Horario, OrdenClinica, ResultadoEjecucion, TOLERANCIA_MIN } from '../enfermeria.models';
import { EnfermeriaService } from '../enfermeria.service';

const VIAS = ['Oral', 'Intravenosa', 'Intramuscular', 'Subcutánea', 'Inhalada', 'Sublingual', 'Tópica', 'Rectal', 'Oftálmica'];

/**
 * RFENF-003 / RFENF-004: registro de una dosis o cuidado. Para medicamentos la enfermera confirma
 * lo que tiene frente a ella (no se precarga lo ordenado): el sistema compara y bloquea si algo no coincide.
 */
@Component({
  selector: 'app-dialogo-administrar',
  imports: [DatePipe, FormsModule],
  template: `
    <div class="velo" (click)="cerrar.emit()"></div>
    <div class="dialogo tarjeta" role="dialog" aria-labelledby="titulo-admin" aria-modal="true">
      <h3 id="titulo-admin"><span class="icono">{{ esMedicamento() ? 'medication' : 'healing' }}</span>{{ orden().descripcion }}</h3>
      <p class="hora">
        Programada para las <strong>{{ horario().programadaPara | date: 'h:mm a' }}</strong>
        @if (tarde()) { <span class="chip chip--critico">Fuera de la ventana de {{ tolerancia }} min</span> }
        @if (orden().altoRiesgo) { <span class="chip chip--critico"><span class="icono">gpp_maybe</span>Alto riesgo</span> }
      </p>
      @if (orden().indicaciones) { <p class="indicaciones"><span class="icono">info</span>{{ orden().indicaciones }}</p> }

      <div class="resultados" role="radiogroup" aria-label="Resultado">
        @for (r of resultados(); track r.valor) {
          <label class="opcion" [class.opcion--activa]="actual() === r.valor">
            <input type="radio" name="resultado" [value]="r.valor" [ngModel]="actual()" (ngModelChange)="resultado.set($event)" />{{ r.etiqueta }}
          </label>
        }
      </div>

      @if (realizada()) {
        @if (esMedicamento()) {
          <fieldset>
            <legend>Cinco correctos · confirme lo que tiene frente al paciente</legend>
            <label>Documento del paciente (escanee o digite la manilla)
              <input [(ngModel)]="documento" autocomplete="off" [class.error]="campos()['verificacion']" /></label>
            <label>Medicamento que va a administrar
              <input [ngModel]="textoMedicamento()" (ngModelChange)="buscarMedicamento($event)" placeholder="Nombre o principio activo"
                autocomplete="off" />
            </label>
            @if (opciones().length && !medicamento()) {
              <ul class="opciones">
                @for (m of opciones(); track m.id) {
                  <li><button type="button" (click)="elegir(m)">{{ m.nombre }} <small>{{ m.presentacion }}</small></button></li>
                }
              </ul>
            }
            <div class="dos">
              <label>Dosis<input [(ngModel)]="dosis" placeholder="p. ej. 10 UI" autocomplete="off" /></label>
              <label>Vía
                <select [(ngModel)]="via">
                  <option value="">Seleccione…</option>
                  @for (v of vias; track v) { <option [value]="v">{{ v }}</option> }
                </select>
              </label>
            </div>
            <p class="ayuda"><span class="icono">schedule</span>Hora: la registra el sistema al guardar.</p>
          </fieldset>
          @if (orden().altoRiesgo) {
            <fieldset class="doble">
              <legend>Doble verificación independiente</legend>
              <p class="ayuda">Otro profesional de enfermería o un médico revisa el medicamento, la dosis y la vía, y confirma con su usuario.</p>
              <div class="dos">
                <label>Usuario de quien verifica<input [(ngModel)]="verificador" autocomplete="off" /></label>
                <label>Contraseña<input type="password" [(ngModel)]="passwordVerificador" autocomplete="new-password" /></label>
              </div>
            </fieldset>
          }
        }
        @if (tarde()) {
          <label class="bloque">Justificación del retraso
            <textarea rows="2" [(ngModel)]="justificacion" [class.error]="campos()['justificacionRetraso']"></textarea></label>
        }
      } @else {
        <label class="bloque">Motivo
          <textarea rows="2" [(ngModel)]="motivo" [class.error]="campos()['motivo']"
            placeholder="p. ej. Paciente en ayuno para procedimiento, náuseas, no hay acceso venoso"></textarea></label>
      }

      @if (error()) {
        <div class="alerta alerta--error" role="alert"><span class="icono">block</span>{{ error() }}</div>
      }
      <div class="acciones">
        <button type="button" class="btn btn--secundario" (click)="cerrar.emit()">Cancelar</button>
        <button type="button" class="btn btn--primario" (click)="registrar()" [disabled]="ocupado()">Registrar</button>
      </div>
    </div>
  `,
  styles: `
    .velo { position: fixed; inset: 0; z-index: 40; background: rgba(16, 42, 76, 0.35); }
    .dialogo { position: fixed; z-index: 41; top: 50%; left: 50%; transform: translate(-50%, -50%); width: min(560px, calc(100vw - 32px));
      max-height: calc(100vh - 32px); overflow: auto; }
    h3 { display: flex; align-items: center; gap: 8px; font-size: 17px; margin-bottom: 6px; }
    .hora { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin: 0 0 8px; color: var(--c-texto-2); }
    .indicaciones { display: flex; gap: 6px; margin: 0 0 10px; font-size: 13.5px; color: var(--c-texto-2); }
    .indicaciones .icono { font-size: 18px; }
    .resultados { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
    .opcion { display: inline-flex; align-items: center; gap: 6px; padding: 6px 12px; border: 1px solid var(--c-borde); border-radius: 999px;
      cursor: pointer; font-size: 13.5px; }
    .opcion--activa { border-color: var(--c-azul); background: var(--c-azul-suave); }
    .opcion input { margin: 0; }
    fieldset { border: 1px solid var(--c-borde); border-radius: var(--radio-sm); padding: 10px 12px; margin: 0 0 12px; display: flex;
      flex-direction: column; gap: 8px; }
    legend { font-weight: 600; font-size: 13.5px; padding: 0 4px; }
    .doble { border-color: #f4b4b4; background: var(--c-critico-suave); }
    label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; color: var(--c-texto-2); }
    input, select, textarea { padding: 8px 10px; border: 1px solid var(--c-borde); border-radius: 8px; font: inherit; font-size: 14px;
      color: var(--c-texto); background: #fff; }
    .error { border-color: var(--c-critico); }
    .dos { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
    .bloque { margin-bottom: 12px; }
    .ayuda { display: flex; align-items: center; gap: 6px; margin: 0; font-size: 12.5px; color: var(--c-texto-2); }
    .ayuda .icono { font-size: 16px; }
    .opciones { list-style: none; margin: -4px 0 0; padding: 4px; border: 1px solid var(--c-borde); border-radius: 8px; max-height: 160px;
      overflow: auto; }
    .opciones button { width: 100%; text-align: left; padding: 6px 8px; border: 0; background: none; font: inherit; cursor: pointer;
      border-radius: 6px; }
    .opciones button:hover { background: var(--c-azul-suave); }
    .opciones small { color: var(--c-texto-3); }
    .alerta { margin-bottom: 12px; }
    @media (max-width: 520px) { .dos { grid-template-columns: 1fr; } }
  `,
})
export class DialogoAdministrar {
  private readonly service = inject(EnfermeriaService);
  private readonly consultas = inject(ConsultaService);

  readonly orden = input.required<OrdenClinica>();
  readonly horario = input.required<Horario>();
  readonly registrado = output<OrdenClinica>();
  readonly cerrar = output<void>();

  protected readonly vias = VIAS;
  protected readonly tolerancia = TOLERANCIA_MIN;
  protected readonly esMedicamento = computed(() => this.orden().tipo === 'MEDICAMENTO');
  protected readonly resultados = computed<{ valor: ResultadoEjecucion; etiqueta: string }[]>(() => [
    this.esMedicamento() ? { valor: 'ADMINISTRADA', etiqueta: 'Administrada' } : { valor: 'REALIZADO', etiqueta: 'Realizado' },
    { valor: 'OMITIDA', etiqueta: 'Omitida' },
    { valor: 'RECHAZADA_POR_PACIENTE', etiqueta: 'Rechazada por el paciente' },
  ]);
  protected readonly resultado = signal<ResultadoEjecucion | null>(null);
  /** Por defecto, administrada / realizado. */
  protected readonly actual = computed(() => this.resultado() ?? this.resultados()[0].valor);
  protected readonly realizada = computed(() => this.actual() === 'ADMINISTRADA' || this.actual() === 'REALIZADO');
  protected readonly tarde = computed(
    () => Date.now() > new Date(this.horario().programadaPara).getTime() + TOLERANCIA_MIN * 60_000,
  );

  protected documento = '';
  protected dosis = '';
  protected via = '';
  protected verificador = '';
  protected passwordVerificador = '';
  protected justificacion = '';
  protected motivo = '';
  protected readonly medicamento = signal<Medicamento | null>(null);
  protected readonly textoMedicamento = signal('');
  protected readonly opciones = signal<Medicamento[]>([]);
  protected readonly ocupado = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly campos = signal<Record<string, string>>({});

  private readonly busqueda = new Subject<string>();

  constructor() {
    this.busqueda
      .pipe(debounceTime(250), distinctUntilChanged(), switchMap((t) => this.consultas.medicamentos(t)))
      .subscribe((l) => this.opciones.set(l));
  }

  protected buscarMedicamento(texto: string): void {
    this.textoMedicamento.set(texto);
    this.medicamento.set(null);
    if (texto.trim().length >= 2) this.busqueda.next(texto.trim());
    else this.opciones.set([]);
  }

  protected elegir(m: Medicamento): void {
    this.medicamento.set(m);
    this.textoMedicamento.set(m.nombre);
    this.opciones.set([]);
  }

  protected registrar(): void {
    const resultado = this.actual();
    const realizada = this.realizada();
    this.error.set(null);
    this.campos.set({});
    if (realizada && this.esMedicamento()) {
      if (!this.documento.trim() || !this.medicamento() || !this.dosis.trim() || !this.via) {
        this.error.set('Confirme documento del paciente, medicamento, dosis y vía antes de administrar.');
        return;
      }
      if (this.orden().altoRiesgo && (!this.verificador.trim() || !this.passwordVerificador)) {
        this.error.set('Medicamento de alto riesgo: falta la verificación de otro profesional.');
        return;
      }
    }
    this.ocupado.set(true);
    this.service
      .ejecutar(this.orden().id, {
        programadaPara: this.horario().programadaPara,
        resultado,
        verificacion: realizada && this.esMedicamento()
          ? { documentoPaciente: this.documento.trim(), medicamentoId: this.medicamento()!.id, dosis: this.dosis.trim(), via: this.via }
          : null,
        motivo: realizada ? null : this.motivo.trim() || null,
        justificacionRetraso: realizada && this.tarde() ? this.justificacion.trim() || null : null,
        dobleValidacion: realizada && this.esMedicamento() && this.orden().altoRiesgo
          ? { usuario: this.verificador.trim(), password: this.passwordVerificador }
          : null,
      })
      .subscribe({
        next: (o) => {
          this.ocupado.set(false);
          this.registrado.emit(o);
        },
        error: (e: HttpErrorResponse) => {
          this.ocupado.set(false);
          this.passwordVerificador = '';
          const campos = erroresCampos(e);
          this.campos.set(campos);
          this.error.set(Object.keys(campos).length ? Object.values(campos).join('. ') : mensajeError(e, 'No fue posible registrar.'));
        },
      });
  }
}
