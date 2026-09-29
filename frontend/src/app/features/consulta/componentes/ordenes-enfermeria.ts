import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged, switchMap } from 'rxjs';

import { erroresCampos, mensajeError } from '../../../core/api/api';
import { OrdenClinica, TIPOS_ORDEN, TipoOrden } from '../../enfermeria/enfermeria.models';
import { EnfermeriaService } from '../../enfermeria/enfermeria.service';
import { Medicamento } from '../consulta.models';
import { ConsultaService } from '../consulta.service';

const VIAS = ['Oral', 'Intravenosa', 'Intramuscular', 'Subcutánea', 'Inhalada', 'Sublingual', 'Tópica', 'Rectal', 'Oftálmica'];

/** RFENF-005 / RFENF-009: el médico ordena a enfermería (medicamento con horario, dieta o cuidado) y puede suspender. */
@Component({
  selector: 'app-ordenes-enfermeria',
  imports: [DatePipe, FormsModule],
  template: `
    <div class="titulo-accion">
      <div class="tarjeta__titulo"><span class="icono">vaccines</span>Órdenes para enfermería</div>
      @if (editable() && !ordenando()) {
        <button type="button" class="btn btn--secundario btn--pequeno" (click)="abrir()"><span class="icono">add</span>Nueva orden</button>
      }
    </div>

    @if (ordenando()) {
      <div class="formulario">
        <div class="tipos" role="radiogroup" aria-label="Tipo de orden">
          @for (t of tipos; track t.valor) {
            <label class="opcion" [class.opcion--activa]="tipo === t.valor">
              <input type="radio" name="tipoOrden" [value]="t.valor" [(ngModel)]="tipo" /><span class="icono">{{ t.icono }}</span>{{ t.etiqueta }}
            </label>
          }
        </div>
        @if (tipo === 'MEDICAMENTO') {
          <div class="rejilla-form">
            <label class="ancho">Medicamento
              <input [ngModel]="textoMedicamento()" (ngModelChange)="buscar($event)" placeholder="Nombre o principio activo" autocomplete="off"
                [class.error]="campos()['medicamentoId']" /></label>
            @if (opciones().length && !medicamento()) {
              <ul class="opciones ancho">
                @for (m of opciones(); track m.id) {
                  <li><button type="button" (click)="elegir(m)">{{ m.nombre }} <small>{{ m.presentacion }} · {{ m.via }}</small></button></li>
                }
              </ul>
            }
            <label>Dosis<input [(ngModel)]="dosis" placeholder="p. ej. 1 g" [class.error]="campos()['dosis']" /></label>
            <label>Vía
              <select [(ngModel)]="via" [class.error]="campos()['via']">
                <option value="">Seleccione…</option>
                @for (v of vias; track v) { <option [value]="v">{{ v }}</option> }
              </select></label>
            <label>Cada (horas)<input type="number" min="1" max="72" [(ngModel)]="frecuencia" placeholder="vacío = dosis única" /></label>
            <label>Duración (horas)<input type="number" min="1" max="720" [(ngModel)]="duracion" [class.error]="campos()['duracionHoras']" /></label>
          </div>
        } @else {
          <div class="rejilla-form">
            <label class="ancho">{{ tipo === 'DIETA' ? 'Dieta' : 'Cuidado' }}
              <input [(ngModel)]="descripcion" [placeholder]="tipo === 'DIETA' ? 'p. ej. Dieta hiposódica, líquidos claros' : 'p. ej. Curación de herida, cambio de posición'"
                [class.error]="campos()['descripcion']" /></label>
            @if (tipo === 'CUIDADO') {
              <label>Cada (horas)<input type="number" min="1" max="72" [(ngModel)]="frecuencia" placeholder="vacío = una vez" /></label>
            }
            <label>Duración (horas)<input type="number" min="1" max="720" [(ngModel)]="duracion" /></label>
          </div>
        }
        <label class="indicaciones">Indicaciones (opcional)<input [(ngModel)]="indicaciones" placeholder="p. ej. Suspender si glucometría &lt; 70" /></label>
        @if (error()) { <p class="campo__error">{{ error() }}</p> }
        <div class="acciones">
          <button type="button" class="btn btn--secundario btn--pequeno" (click)="ordenando.set(false)">Cancelar</button>
          <button type="button" class="btn btn--primario btn--pequeno" (click)="crear()" [disabled]="ocupado()">Enviar a enfermería</button>
        </div>
      </div>
    }

    @for (o of ordenes(); track o.id) {
      <div class="orden" [class.orden--inactiva]="o.situacion !== 'ACTIVA'">
        <span>
          <strong>{{ o.descripcion }}</strong>
          <small>{{ o.tipoDescripcion }} · desde {{ o.inicio | date: 'dd/MM h:mm a' }} hasta {{ o.fin | date: 'dd/MM h:mm a' }}
            · {{ registradas(o) }} registro(s)</small>
          @if (o.situacion === 'SUSPENDIDA') { <small class="mal">Suspendida: {{ o.motivoSuspension }}</small> }
        </span>
        @if (o.altoRiesgo) { <span class="chip chip--critico">Alto riesgo</span> }
        <span class="chip" [class]="'chip ' + (o.situacion === 'ACTIVA' ? 'chip--normal' : o.situacion === 'SUSPENDIDA' ? 'chip--critico' : '')">
          {{ o.situacion === 'ACTIVA' ? 'Activa' : o.situacion === 'SUSPENDIDA' ? 'Suspendida' : 'Vencida' }}</span>
        @if (puedeSuspender() && o.situacion === 'ACTIVA') {
          @if (suspendiendo() === o.id) {
            <input class="motivo" [(ngModel)]="motivo" placeholder="Motivo" aria-label="Motivo de suspensión" (keyup.enter)="suspender(o)" />
            <button type="button" class="btn btn--peligro btn--pequeno" (click)="suspender(o)" [disabled]="!motivo.trim()">Suspender</button>
          } @else {
            <button type="button" class="enlace" (click)="suspendiendo.set(o.id); motivo = ''">Suspender</button>
          }
        }
      </div>
    } @empty {
      @if (!ordenando()) { <p class="vacio">Sin órdenes para enfermería en esta consulta.</p> }
    }
  `,
  styles: `
    .titulo-accion { display: flex; justify-content: space-between; align-items: flex-start; }
    .formulario { padding: 12px; border: 1px dashed var(--c-borde); border-radius: var(--radio-sm); margin-bottom: 12px; }
    .tipos { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 10px; }
    .opcion { display: inline-flex; align-items: center; gap: 6px; padding: 6px 12px; border: 1px solid var(--c-borde); border-radius: 999px;
      cursor: pointer; font-size: 13.5px; }
    .opcion .icono { font-size: 18px; }
    .opcion input { display: none; }
    .opcion--activa { border-color: var(--c-azul); background: var(--c-azul-suave); color: var(--c-azul); }
    .rejilla-form { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 8px; }
    .ancho { grid-column: 1 / -1; }
    label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; color: var(--c-texto-2); }
    input, select { height: 36px; padding: 0 10px; border: 1px solid var(--c-borde); border-radius: 8px; font: inherit; font-size: 13.5px;
      color: var(--c-texto); }
    .error { border-color: var(--c-critico); }
    .indicaciones { margin-top: 8px; }
    .acciones { margin-top: 10px; }
    .opciones { list-style: none; margin: 0; padding: 4px; border: 1px solid var(--c-borde); border-radius: 8px; max-height: 160px; overflow: auto; }
    .opciones button { width: 100%; text-align: left; padding: 6px 8px; border: 0; background: none; font: inherit; cursor: pointer; border-radius: 6px; }
    .opciones button:hover { background: var(--c-azul-suave); }
    .opciones small { color: var(--c-texto-3); }
    .orden { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; padding: 10px 12px; border: 1px solid var(--c-borde);
      border-radius: var(--radio-sm); margin-bottom: 8px; }
    .orden > span:first-child { flex: 1; min-width: 200px; display: flex; flex-direction: column; }
    .orden small { color: var(--c-texto-2); }
    .orden--inactiva { opacity: 0.7; }
    .mal { color: var(--c-critico) !important; }
    .motivo { width: 180px; }
    .vacio { margin: 0; color: var(--c-texto-3); font-size: 13.5px; }
  `,
})
export class OrdenesEnfermeria implements OnInit {
  private readonly service = inject(EnfermeriaService);
  private readonly consultas = inject(ConsultaService);

