import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { erroresCampos, mensajeError } from '../../../core/api/api';
import { CampoPlantilla, PlantillaPatologia, TipoCaso } from '../patologia.models';
import { PatologiaService } from '../patologia.service';

interface CampoEditable extends Omit<CampoPlantilla, 'opciones' | 'unidad'> {
  opciones: string;
  unidad: string;
}

interface Edicion {
  id: number | null;
  tipo: TipoCaso;
  servicio: string;
  nombre: string;
  campos: CampoEditable[];
}

/** RFPAT-005: plantillas por tipo de estudio y personalizadas por servicio, sin programar. */
@Component({
  selector: 'app-plantillas-patologia',
  imports: [FormsModule, RouterLink],
  template: `
    <a class="volver" routerLink="/patologia"><span class="icono">arrow_back</span> Casos de patología</a>
    <div class="cabecera-pagina"><h2>Plantillas de informe</h2></div>

    <section class="tarjeta">
      <div class="tabla-contenedor">
        <table class="tabla">
          <thead><tr><th>Plantilla</th><th>Tipo</th><th>Servicio</th><th>Campos</th><th></th></tr></thead>
          <tbody>
            @for (p of plantillas(); track p.id) {
              <tr>
                <td><strong>{{ p.nombre }}</strong>@if (p.modificadoPor) { <br /><small>Modificada por {{ p.modificadoPor }}</small> }</td>
                <td>{{ p.tipoDescripcion }}</td>
                <td>{{ p.servicio ?? 'General' }}</td>
                <td><small>{{ resumenCampos(p) }}</small></td>
                <td class="nowrap">
                  <button type="button" class="enlace" (click)="editar(p, false)">Editar</button>
                  @if (!p.servicio) { · <button type="button" class="enlace" (click)="editar(p, true)">Personalizar para un servicio</button> }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </section>

    @if (edicion(); as e) {
      <section class="tarjeta editor">
        <div class="tarjeta__titulo"><span class="icono">edit</span>{{ e.id ? 'Editar plantilla' : 'Nueva plantilla de servicio' }}</div>
        <div class="form-rejilla">
          <label>Nombre<input class="control" [(ngModel)]="e.nombre" /></label>
          <label>Servicio<input class="control" [(ngModel)]="e.servicio" [disabled]="!!e.id" placeholder="p. ej. Gastroenterología" /></label>
        </div>
        <div class="tabla-contenedor">
          <table class="tabla campos">
            <thead><tr><th>Clave</th><th>Etiqueta</th><th>Sección</th><th>Tipo</th><th>Opciones (separadas por ;)</th><th>Unidad</th><th>Obligatorio</th><th></th></tr></thead>
            <tbody>
              @for (c of e.campos; track $index; let i = $index) {
                <tr>
                  <td><input class="control" [(ngModel)]="c.clave" placeholder="en_minusculas" /></td>
                  <td><input class="control" [(ngModel)]="c.etiqueta" /></td>
                  <td><select class="control" [(ngModel)]="c.seccion"><option value="MACRO">Macroscopía</option><option value="MICRO">Microscopía</option></select></td>
                  <td><select class="control" [(ngModel)]="c.tipo"><option value="texto">Texto</option><option value="numero">Número</option><option value="opcion">Lista</option></select></td>
                  <td><input class="control" [(ngModel)]="c.opciones" [disabled]="c.tipo !== 'opcion'" /></td>
                  <td><input class="control unidad" [(ngModel)]="c.unidad" /></td>
                  <td><input type="checkbox" [(ngModel)]="c.requerido" aria-label="Obligatorio" /></td>
                  <td><button type="button" class="enlace" (click)="e.campos.splice(i, 1)" aria-label="Quitar campo"><span class="icono">delete</span></button></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <button type="button" class="enlace agregar" (click)="agregar(e)">+ Agregar campo</button>
        @if (error()) { <p class="campo__error">{{ error() }}</p> }
        <div class="acciones">
          <button type="button" class="btn btn--secundario" (click)="edicion.set(null)">Cancelar</button>
          <button type="button" class="btn btn--primario" (click)="guardar(e)">Guardar plantilla</button>
        </div>
      </section>
    }
  `,
  styles: `
    .tarjeta { margin-bottom: 14px; }
    small { color: var(--c-texto-2); }
    .nowrap { white-space: nowrap; }
    .campos td { padding: 6px; }
    .campos .control { min-width: 110px; }
    .unidad { min-width: 60px !important; width: 70px; }
    .agregar { margin: 10px 0; display: inline-block; }
    .editor .form-rejilla { margin-bottom: 12px; }
  `,
})
export class PlantillasPatologia {
  private readonly service = inject(PatologiaService);

  protected readonly plantillas = signal<PlantillaPatologia[]>([]);
  protected readonly edicion = signal<Edicion | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.cargar();
  }

  private cargar(): void {
    this.service.plantillas().subscribe((p) => this.plantillas.set(p));
  }

  protected resumenCampos(p: PlantillaPatologia): string {
    const macro = p.campos.filter((c) => c.seccion === 'MACRO').length;
    return `${macro} de macroscopía · ${p.campos.length - macro} de microscopía`;
  }

  /** Editar la plantilla, o copiarla como punto de partida para un servicio. */
  protected editar(p: PlantillaPatologia, copia: boolean): void {
    this.error.set(null);
    this.edicion.set({
      id: copia ? null : p.id, tipo: p.tipo, servicio: copia ? '' : p.servicio ?? '',
      nombre: copia ? p.nombre + ' — ' : p.nombre,
      campos: p.campos.map((c) => ({ ...c, opciones: (c.opciones ?? []).join('; '), unidad: c.unidad ?? '' })),
    });
  }

  protected agregar(e: Edicion): void {
    e.campos.push({ clave: '', etiqueta: '', seccion: 'MICRO', tipo: 'texto', opciones: '', requerido: false, unidad: '' });
  }

  protected guardar(e: Edicion): void {
    const datos = {
      tipo: e.tipo, servicio: e.servicio.trim() || null, nombre: e.nombre.trim(),
      campos: e.campos.map((c) => ({
        clave: c.clave.trim(), etiqueta: c.etiqueta.trim(), seccion: c.seccion, tipo: c.tipo, requerido: c.requerido,
        opciones: c.tipo === 'opcion' ? c.opciones.split(';').map((o) => o.trim()).filter(Boolean) : null,
        unidad: c.unidad.trim() || null,
      })),
    };
    const peticion = e.id ? this.service.actualizarPlantilla(e.id, datos) : this.service.crearPlantilla(datos);
    peticion.subscribe({
      next: () => {
        this.edicion.set(null);
        this.cargar();
      },
      error: (err: HttpErrorResponse) => {
        const campos = erroresCampos(err);
        this.error.set(Object.keys(campos).length ? Object.values(campos).join('. ') : mensajeError(err, 'No se pudo guardar la plantilla.'));
      },
    });
  }
}
