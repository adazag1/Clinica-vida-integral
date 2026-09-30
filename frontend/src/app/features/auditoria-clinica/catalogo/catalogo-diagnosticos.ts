import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { erroresCampos, mensajeError } from '../../../core/api/api';
import { AuthService } from '../../../core/auth/auth.service';
import { CambioCatalogo, DiagnosticoCatalogo } from '../auditoria.models';
import { AuditoriaClinicaService } from '../auditoria.service';

/** RFAU-005: catálogo CIE-10 con control de versiones; un código no se borra, se inactiva con motivo. */
@Component({
  selector: 'app-catalogo-diagnosticos',
  imports: [DatePipe, FormsModule, RouterLink],
  template: `
    <a class="volver" routerLink="/auditoria-clinica"><span class="icono">arrow_back</span> Auditoría clínica</a>
    <div class="cabecera-pagina">
      <h2>Catálogo CIE-10</h2>
      @if (version(); as v) { <span class="chip chip--info">Versión {{ v.version }} · {{ v.actualizadoEn | date: 'dd/MM/yyyy' }} · {{ v.actualizadoPor }}</span> }
    </div>
    @if (error()) { <div class="alerta alerta--error" role="alert"><span class="icono">error</span>{{ error() }}</div> }

    <div class="rejilla">
      <section class="tarjeta">
        <div class="linea">
          <input class="control" [(ngModel)]="texto" (keyup.enter)="buscar()" placeholder="Código o nombre" />
          <button type="button" class="btn btn--secundario btn--pequeno" (click)="buscar()">Buscar</button>
        </div>
        @if (puedeEditar()) {
          <div class="form-rejilla nuevo">
            <label>Código<input class="control" [(ngModel)]="nuevo.codigo" placeholder="p. ej. U07.1" /></label>
            <label class="ancho">Nombre<input class="control" [(ngModel)]="nuevo.nombre" /></label>
          </div>
          <div class="acciones"><button type="button" class="btn btn--primario btn--pequeno" (click)="crear()" [disabled]="!nuevo.codigo.trim() || !nuevo.nombre.trim()">Agregar código</button></div>
        }
        <table class="tabla">
          <tbody>
            @for (d of lista(); track d.id) {
              <tr [class.inactivo]="!d.activo">
                <td><strong>{{ d.codigo }}</strong></td>
                <td>{{ d.nombre }}@if (!d.activo) { <span class="chip">Inactivo</span> }</td>
                <td>@if (puedeEditar()) { <button type="button" class="enlace" (click)="editar(d)">Editar</button> }</td>
              </tr>
              @if (editando()?.id === d.id) {
                <tr><td colspan="3">
                  <div class="form-rejilla">
                    <label class="ancho">Nombre<input class="control" [(ngModel)]="edicion.nombre" /></label>
                    <label class="check"><input type="checkbox" [(ngModel)]="edicion.activo" /> Activo (se ofrece al médico)</label>
                    <label class="ancho">Motivo del cambio<input class="control" [(ngModel)]="edicion.motivo" /></label>
                  </div>
                  <div class="acciones"><button type="button" class="btn btn--primario btn--pequeno" (click)="guardar(d)" [disabled]="!edicion.motivo.trim()">Guardar</button></div>
                </td></tr>
              }
            }
          </tbody>
        </table>
      </section>
      <section class="tarjeta">
        <div class="tarjeta__titulo"><span class="icono">history</span>Historial del catálogo</div>
        <ul class="lista-eventos">
          @for (h of historial(); track $index) {
            <li><small>v{{ h.version }} · {{ h.fecha | date: 'dd/MM/yyyy h:mm a' }} · {{ h.usuario }}</small><br /><strong>{{ h.accion }}</strong> {{ h.codigo }}{{ h.detalle ? ': ' + h.detalle : '' }}</li>
          } @empty { <li class="vacio">Sin cambios desde la versión inicial.</li> }
        </ul>
      </section>
    </div>
  `,
  styles: `
    .alerta { margin-bottom: 12px; }
    .rejilla { display: grid; grid-template-columns: minmax(0, 1.6fr) minmax(280px, 1fr); gap: 14px; align-items: start; }
    .linea { display: flex; gap: 6px; margin-bottom: 10px; }
    .nuevo { margin-bottom: 6px; }
    .acciones { margin-bottom: 10px; }
    .inactivo td { opacity: 0.6; }
    .chip { margin-left: 6px; font-size: 11px; }
    @media (max-width: 900px) { .rejilla { grid-template-columns: 1fr; } }
  `,
})
export class CatalogoDiagnosticos {
  private readonly service = inject(AuditoriaClinicaService);
  private readonly auth = inject(AuthService);

  protected readonly puedeEditar = computed(() => this.auth.usuario()?.rol === 'AUDITORIA');
  protected readonly lista = signal<DiagnosticoCatalogo[]>([]);
  protected readonly historial = signal<CambioCatalogo[]>([]);
  protected readonly version = signal<{ version: number; actualizadoEn: string; actualizadoPor: string } | null>(null);
  protected readonly editando = signal<DiagnosticoCatalogo | null>(null);
  protected readonly error = signal<string | null>(null);
  protected texto = '';
  protected nuevo = { codigo: '', nombre: '' };
  protected edicion = { nombre: '', activo: true, motivo: '' };

  constructor() {
    this.buscar();
    this.refrescarVersion();
  }

  protected buscar(): void {
    this.service.diagnosticos(this.texto).subscribe((l) => this.lista.set(l));
  }

  private refrescarVersion(): void {
    this.service.version('diagnosticos').subscribe((v) => this.version.set(v));
    this.service.historial('diagnosticos').subscribe((h) => this.historial.set(h));
  }

  protected crear(): void {
    this.service.crearDiagnostico(this.nuevo.codigo.trim(), this.nuevo.nombre.trim()).subscribe({
      next: () => {
        this.nuevo = { codigo: '', nombre: '' };
        this.error.set(null);
        this.buscar();
        this.refrescarVersion();
      },
      error: (e: HttpErrorResponse) => this.fallo(e),
    });
  }

  protected editar(d: DiagnosticoCatalogo): void {
    this.edicion = { nombre: d.nombre, activo: d.activo, motivo: '' };
    this.editando.set(this.editando()?.id === d.id ? null : d);
  }

  protected guardar(d: DiagnosticoCatalogo): void {
    this.service.actualizarDiagnostico(d.id, this.edicion.nombre.trim(), this.edicion.activo, this.edicion.motivo.trim()).subscribe({
      next: () => {
        this.editando.set(null);
        this.error.set(null);
        this.buscar();
        this.refrescarVersion();
      },
      error: (e: HttpErrorResponse) => this.fallo(e),
    });
  }

  private fallo(e: HttpErrorResponse): void {
    const campos = erroresCampos(e);
    this.error.set(Object.keys(campos).length ? Object.values(campos).join('. ') : mensajeError(e, 'No se pudo guardar.'));
  }
}
