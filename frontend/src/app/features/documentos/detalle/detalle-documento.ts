import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { erroresCampos, mensajeError } from '../../../core/api/api';
import { AuthService } from '../../../core/auth/auth.service';
import { Documento, ESTADOS_DOCUMENTO, claseEstadoDocumento, tamano } from '../documentos.models';
import { DocumentosService } from '../documentos.service';

type Panel = 'version' | 'ubicacion' | 'estado' | 'solicitud' | null;

/** Detalle del documento: versiones, descarga, ubicación física, ciclo de vida y trazabilidad (RFGAC-005/006/010). */
@Component({
  selector: 'app-detalle-documento',
  imports: [DatePipe, FormsModule, RouterLink],
  template: `
    <a class="volver" routerLink="/documentos"><span class="icono">arrow_back</span> Gestión documental</a>
    @if (error()) { <div class="alerta alerta--error" role="alert"><span class="icono">error</span>{{ error() }}</div> }
    @if (aviso()) { <div class="alerta alerta--exito" role="status"><span class="icono">check_circle</span>{{ aviso() }}</div> }

    @if (documento(); as d) {
      <section class="tarjeta encabezado">
        <span class="icono grande">{{ d.resumen.tieneArchivo && d.resumen.tipoMime?.startsWith('image') ? 'image' : 'description' }}</span>
        <div class="datos">
          <h2>{{ d.resumen.titulo }}</h2>
          <p>{{ d.resumen.tipoDescripcion }} · {{ d.resumen.fechaDocumento | date: 'dd/MM/yyyy' }} · {{ d.resumen.servicio }}
            {{ d.resumen.profesional ? ' · ' + d.resumen.profesional : '' }}</p>
          <p><strong>{{ d.paciente.nombreCompleto }}</strong> · {{ d.paciente.tipoDocumento }} {{ d.paciente.numeroDocumento }}</p>
        </div>
        <span class="chip" [class]="'chip ' + claseEstado(d.resumen.estado)">{{ d.resumen.estadoDescripcion }}</span>
      </section>

      <div class="rejilla">
        <div class="principal">
          <section class="tarjeta">
            <div class="tarjeta__titulo"><span class="icono">info</span>Información</div>
            <dl class="datos-lista">
              <dt>Origen</dt><dd>{{ d.resumen.origen === 'GENERADO' ? 'Generado por el sistema' : 'Cargado' }}
                @if (d.resumen.enlace) { · <a [routerLink]="d.resumen.enlace">ver el original</a> }</dd>
              <dt>Soporte</dt><dd>{{ d.resumen.soporte === 'FISICO' ? 'Físico' : 'Digital' }}</dd>
              <dt>Ubicación física</dt><dd>{{ d.resumen.ubicacion ?? '—' }}</dd>
              <dt>Conservar hasta</dt><dd>{{ d.retencionHasta | date: 'dd/MM/yyyy' }}</dd>
              @if (d.descripcion) { <dt>Descripción</dt><dd>{{ d.descripcion }}</dd> }
              @if (d.motivoEstado) { <dt>Último cambio de estado</dt><dd>{{ d.motivoEstado }}</dd> }
              <dt>Registrado</dt><dd>{{ d.registradoEn | date: 'dd/MM/yyyy h:mm a' }} por {{ d.registradoPor }}</dd>
            </dl>
          </section>

          <section class="tarjeta">
            <div class="titulo-accion">
              <div class="tarjeta__titulo"><span class="icono">layers</span>Versiones</div>
              @if (puedeCargar() && d.resumen.origen === 'CARGADO' && d.resumen.estado !== 'ELIMINADO') {
                <button type="button" class="enlace" (click)="abrir('version')">+ Nueva versión</button>
              }
            </div>
            @if (panel() === 'version') {
              <div class="form-rejilla panel">
                <label>Archivo<input type="file" accept=".pdf,.jpg,.jpeg,.png" (change)="elegirArchivo($event)" /></label>
                <label>Motivo del cambio<input class="control" [(ngModel)]="motivo" /></label>
              </div>
              <div class="acciones"><button type="button" class="btn btn--primario btn--pequeno" (click)="subirVersion()"
                [disabled]="!archivo || !motivo.trim() || ocupado()">Guardar versión</button></div>
            }
            @for (v of d.versiones; track v.version; let primera = $first) {
              <div class="version">
                <span class="icono">{{ v.tipoMime === 'application/pdf' ? 'picture_as_pdf' : 'image' }}</span>
                <span><strong>v{{ v.version }}{{ primera ? ' (vigente)' : '' }}</strong> · {{ v.nombre }} · {{ tamano(v.tamano) }}
                  <small>{{ v.fecha | date: 'dd/MM/yyyy h:mm a' }} · {{ v.usuario }}{{ v.motivo ? ' · ' + v.motivo : '' }}</small>
                  <small class="huella" [title]="v.sha256">SHA-256 {{ v.sha256.slice(0, 16) }}…</small></span>
                @if (d.resumen.estado !== 'ELIMINADO') {
                  <button type="button" class="btn btn--secundario btn--pequeno" (click)="abrirArchivo(v.version)">Abrir</button>
                }
              </div>
            } @empty {
              <p class="vacio">{{ d.resumen.origen === 'GENERADO' ? 'El contenido está en el módulo que lo generó.' : 'Documento físico sin copia digital.' }}</p>
            }
          </section>
        </div>

        <aside class="lateral">
          <section class="tarjeta">
            <div class="tarjeta__titulo"><span class="icono">settings</span>Acciones</div>
            <div class="botones">
              @if (esArchivo() && d.resumen.estado !== 'ELIMINADO') {
                <button type="button" class="btn btn--secundario btn--pequeno" (click)="abrir('ubicacion')"><span class="icono">shelves</span>Ubicación física</button>
                <button type="button" class="btn btn--secundario btn--pequeno" (click)="abrir('estado')"><span class="icono">sync_alt</span>Cambiar estado</button>
              }
              @if (puedeSolicitar()) {
                <button type="button" class="btn btn--secundario btn--pequeno" (click)="abrir('solicitud')"><span class="icono">move_to_inbox</span>Solicitar al archivo</button>
              }
            </div>
            @switch (panel()) {
              @case ('ubicacion') {
                <div class="form-rejilla panel">
                  <label>Sala<input class="control" [(ngModel)]="ubicacion.sala" /></label>
                  <label>Estantería<input class="control" [(ngModel)]="ubicacion.estanteria" /></label>
                  <label>Sección<input class="control" [(ngModel)]="ubicacion.seccion" /></label>
                  <label>Caja<input class="control" [(ngModel)]="ubicacion.caja" /></label>
                </div>
                <div class="acciones"><button type="button" class="btn btn--primario btn--pequeno" (click)="guardarUbicacion()"
                  [disabled]="!ubicacion.sala.trim() || !ubicacion.estanteria.trim() || ocupado()">Guardar</button></div>
              }
              @case ('estado') {
                <div class="form-rejilla panel">
                  <label>Nuevo estado
                    <select class="control" [(ngModel)]="nuevoEstado">
                      @for (e of estados; track e.valor) { @if (e.valor !== d.resumen.estado) { <option [value]="e.valor">{{ e.etiqueta }}</option> } }
                    </select></label>
                  <label class="ancho">Motivo<input class="control" [(ngModel)]="motivo" /></label>
                </div>
                @if (nuevoEstado === 'ELIMINADO') {
                  <p class="advertencia"><span class="icono">warning</span>Solo es posible después del {{ d.retencionHasta | date: 'dd/MM/yyyy' }}.
                    Se borra el contenido y se conservan los datos del documento y sus huellas.</p>
                }
                <div class="acciones"><button type="button" class="btn btn--primario btn--pequeno" (click)="cambiarEstado()"
                  [disabled]="!nuevoEstado || !motivo.trim() || ocupado()">Aplicar</button></div>
              }
              @case ('solicitud') {
                <div class="form-rejilla panel">
                  <label class="ancho">¿Para qué lo necesita?<input class="control" [(ngModel)]="motivo" /></label>
                  <label class="check"><input type="checkbox" [(ngModel)]="urgente" /> Urgente</label>
                </div>
                <div class="acciones"><button type="button" class="btn btn--primario btn--pequeno" (click)="solicitar()"
                  [disabled]="!motivo.trim() || ocupado()">Enviar solicitud</button></div>
              }
            }
          </section>

          <section class="tarjeta">
            <div class="tarjeta__titulo"><span class="icono">receipt_long</span>Trazabilidad</div>
            <ul class="lista-eventos">
              @for (ev of d.eventos; track $index) {
                <li><small>{{ ev.fecha | date: 'dd/MM/yyyy h:mm a' }} · {{ ev.usuario }}</small><br />{{ accion(ev.accion) }}{{ ev.detalle ? ': ' + ev.detalle : '' }}</li>
              }
            </ul>
          </section>
        </aside>
      </div>
    }
  `,
  styles: `
    .alerta { margin-bottom: 12px; }
    .encabezado { display: flex; align-items: flex-start; gap: 16px; margin-bottom: 14px; }
    .encabezado h2 { font-size: 20px; margin-bottom: 4px; }
    .encabezado p { margin: 2px 0; color: var(--c-texto-2); }
    .grande { font-size: 40px; color: var(--c-azul); }
    .datos { flex: 1; min-width: 0; }
    .rejilla { display: grid; grid-template-columns: minmax(0, 2fr) minmax(280px, 1fr); gap: 14px; align-items: start; }
    .principal, .lateral { display: flex; flex-direction: column; gap: 14px; }
    .datos-lista { display: grid; grid-template-columns: minmax(130px, auto) 1fr; gap: 6px 14px; margin: 0; font-size: 13.5px; }
    .datos-lista dt { color: var(--c-texto-2); }
    .datos-lista dd { margin: 0; }
    .version { display: flex; align-items: center; gap: 10px; padding: 10px 0; border-bottom: 1px solid var(--c-borde); }
    .version > .icono { color: var(--c-azul); }
    .version > span:nth-child(2) { flex: 1; display: flex; flex-direction: column; min-width: 0; font-size: 13.5px; }
    .version small { color: var(--c-texto-2); }
    .huella { font-family: ui-monospace, monospace; font-size: 11.5px; }
    .panel { margin: 10px 0; }
    .botones { display: flex; flex-wrap: wrap; gap: 8px; }
    .advertencia { display: flex; gap: 6px; color: var(--c-alerta); font-size: 13px; }
    @media (max-width: 900px) { .rejilla { grid-template-columns: 1fr; } }
  `,
})
export class DetalleDocumento {
  private readonly service = inject(DocumentosService);
  private readonly auth = inject(AuthService);

