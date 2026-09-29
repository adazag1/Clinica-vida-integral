import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { mensajeError } from '../../../core/api/api';
import { AuthService } from '../../../core/auth/auth.service';
import { ResumenClinicoPanel } from '../componentes/resumen-clinico';
import { Antecedente, ConsultaHistorial, ResumenClinico, Signos, TIPOS_ANTECEDENTE, TipoAntecedente } from '../consulta.models';
import { ConsultaService } from '../consulta.service';

/** RFMT-009: consulta integral del historial clínico del paciente. */
@Component({
  selector: 'app-historia-clinica',
  imports: [DatePipe, DecimalPipe, FormsModule, RouterLink, ResumenClinicoPanel],
  templateUrl: './historia-clinica.html',
  styleUrl: './historia-clinica.scss',
})
export class HistoriaClinica implements OnInit {
  private readonly service = inject(ConsultaService);
  private readonly auth = inject(AuthService);

  readonly pacienteId = input.required<string>();

  protected readonly resumen = signal<ResumenClinico | null>(null);
  protected readonly consultas = signal<ConsultaHistorial[]>([]);
  protected readonly antecedentes = signal<Antecedente[]>([]);
  protected readonly signos = signal<Signos[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly verInactivos = signal(false);
  protected readonly tipos = TIPOS_ANTECEDENTE;

  protected readonly puedeRegistrar = computed(() => ['MEDICO', 'ENFERMERIA'].includes(this.auth.usuario()?.rol ?? ''));
  protected readonly visibles = computed(() => this.antecedentes().filter((a) => this.verInactivos() || a.activo));

  protected nuevo = { tipo: 'ALERGIA' as TipoAntecedente, descripcion: '', detalle: '', severidad: 'MODERADA' };
  protected readonly errorNuevo = signal<string | null>(null);
  protected inactivando: number | null = null;
  protected motivo = '';

  ngOnInit(): void {
    this.cargar();
  }

  private cargar(): void {
    const id = +this.pacienteId();
    this.service.resumen(id).subscribe({
      next: (r) => this.resumen.set(r),
      error: (e: HttpErrorResponse) => this.error.set(mensajeError(e, 'No fue posible cargar la historia clínica.')),
    });
    this.service.historial(id).subscribe({ next: (c) => this.consultas.set(c) });
    this.service.antecedentes(id).subscribe({ next: (a) => this.antecedentes.set(a) });
    this.service.signos(id).subscribe({ next: (s) => this.signos.set(s) });
  }

  protected agregar(): void {
    const n = this.nuevo;
    if (!n.descripcion.trim()) {
      this.errorNuevo.set('Ingrese la descripción.');
      return;
    }
    this.service
      .agregarAntecedente(+this.pacienteId(), {
        tipo: n.tipo,
        descripcion: n.descripcion.trim(),
        detalle: n.detalle.trim() || null,
        severidad: n.tipo === 'ALERGIA' ? n.severidad : null,
      })
      .subscribe({
        next: () => {
          this.nuevo = { tipo: 'ALERGIA', descripcion: '', detalle: '', severidad: 'MODERADA' };
          this.errorNuevo.set(null);
          this.cargar();
        },
        error: (e: HttpErrorResponse) => this.errorNuevo.set(mensajeError(e, 'No fue posible registrar el antecedente.')),
      });
  }

  protected confirmarInactivar(a: Antecedente): void {
    if (!this.motivo.trim()) return;
    this.service.inactivarAntecedente(a.id, this.motivo.trim()).subscribe({
      next: () => {
        this.inactivando = null;
        this.motivo = '';
        this.cargar();
      },
    });
  }

  protected alerta(s: Signos, campo: string): string {
    const a = s.alertas.find((x) => x.campo === campo);
    return a ? (a.nivel === 'CRITICO' ? 'critico' : 'anormal') : '';
  }
}
