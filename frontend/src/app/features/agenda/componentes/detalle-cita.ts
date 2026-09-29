import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { mensajeError } from '../../../core/api/api';
import { AuthService } from '../../../core/auth/auth.service';
import { Cita, Cupo, MOTIVOS_CANCELACION, ResultadoCupoLiberado, fechaLocal } from '../agenda.models';
import { AgendaService } from '../agenda.service';
import { ConsultaService } from '../../consulta/consulta.service';
import { SelectorCupo } from './selector-cupo';

type Modo = 'ver' | 'cancelar' | 'reprogramar';

/** Panel lateral de la cita seleccionada, como en el mockup. */
@Component({
  selector: 'app-detalle-cita',
  imports: [DatePipe, FormsModule, RouterLink, SelectorCupo],
  templateUrl: './detalle-cita.html',
  styleUrls: ['./panel.scss', './detalle-cita.scss'],
})
export class DetalleCita {
  private readonly service = inject(AgendaService);
  private readonly auth = inject(AuthService);
  private readonly consultas = inject(ConsultaService);
  private readonly router = inject(Router);

  readonly cita = input.required<Cita>();
  /** La cita cambió (confirmada, cancelada...): la agenda debe recargarse. */
  readonly cambio = output<Cita>();
  readonly cerrar = output<void>();

  protected readonly motivos = MOTIVOS_CANCELACION;
  protected readonly modo = signal<Modo>('ver');
  protected readonly ocupado = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  protected readonly copiado = signal(false);

  protected motivoCancelacion = 'SOLICITUD_PACIENTE';
  protected detalle = '';
  protected readonly nuevoCupo = signal<Cupo | null>(null);

  protected readonly puedeEditar = computed(() => this.auth.tieneRol('AGENDAMIENTO', 'ADMISIONES'));
  protected readonly pendiente = computed(() => ['PROGRAMADA', 'CONFIRMADA'].includes(this.cita().estado));
  protected readonly futura = computed(() => new Date(this.cita().inicio).getTime() > Date.now());
  protected readonly puedeAsistencia = computed(() => this.pendiente() && !this.futura());
  protected readonly fechaCita = computed(() => fechaLocal(new Date(this.cita().inicio)));

  protected readonly esMedico = computed(() => this.auth.usuario()?.rol === 'MEDICO');

  /** Abre la consulta de esta cita (Fase 4). */
  protected atender(): void {
    this.ocupado.set(true);
    this.consultas.iniciar(this.cita().paciente.id, null, this.cita().id).subscribe({
      next: (c) => this.router.navigate(['/consulta', c.id]),
      error: (e: HttpErrorResponse) => {
        this.ocupado.set(false);
        this.error.set(mensajeError(e, 'No fue posible abrir la consulta.'));
      },
    });
  }

  protected claseEstado(estado: string): string {
    return (
      { CONFIRMADA: 'chip--normal', ATENDIDA: 'chip--primario', CANCELADA: 'chip--critico', NO_ASISTIO: 'chip--critico' } as Record<string, string>
    )[estado] ?? 'chip--info';
  }

  protected cambiarModo(modo: Modo): void {
    this.modo.set(modo);
    this.error.set(null);
    this.aviso.set(null);
    this.detalle = '';
    this.nuevoCupo.set(null);
  }

  protected confirmar(): void {
    this.ejecutar(this.service.confirmar(this.cita().id), 'Cita confirmada.');
  }

  protected recordatorio(): void {
    this.ejecutar(this.service.recordatorio(this.cita().id), 'Recordatorio registrado. Revise el resultado abajo.');
  }

  protected asistencia(asistio: boolean): void {
    this.ejecutar(this.service.asistencia(this.cita().id, asistio), asistio ? 'Asistencia registrada.' : 'Inasistencia registrada.');
  }

  protected cancelar(): void {
    this.liberar(this.service.cancelar(this.cita().id, this.motivoCancelacion, this.detalle.trim() || null), 'Cita cancelada.');
  }

  protected reprogramar(): void {
    const c = this.nuevoCupo();
    if (!c) {
      this.error.set('Elija el nuevo horario.');
      return;
    }
    const otro = c.profesionalId !== this.cita().profesional.id ? c.profesionalId : null;
    this.liberar(this.service.reprogramar(this.cita().id, c.inicio, otro, this.detalle.trim() || null), 'Cita reprogramada.');
  }

  protected async copiarEnlace(): Promise<void> {
    const enlace = this.cita().enlacePaciente;
    if (!enlace) return;
    try {
      await navigator.clipboard.writeText(enlace);
      this.copiado.set(true);
      setTimeout(() => this.copiado.set(false), 2000);
    } catch {
      this.error.set('No se pudo copiar. Enlace: ' + enlace);
    }
  }

  private ejecutar(peticion: Observable<Cita>, mensaje: string): void {
    this.ocupado.set(true);
    this.error.set(null);
    peticion.subscribe({
      next: (c) => {
        this.ocupado.set(false);
        this.modo.set('ver');
        this.aviso.set(mensaje);
        this.cambio.emit(c);
      },
      error: (e: HttpErrorResponse) => {
        this.ocupado.set(false);
        this.error.set(mensajeError(e, 'No fue posible completar la acción.'));
      },
    });
  }

  /** Cancelar y reprogramar liberan el cupo: se informa si lo tomó alguien de la lista de espera. */
  private liberar(peticion: Observable<ResultadoCupoLiberado>, mensaje: string): void {
    this.ocupado.set(true);
    this.error.set(null);
    peticion.subscribe({
      next: (r) => {
        this.ocupado.set(false);
        this.modo.set('ver');
        const asignada = r.asignadaListaEspera;
        this.aviso.set(
          asignada
            ? `${mensaje} El cupo se asignó a ${asignada.paciente.nombreCompleto} de la lista de espera: llámelo para confirmar.`
            : mensaje,
        );
        this.cambio.emit(r.cita);
      },
      error: (e: HttpErrorResponse) => {
        this.ocupado.set(false);
        this.error.set(mensajeError(e, 'No fue posible completar la acción.'));
      },
    });
  }
}