  /** Viene de la ruta /documentos/:id. */
  readonly id = input.required<string>();

  protected readonly estados = ESTADOS_DOCUMENTO;
  protected readonly claseEstado = claseEstadoDocumento;
  protected readonly tamano = tamano;
  protected readonly documento = signal<Documento | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  protected readonly ocupado = signal(false);
  protected readonly panel = signal<Panel>(null);

  protected readonly esArchivo = computed(() => this.auth.tieneRol('ARCHIVO'));
  protected readonly puedeCargar = computed(() => this.auth.tieneRol('ARCHIVO', 'MEDICO', 'ENFERMERIA', 'ADMISIONES'));
  protected readonly puedeSolicitar = computed(() => this.auth.tieneRol('MEDICO', 'ENFERMERIA', 'AUDITORIA'));

  protected motivo = '';
  protected urgente = false;
  protected nuevoEstado = '';
  protected archivo: File | null = null;
  protected ubicacion = { sala: '', estanteria: '', seccion: '', caja: '' };

  constructor() {
    effect(() => this.cargar(Number(this.id())));
  }

  private cargar(id: number): void {
    this.service.obtener(id).subscribe({
      next: (d) => this.mostrar(d),
      error: (e: HttpErrorResponse) => this.error.set(mensajeError(e, 'No fue posible cargar el documento.')),
    });
  }

