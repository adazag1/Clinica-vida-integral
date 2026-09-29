import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { mensajeError } from '../../../core/api/api';
import { AuthService } from '../../../core/auth/auth.service';
import { Episodio, TIPOS_ATENCION, TipoAtencion } from '../../admisiones/admisiones.models';
import { AdmisionesService } from '../../admisiones/admisiones.service';
import { Cita } from '../../agenda/agenda.models';
import { AgendaService } from '../../agenda/agenda.service';
import { CAMPOS_PACIENTE, CambioDemografico, Paciente, REGIMENES, SEXOS } from '../pacientes.models';
import { PacientesService } from '../pacientes.service';

@Component({
  selector: 'app-paciente-detalle',
  imports: [DatePipe, RouterLink, ReactiveFormsModule],
  templateUrl: './paciente-detalle.html',
  styleUrl: './paciente-detalle.scss',
})
export class PacienteDetalle implements OnInit {
  private readonly pacientes = inject(PacientesService);
  private readonly admisiones = inject(AdmisionesService);
  private readonly agenda = inject(AgendaService);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);

  readonly id = input.required<string>();

  protected readonly paciente = signal<Paciente | null>(null);
  protected readonly episodios = signal<Episodio[]>([]);
  protected readonly cambios = signal<CambioDemografico[]>([]);
  protected readonly citas = signal<Cita[]>([]);
  protected readonly puedeVerCitas = computed(() => this.auth.tieneRol('AGENDAMIENTO', 'ADMISIONES', 'MEDICO'));
  protected readonly error = signal<string | null>(null);

  /** Sin documento solo se puede ingresar por urgencias (regla del backend). */
  protected readonly tiposAtencion = computed(() =>
    this.paciente()?.identificacionTemporal ? TIPOS_ATENCION.filter((t) => t.valor === 'URGENCIAS') : TIPOS_ATENCION,
  );
  protected readonly campos = CAMPOS_PACIENTE;
  protected readonly admitiendo = signal(false);
  protected readonly guardando = signal(false);
  protected readonly errorAdmision = signal<string | null>(null);
  protected readonly admision = inject(NonNullableFormBuilder).group({
    tipoAtencion: ['URGENCIAS' as TipoAtencion, Validators.required],
    motivoConsulta: ['', [Validators.required, Validators.maxLength(1000)]],
  });

  protected readonly atencionAbierta = computed(() =>
    this.episodios().find((e) => ['EN_ADMISION', 'EN_ESPERA_CONSULTA', 'EN_ATENCION'].includes(e.estado)),
  );

  ngOnInit(): void {
    const id = +this.id();
    this.pacientes.obtener(id).subscribe({
      next: (p) => this.paciente.set(p),
      error: (e: HttpErrorResponse) => this.error.set(mensajeError(e, 'No fue posible cargar el paciente.')),
    });
    this.admisiones.dePaciente(id).subscribe({ next: (lista) => this.episodios.set(lista) });
    if (this.puedeVerCitas()) {
      this.agenda.citasDePaciente(id).subscribe({ next: (lista) => this.citas.set(lista) });
    }
    this.pacientes.cambios(id).subscribe({ next: (lista) => this.cambios.set(lista) });
  }

  protected etiquetaSexo(valor: string | null): string {
    return SEXOS.find((s) => s.valor === valor)?.etiqueta ?? '—';
  }

  protected etiquetaRegimen(valor: string | null): string {
    return REGIMENES.find((r) => r.valor === valor)?.etiqueta ?? '—';
  }

  protected claseEstado(estado: string): string {
    if (estado === 'EN_ESPERA_CONSULTA') return 'chip--normal';
    if (estado === 'EN_ADMISION' || estado === 'EN_ATENCION') return 'chip--info';
    if (estado === 'ANULADO') return 'chip--critico';
    return 'chip--primario';
  }

  protected claseCita(estado: string): string {
    if (estado === 'CONFIRMADA' || estado === 'ATENDIDA') return 'chip--normal';
    if (estado === 'CANCELADA' || estado === 'NO_ASISTIO') return 'chip--critico';
    if (estado === 'REPROGRAMADA') return 'chip--primario';
    return 'chip--info';
  }

  protected admitir(): void {
    if (this.admision.invalid) {
      this.admision.markAllAsTouched();
      return;
    }
    const { tipoAtencion, motivoConsulta } = this.admision.getRawValue();
    this.guardando.set(true);
    this.errorAdmision.set(null);
    this.admisiones.crear(+this.id(), tipoAtencion, motivoConsulta.trim()).subscribe({
      next: (e) => this.router.navigate(['/admisiones', e.id]),
      error: (e: HttpErrorResponse) => {
        this.guardando.set(false);
        this.errorAdmision.set(mensajeError(e, 'No fue posible admitir al paciente.'));
      },
    });
  }
}
