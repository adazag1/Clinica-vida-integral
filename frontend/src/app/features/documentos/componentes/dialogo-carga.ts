import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { erroresCampos, mensajeError } from '../../../core/api/api';
import { AuthService } from '../../../core/auth/auth.service';
import { PacienteResumen } from '../../pacientes/pacientes.models';
import { Documento, TIPOS_DOCUMENTO } from '../documentos.models';
import { DocumentosService } from '../documentos.service';
import { SelectorPaciente } from './selector-paciente';

/** RFGAC-002 / 004 / 010: registrar un documento digital (PDF o imagen) o físico con sus metadatos. */
@Component({
  selector: 'app-dialogo-carga',
  imports: [FormsModule, SelectorPaciente],
  template: `
    <div class="velo" (click)="cerrar.emit()"></div>
    <div class="dialogo tarjeta ancho" role="dialog" aria-modal="true" aria-labelledby="titulo-carga">
      <h3 id="titulo-carga"><span class="icono">upload_file</span>Registrar documento</h3>
      <div class="form-rejilla">
        <div class="etiqueta ancho">Paciente
          @if (paciente(); as p) {
            <strong>{{ p.nombreCompleto }} · {{ p.tipoDocumento }} {{ p.numeroDocumento }}</strong>
          } @else {
            <app-selector-paciente (seleccion)="elegido.set($event)" />
          }
        </div>
        <label>Tipo
          <select class="control" [(ngModel)]="datos.tipo" [class.control--error]="campos()['tipo']">
            <option value="">Seleccione…</option>
            @for (t of tipos(); track t.valor) { <option [value]="t.valor">{{ t.etiqueta }}</option> }
          </select></label>
        <label>Fecha del documento<input class="control" type="date" [(ngModel)]="datos.fechaDocumento" [max]="hoy" /></label>
        <label class="ancho">Título<input class="control" [(ngModel)]="datos.titulo" [class.control--error]="campos()['titulo']"
          placeholder="p. ej. Resonancia de rodilla — Laboratorio externo" /></label>
        <label>Servicio<input class="control" [(ngModel)]="datos.servicio" [class.control--error]="campos()['servicio']" /></label>
        <label>Profesional<input class="control" [(ngModel)]="datos.profesional" /></label>
        <label class="ancho">Descripción / palabras clave<textarea class="control" rows="2" [(ngModel)]="datos.descripcion"></textarea></label>
        <label>Soporte
          <select class="control" [(ngModel)]="datos.soporte">
            <option value="DIGITAL">Digital o digitalizado</option>
            <option value="FISICO">Físico (en el archivo)</option>
          </select></label>
        <label>{{ datos.soporte === 'DIGITAL' ? 'Archivo (PDF, JPG o PNG, máx. 10 MB)' : 'Copia digitalizada (opcional)' }}
          <input type="file" accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png" (change)="elegirArchivo($event)" /></label>
        @if (datos.soporte === 'FISICO') {
          <label>Sala<input class="control" [(ngModel)]="datos.sala" [class.control--error]="campos()['sala']" /></label>
          <label>Estantería<input class="control" [(ngModel)]="datos.estanteria" /></label>
          <label>Sección<input class="control" [(ngModel)]="datos.seccion" /></label>
          <label>Caja<input class="control" [(ngModel)]="datos.caja" /></label>
        }
      </div>
      @if (error()) { <div class="alerta alerta--error" role="alert"><span class="icono">error</span>{{ error() }}</div> }
      <div class="acciones">
        <button type="button" class="btn btn--secundario" (click)="cerrar.emit()">Cancelar</button>
        <button type="button" class="btn btn--primario" (click)="guardar()" [disabled]="ocupado() || !pacienteId()">Registrar</button>
      </div>
    </div>
  `,
  styles: `
    .ancho.dialogo { width: min(640px, calc(100vw - 32px)); }
    .etiqueta { display: flex; flex-direction: column; gap: 4px; font-size: 13px; color: var(--c-texto-2); }
    .etiqueta strong { color: var(--c-texto); }
  `,
})
export class DialogoCarga {
  private readonly service = inject(DocumentosService);
  private readonly auth = inject(AuthService);

  /** Si se abre desde la historia de un paciente, ya viene elegido. */
  readonly paciente = input<PacienteResumen | null>(null);
  readonly registrado = output<Documento>();
  readonly cerrar = output<void>();

  protected readonly elegido = signal<PacienteResumen | null>(null);
  protected readonly pacienteId = computed(() => this.paciente()?.id ?? this.elegido()?.id ?? null);
  /** Admisiones solo registra documentos administrativos. */
  protected readonly tipos = computed(() =>
    this.auth.usuario()?.rol === 'ADMISIONES' ? TIPOS_DOCUMENTO.filter((t) => t.administrativo) : TIPOS_DOCUMENTO,
  );
  protected readonly hoy = new Date().toLocaleDateString('en-CA');
  protected datos = {
    tipo: '', titulo: '', descripcion: '', fechaDocumento: this.hoy, servicio: '', profesional: '', soporte: 'DIGITAL',
    sala: '', estanteria: '', seccion: '', caja: '',
  };
  protected archivo: File | null = null;
  protected readonly ocupado = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly campos = signal<Record<string, string>>({});

  protected elegirArchivo(ev: Event): void {
    this.archivo = (ev.target as HTMLInputElement).files?.[0] ?? null;
  }

  protected guardar(): void {
    this.ocupado.set(true);
    this.error.set(null);
    this.service.cargar({ pacienteId: this.pacienteId(), ...this.datos }, this.archivo).subscribe({
      next: (d) => {
        this.ocupado.set(false);
        this.registrado.emit(d);
      },
      error: (e: HttpErrorResponse) => {
        this.ocupado.set(false);
        const campos = erroresCampos(e);
        this.campos.set(campos);
        this.error.set(Object.keys(campos).length ? Object.values(campos).join('. ') : mensajeError(e, 'No fue posible registrar el documento.'));
      },
    });
  }
}