  readonly pacienteId = input.required<number>();
  readonly consultaId = input.required<number>();
  /** Puede ordenar (consulta en curso y es su médico). */
  readonly editable = input(false);
  /** Puede suspender: cualquier médico, incluso con la consulta ya firmada. */
  readonly puedeSuspender = input(false);

  protected readonly tipos = TIPOS_ORDEN;
  protected readonly vias = VIAS;
  protected readonly ordenes = signal<OrdenClinica[]>([]);
  protected readonly ordenando = signal(false);
  protected readonly ocupado = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly campos = signal<Record<string, string>>({});
  protected readonly suspendiendo = signal<number | null>(null);

  protected tipo: TipoOrden = 'MEDICAMENTO';
  protected descripcion = '';
  protected dosis = '';
  protected via = '';
  protected frecuencia: number | null = 8;
  protected duracion: number | null = 24;
  protected indicaciones = '';
  protected motivo = '';
  protected readonly medicamento = signal<Medicamento | null>(null);
  protected readonly textoMedicamento = signal('');
  protected readonly opciones = signal<Medicamento[]>([]);
  private readonly busqueda = new Subject<string>();

  constructor() {
    this.busqueda
      .pipe(debounceTime(250), distinctUntilChanged(), switchMap((t) => this.consultas.medicamentos(t)))
      .subscribe((l) => this.opciones.set(l));
  }

