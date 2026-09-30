import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { mensajeError } from '../../../core/api/api';
import { AuthService } from '../../../core/auth/auth.service';
import { DialogoCarga } from '../componentes/dialogo-carga';
import { Documento, DocumentoResumen, ESTADOS_DOCUMENTO, TIPOS_DOCUMENTO, claseEstadoDocumento } from '../documentos.models';
import { DocumentosService, FiltrosDocumentos } from '../documentos.service';

/** RFGAC-003: búsqueda avanzada por paciente, tipo, fechas, profesional, servicio y palabras clave. */
@Component({
  selector: 'app-buscador-documentos',
  imports: [DatePipe, FormsModule, RouterLink, DialogoCarga],
  template: `
    <div class="cabecera-pagina">
      <h2>Gestión documental</h2>
      <div class="enlaces">
        <a class="enlace" routerLink="/documentos/solicitudes"><span class="icono">move_to_inbox</span> Solicitudes al archivo</a>
        @if (puedeVerReportes()) { <a class="enlace" routerLink="/documentos/reportes"><span class="icono">bar_chart</span> Reportes</a> }
        <button type="button" class="btn btn--primario btn--pequeno" (click)="cargando.set(true)"><span class="icono">upload_file</span>Registrar documento</button>
      </div>
    </div>

    <section class="tarjeta">
      <div class="form-rejilla">
        <label>Documento del paciente<input class="control" [(ngModel)]="filtros.documentoPaciente" (keyup.enter)="buscar(0)" /></label>
        <label>Tipo
          <select class="control" [(ngModel)]="filtros.tipo">
            <option value="">Todos</option>
            @for (t of tipos; track t.valor) { <option [value]="t.valor">{{ t.etiqueta }}</option> }
          </select></label>
        <label>Desde<input class="control" type="date" [(ngModel)]="filtros.desde" /></label>
        <label>Hasta<input class="control" type="date" [(ngModel)]="filtros.hasta" /></label>
        <label>Profesional<input class="control" [(ngModel)]="filtros.profesional" (keyup.enter)="buscar(0)" /></label>
        <label>Servicio<input class="control" [(ngModel)]="filtros.servicio" (keyup.enter)="buscar(0)" /></label>
        <label>Estado
          <select class="control" [(ngModel)]="filtros.estado">
            <option value="">Todos</option>
            @for (e of estados; track e.valor) { <option [value]="e.valor">{{ e.etiqueta }}</option> }
          </select></label>
        <label>Palabras clave<input class="control" [(ngModel)]="filtros.texto" (keyup.enter)="buscar(0)" placeholder="En título o descripción" /></label>
      </div>
      <div class="acciones">
        <button type="button" class="btn btn--secundario btn--pequeno" (click)="limpiar()">Limpiar</button>
        <button type="button" class="btn btn--primario btn--pequeno" (click)="buscar(0)"><span class="icono">search</span>Buscar</button>
      </div>
    </section>

    @if (error()) { <div class="alerta alerta--error" role="alert"><span class="icono">error</span>{{ error() }}</div> }

    <section class="tarjeta">
      @if (resultados().length) {
        <p class="total">{{ total() }} documento(s)</p>
        <div class="tabla-contenedor">
          <table class="tabla">
            <thead><tr><th>Fecha</th><th>Documento</th><th>Paciente</th><th>Servicio</th><th>Estado</th><th>Soporte</th><th></th></tr></thead>
            <tbody>
              @for (d of resultados(); track d.id) {
                <tr>
                  <td class="nowrap">{{ d.fechaDocumento | date: 'dd/MM/yyyy' }}</td>
                  <td><strong>{{ d.titulo }}</strong><br /><small>{{ d.tipoDescripcion }}{{ d.versionActual > 1 ? ' · v' + d.versionActual : '' }}
                    {{ d.origen === 'GENERADO' ? ' · generado por el sistema' : '' }}</small></td>
                  <td>{{ d.pacienteNombre }}<br /><small>{{ d.pacienteDocumento }}</small></td>
                  <td>{{ d.servicio }}<br /><small>{{ d.profesional ?? '' }}</small></td>
                  <td><span class="chip" [class]="'chip ' + claseEstado(d.estado)">{{ d.estadoDescripcion }}</span></td>
                  <td><small>{{ d.soporte === 'FISICO' ? 'Físico' : 'Digital' }}{{ d.ubicacion ? ' · ' + d.ubicacion : '' }}</small></td>
                  <td><a class="btn btn--secundario btn--pequeno" [routerLink]="['/documentos', d.id]">Abrir</a></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        @if (paginas() > 1) {
          <div class="paginacion">
            <button type="button" class="btn btn--secundario btn--pequeno" (click)="buscar(pagina() - 1)" [disabled]="pagina() === 0">Anterior</button>
            <span>Página {{ pagina() + 1 }} de {{ paginas() }}</span>
            <button type="button" class="btn btn--secundario btn--pequeno" (click)="buscar(pagina() + 1)" [disabled]="pagina() + 1 >= paginas()">Siguiente</button>
          </div>
        }
      } @else {
        <p class="vacio">{{ buscado() ? 'No hay documentos con estos criterios.' : 'Use los filtros que necesite y pulse Buscar.' }}</p>
      }
    </section>

    @if (cargando()) {
      <app-dialogo-carga (registrado)="registrado($event)" (cerrar)="cargando.set(false)" />
    }
  `,
  styles: `
    .enlaces { display: flex; flex-wrap: wrap; align-items: center; gap: 14px; }
    .tarjeta, .alerta { margin-bottom: 14px; }
    .total { margin: 0 0 8px; color: var(--c-texto-2); font-size: 13px; }
    small { color: var(--c-texto-2); }
    .nowrap { white-space: nowrap; }
    .paginacion { display: flex; justify-content: center; align-items: center; gap: 12px; margin-top: 12px; font-size: 13px; }
  `,
})
export class BuscadorDocumentos {
  private readonly service = inject(DocumentosService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly tipos = TIPOS_DOCUMENTO;
  protected readonly estados = ESTADOS_DOCUMENTO;
  protected readonly claseEstado = claseEstadoDocumento;
  protected readonly puedeVerReportes = computed(() => this.auth.tieneRol('ARCHIVO', 'AUDITORIA', 'DIRECTIVO'));

  protected filtros: FiltrosDocumentos = {};
  protected readonly resultados = signal<DocumentoResumen[]>([]);
  protected readonly total = signal(0);
  protected readonly pagina = signal(0);
  protected readonly paginas = signal(0);
  protected readonly buscado = signal(false);
  protected readonly cargando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected buscar(pagina: number): void {
    this.service.buscar(this.filtros, pagina).subscribe({
      next: (p) => {
        this.resultados.set(p.content);
        this.total.set(p.page.totalElements);
        this.pagina.set(p.page.number);
        this.paginas.set(p.page.totalPages);
        this.buscado.set(true);
        this.error.set(null);
      },
      error: (e: HttpErrorResponse) => this.error.set(mensajeError(e, 'No fue posible buscar.')),
    });
  }

  protected limpiar(): void {
    this.filtros = {};
    this.resultados.set([]);
    this.buscado.set(false);
  }

  protected registrado(d: Documento): void {
    this.cargando.set(false);
    this.router.navigate(['/documentos', d.resumen.id]);
  }
}