  private mostrar(d: Documento): void {
    this.documento.set(d);
    this.ubicacion = { sala: d.sala ?? '', estanteria: d.estanteria ?? '', seccion: d.seccion ?? '', caja: d.caja ?? '' };
  }

  protected accion(a: string): string {
    const nombres: Record<string, string> = {
      REGISTRADO: 'Registrado', VER: 'Consultado', DESCARGAR: 'Abierto el archivo', NUEVA_VERSION: 'Nueva versión',
      UBICACION: 'Ubicación', CAMBIO_ESTADO: 'Cambio de estado', ELIMINADO: 'Eliminado', SOLICITADO: 'Solicitado al archivo',
    };
    return nombres[a] ?? a;
  }

  protected abrir(p: Panel): void {
    this.motivo = '';
    this.urgente = false;
    this.nuevoEstado = '';
    this.archivo = null;
    this.error.set(null);
    this.panel.set(this.panel() === p ? null : p);
  }

  protected elegirArchivo(ev: Event): void {
    this.archivo = (ev.target as HTMLInputElement).files?.[0] ?? null;
  }

  /** Abre el archivo en otra pestaña a partir del blob (el enlace directo no llevaría el token). */
  protected abrirArchivo(version: number): void {
    const d = this.documento();
    if (!d) return;
    const ventana = window.open('', '_blank');
    this.service.contenido(d.resumen.id, version).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        if (ventana) ventana.location.href = url;
        else window.location.href = url;
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: (e: HttpErrorResponse) => {
        ventana?.close();
        this.error.set(mensajeError(e, 'No fue posible abrir el archivo.'));
      },
    });
  }

  protected subirVersion(): void {
    const d = this.documento();
    if (d && this.archivo) this.ejecutar(this.service.nuevaVersion(d.resumen.id, this.archivo, this.motivo.trim()), 'Nueva versión guardada; la anterior se conserva.');
  }

  protected guardarUbicacion(): void {
    const d = this.documento();
    const u = this.ubicacion;
    if (d) this.ejecutar(this.service.ubicar(d.resumen.id, { sala: u.sala.trim(), estanteria: u.estanteria.trim(),
      seccion: u.seccion.trim() || null, caja: u.caja.trim() || null }), 'Ubicación registrada.');
  }

  protected cambiarEstado(): void {
    const d = this.documento();
    if (d) this.ejecutar(this.service.cambiarEstado(d.resumen.id, this.nuevoEstado, this.motivo.trim()), 'Estado actualizado.');
  }

  protected solicitar(): void {
    const d = this.documento();
    if (!d) return;
    this.ocupado.set(true);
    this.service.solicitar({ pacienteId: d.paciente.id, documentoId: d.resumen.id, motivo: this.motivo.trim(), urgente: this.urgente }).subscribe({
      next: () => {
        this.ocupado.set(false);
        this.panel.set(null);
        this.aviso.set('Solicitud enviada al archivo. Le avisaremos cuando esté disponible.');
        this.cargar(d.resumen.id);
      },
      error: (e: HttpErrorResponse) => {
        this.ocupado.set(false);
        this.error.set(mensajeError(e, 'No fue posible enviar la solicitud.'));
      },
    });
  }

  private ejecutar(obs: Observable<Documento>, exito: string): void {
    this.ocupado.set(true);
    this.aviso.set(null);
    obs.subscribe({
      next: (d) => {
        this.ocupado.set(false);
        this.panel.set(null);
        this.mostrar(d);
        this.aviso.set(exito);
      },
      error: (e: HttpErrorResponse) => {
        this.ocupado.set(false);
        const campos = erroresCampos(e);
        this.error.set(Object.keys(campos).length ? Object.values(campos).join('. ') : mensajeError(e, 'No fue posible completar la acción.'));
      },
    });
  }
}