  ngOnInit(): void {
    this.cargar();
  }

  private cargar(): void {
    this.service.deConsulta(this.consultaId()).subscribe((l) => this.ordenes.set(l));
  }

  protected registradas(o: OrdenClinica): number {
    return o.horarios.filter((h) => h.ejecucion).length;
  }

  protected abrir(): void {
    this.tipo = 'MEDICAMENTO';
    this.descripcion = this.dosis = this.via = this.indicaciones = '';
    this.frecuencia = 8;
    this.duracion = 24;
    this.medicamento.set(null);
    this.textoMedicamento.set('');
    this.error.set(null);
    this.campos.set({});
    this.ordenando.set(true);
  }

  protected buscar(texto: string): void {
    this.textoMedicamento.set(texto);
    this.medicamento.set(null);
    if (texto.trim().length >= 2) this.busqueda.next(texto.trim());
    else this.opciones.set([]);
  }

  protected elegir(m: Medicamento): void {
    this.medicamento.set(m);
    this.textoMedicamento.set(m.nombre);
    this.opciones.set([]);
    if (!this.via) this.via = VIAS.find((v) => v === m.via) ?? '';
  }

  protected crear(): void {
    const med = this.tipo === 'MEDICAMENTO';
    this.ocupado.set(true);
    this.error.set(null);
    this.campos.set({});
    this.service
      .crearOrden({
        pacienteId: this.pacienteId(), consultaId: this.consultaId(), tipo: this.tipo,
        descripcion: med ? null : this.descripcion.trim() || null,
        medicamentoId: med ? this.medicamento()?.id ?? null : null,
        dosis: med ? this.dosis.trim() || null : null, via: med ? this.via || null : null,
        frecuenciaHoras: this.tipo === 'DIETA' ? null : this.frecuencia || null, inicio: null,
        duracionHoras: this.duracion ?? 0, indicaciones: this.indicaciones.trim() || null,
      })
      .subscribe({
        next: () => {
          this.ocupado.set(false);
          this.ordenando.set(false);
          this.cargar();
        },
        error: (e: HttpErrorResponse) => {
          this.ocupado.set(false);
          const campos = erroresCampos(e);
          this.campos.set(campos);
          this.error.set(Object.keys(campos).length ? Object.values(campos).join('. ') : mensajeError(e, 'No fue posible crear la orden.'));
        },
      });
  }

  protected suspender(o: OrdenClinica): void {
    if (!this.motivo.trim()) return;
    this.service.suspender(o.id, this.motivo.trim()).subscribe({
      next: (s) => {
        this.ordenes.set(this.ordenes().map((x) => (x.id === s.id ? s : x)));
        this.suspendiendo.set(null);
      },
      error: (e: HttpErrorResponse) => this.error.set(mensajeError(e, 'No fue posible suspender la orden.')),
    });
  }
}
