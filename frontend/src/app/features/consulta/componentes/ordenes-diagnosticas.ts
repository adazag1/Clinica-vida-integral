import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { erroresCampos, mensajeError } from '../../../core/api/api';
import {
  EstudioResumen,
  PRIORIDADES_ESTUDIO,
  PrioridadEstudio,
  TipoEstudio,
  claseEstadoEstudio,
} from '../../imagenes/imagenes.models';
import { ImagenesService } from '../../imagenes/imagenes.service';
import { CasoResumen, PRIORIDADES_CASO, PrioridadCaso, TIPOS_CASO, TipoCaso, claseEstadoCaso } from '../../patologia/patologia.models';
import { PatologiaService } from '../../patologia/patologia.service';

/** El médico ordena imágenes diagnósticas y estudios de patología desde la consulta, y ve su estado. */
@Component({
  selector: 'app-ordenes-diagnosticas',
  imports: [DatePipe, FormsModule, RouterLink],
  template: `
    <div class="titulo-accion">
      <div class="tarjeta__titulo"><span class="icono">radiology</span>Imágenes diagnósticas y patología</div>
      @if (editable() && !formulario()) {
        <div class="botones">
          <button type="button" class="btn btn--secundario btn--pequeno" (click)="abrir('imagen')"><span class="icono">add</span>Estudio de imágenes</button>
          <button type="button" class="btn btn--secundario btn--pequeno" (click)="abrir('patologia')"><span class="icono">add</span>Patología</button>
        </div>
      }
    </div>

    @if (formulario() === 'imagen') {
      <div class="formulario">
        <div class="form-rejilla">
          <label class="ancho">Estudio
            <select class="control" [(ngModel)]="imagen.tipoEstudioId">
              <option [ngValue]="null">Seleccione…</option>
              @for (t of tipos(); track t.id) { <option [ngValue]="t.id">{{ t.modalidadDescripcion }} · {{ t.nombre }}</option> }
            </select></label>
          <label>Prioridad
            <select class="control" [(ngModel)]="imagen.prioridad">
              @for (p of prioridadesImagen; track p.valor) { <option [value]="p.valor">{{ p.etiqueta }}</option> }
            </select></label>
          <label>Diagnóstico presuntivo<input class="control" [(ngModel)]="imagen.diagnosticoPresuntivo" placeholder="Si lo deja vacío se usa el principal de la consulta" /></label>
          <label class="ancho">Motivo del estudio<input class="control" [(ngModel)]="imagen.motivo" placeholder="Pregunta clínica para el radiólogo" /></label>
        </div>
        @if (error()) { <p class="campo__error">{{ error() }}</p> }
        <div class="acciones">
          <button type="button" class="btn btn--secundario btn--pequeno" (click)="formulario.set(null)">Cancelar</button>
          <button type="button" class="btn btn--primario btn--pequeno" (click)="ordenarImagen()" [disabled]="ocupado() || !imagen.tipoEstudioId || !imagen.motivo.trim()">
            Enviar a radiología</button>
        </div>
      </div>
    }

    @if (formulario() === 'patologia') {
      <div class="formulario">
        <div class="form-rejilla">
          <label>Tipo de estudio
            <select class="control" [(ngModel)]="caso.tipo">
              @for (t of tiposCaso; track t.valor) { <option [value]="t.valor">{{ t.etiqueta }}</option> }
            </select></label>
          <label>Prioridad
            <select class="control" [(ngModel)]="caso.prioridad">
              @for (p of prioridadesCaso; track p.valor) { <option [value]="p.valor">{{ p.etiqueta }}</option> }
            </select></label>
          <label>Número de muestras<input class="control" type="number" min="1" max="20" [(ngModel)]="caso.numeroMuestras" /></label>
          <label class="ancho">Sitio anatómico<input class="control" [(ngModel)]="caso.sitioAnatomico" placeholder="p. ej. Mama derecha, cuadrante superior externo" /></label>
          <label class="ancho">Datos clínicos<textarea class="control" rows="2" [(ngModel)]="caso.datosClinicos"></textarea></label>
        </div>
        @if (error()) { <p class="campo__error">{{ error() }}</p> }
        <div class="acciones">
          <button type="button" class="btn btn--secundario btn--pequeno" (click)="formulario.set(null)">Cancelar</button>
          <button type="button" class="btn btn--primario btn--pequeno" (click)="solicitarPatologia()" [disabled]="ocupado() || !caso.sitioAnatomico.trim()">
            Enviar a patología</button>
        </div>
      </div>
    }

    @for (e of estudios(); track e.id) {
      <a class="orden" [routerLink]="['/imagenes/estudios', e.id]" [class.orden--critica]="e.critico">
        <span class="icono">radiology</span>
        <span><strong>{{ e.estudio }}</strong>
          <small>{{ e.numero }} · {{ e.ordenadoEn | date: 'dd/MM h:mm a' }} · {{ e.prioridadDescripcion }}</small>
          @if (e.impresion) { <small class="resultado">{{ e.impresion }}</small> }</span>
        <span class="chip" [class]="'chip ' + claseEstudio(e.estado)">{{ e.estadoDescripcion }}</span>
        @if (e.critico) { <span class="chip chip--critico">Crítico</span> }
      </a>
    }
    @for (c of casos(); track c.id) {
      <a class="orden" [routerLink]="['/patologia/casos', c.id]">
        <span class="icono">biotech</span>
        <span><strong>{{ c.tipoDescripcion }} · {{ c.sitioAnatomico }}</strong>
          <small>{{ c.numero }} · {{ c.solicitadoEn | date: 'dd/MM h:mm a' }} · {{ c.prioridadDescripcion }}</small>
          @if (c.diagnostico) { <small class="resultado">{{ c.diagnostico }}</small> }</span>
        <span class="chip" [class]="'chip ' + claseCaso(c.estado)">{{ c.estadoDescripcion }}</span>
      </a>
    }
    @if (!estudios().length && !casos().length && !formulario()) {
      <p class="vacio">Sin estudios de imágenes ni de patología.</p>
    }
  `,
  styles: `
    .botones { display: flex; gap: 6px; flex-wrap: wrap; }
    .formulario { padding: 12px; border: 1px dashed var(--c-borde); border-radius: var(--radio-sm); margin-bottom: 12px; }
    .acciones { margin-top: 10px; }
    .orden { display: flex; align-items: center; gap: 10px; padding: 10px 12px; border: 1px solid var(--c-borde); border-radius: var(--radio-sm);
      margin-bottom: 8px; color: inherit; }
    .orden:hover { border-color: var(--c-azul); }
    .orden > .icono { color: var(--c-azul); }
    .orden > span:nth-child(2) { flex: 1; display: flex; flex-direction: column; min-width: 0; }
    .orden small { color: var(--c-texto-2); }
    .resultado { color: var(--c-texto) !important; }
    .orden--critica { border-color: var(--c-critico); background: var(--c-critico-suave); }
  `,
})
export class OrdenesDiagnosticas implements OnInit {
  private readonly imagenes = inject(ImagenesService);
  private readonly patologia = inject(PatologiaService);

  readonly pacienteId = input.required<number>();
  readonly consultaId = input.required<number>();
  readonly editable = input(false);

  protected readonly prioridadesImagen = PRIORIDADES_ESTUDIO;
  protected readonly prioridadesCaso = PRIORIDADES_CASO;
  protected readonly tiposCaso = TIPOS_CASO;
  protected readonly claseEstudio = claseEstadoEstudio;
  protected readonly claseCaso = claseEstadoCaso;

  protected readonly tipos = signal<TipoEstudio[]>([]);
  protected readonly estudios = signal<EstudioResumen[]>([]);
  protected readonly casos = signal<CasoResumen[]>([]);
  protected readonly formulario = signal<'imagen' | 'patologia' | null>(null);
  protected readonly ocupado = signal(false);
  protected readonly error = signal<string | null>(null);

  protected imagen = { tipoEstudioId: null as number | null, prioridad: 'AMBULATORIO' as PrioridadEstudio, motivo: '', diagnosticoPresuntivo: '' };
  protected caso = { tipo: 'BIOPSIA' as TipoCaso, prioridad: 'RUTINA' as PrioridadCaso, sitioAnatomico: '', datosClinicos: '', numeroMuestras: 1 };

  ngOnInit(): void {
    this.cargar();
  }

  private cargar(): void {
    this.imagenes.deConsulta(this.consultaId()).subscribe({ next: (l) => this.estudios.set(l), error: () => undefined });
    this.patologia.deConsulta(this.consultaId()).subscribe({ next: (l) => this.casos.set(l), error: () => undefined });
  }

  protected abrir(f: 'imagen' | 'patologia'): void {
    this.error.set(null);
    if (f === 'imagen') {
      this.imagenes.tipos().subscribe((t) => this.tipos.set(t));
      this.imagen = { tipoEstudioId: null, prioridad: 'AMBULATORIO', motivo: '', diagnosticoPresuntivo: '' };
    } else {
      this.caso = { tipo: 'BIOPSIA', prioridad: 'RUTINA', sitioAnatomico: '', datosClinicos: '', numeroMuestras: 1 };
    }
    this.formulario.set(f);
  }

  protected ordenarImagen(): void {
    const i = this.imagen;
    this.enviar(this.imagenes.ordenar({ pacienteId: this.pacienteId(), consultaId: this.consultaId(), tipoEstudioId: i.tipoEstudioId!,
      prioridad: i.prioridad, motivo: i.motivo.trim(), diagnosticoPresuntivo: i.diagnosticoPresuntivo.trim() || null }));
  }

  protected solicitarPatologia(): void {
    const c = this.caso;
    this.enviar(this.patologia.solicitar({ pacienteId: this.pacienteId(), consultaId: this.consultaId(), tipo: c.tipo,
      prioridad: c.prioridad, sitioAnatomico: c.sitioAnatomico.trim(), datosClinicos: c.datosClinicos.trim() || null,
      numeroMuestras: Number(c.numeroMuestras) }));
  }

  private enviar(obs: Observable<unknown>): void {
    this.ocupado.set(true);
    obs.subscribe({
      next: () => {
        this.ocupado.set(false);
        this.formulario.set(null);
        this.cargar();
      },
      error: (e: HttpErrorResponse) => {
        this.ocupado.set(false);
        const campos = erroresCampos(e);
        this.error.set(Object.keys(campos).length ? Object.values(campos).join('. ') : mensajeError(e, 'No fue posible enviar la orden.'));
      },
    });
  }
}
