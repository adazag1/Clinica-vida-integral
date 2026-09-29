import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { interval } from 'rxjs';

import { mensajeError } from '../../../core/api/api';
import {
  AREAS_LAB,
  ControlCalidad,
  ESTADOS_ORDEN,
  EstadoOrden,
  OrdenResumen,
  PRIORIDADES_LAB,
  PrioridadLab,
  claseEstadoOrden,
} from '../laboratorio.models';
import { LaboratorioService } from '../laboratorio.service';

/** RFLC-002 / RFLC-005 / RFLC-007 / RFLC-012: cola de trabajo y control de calidad del día. */
@Component({
  selector: 'app-cola-laboratorio',
  imports: [DatePipe, FormsModule, RouterLink],
  templateUrl: './cola-laboratorio.html',
  styleUrl: './cola-laboratorio.scss',
})
export class ColaLaboratorio {
  private readonly service = inject(LaboratorioService);

  protected readonly estados = ESTADOS_ORDEN;
  protected readonly prioridades = PRIORIDADES_LAB;
  protected readonly areas = AREAS_LAB;
  protected readonly claseEstado = claseEstadoOrden;

  protected readonly estado = signal<EstadoOrden | null>(null);
  protected readonly prioridad = signal<PrioridadLab | null>(null);
  protected readonly ordenes = signal<OrdenResumen[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly controles = signal<ControlCalidad[]>([]);
  protected readonly registrandoControl = signal(false);
  protected control = { area: 'QUIMICA', equipo: 'Cobas c311', niveles: 'Normal y patológico', loteControl: '', aceptado: true, observacion: '' };
  protected readonly errorControl = signal<string | null>(null);

  constructor() {
    this.cargar();
    this.cargarControles();
    interval(30_000).pipe(takeUntilDestroyed(inject(DestroyRef))).subscribe(() => this.cargar());
  }

  protected filtrar(estado: EstadoOrden | null, prioridad: PrioridadLab | null): void {
    this.estado.set(estado);
    this.prioridad.set(prioridad);
    this.cargar();
  }

  private cargar(): void {
    this.service.cola(this.estado(), this.prioridad()).subscribe({
      next: (l) => {
        this.ordenes.set(l);
        this.cargando.set(false);
        this.error.set(null);
      },
      error: (e: HttpErrorResponse) => {
        this.error.set(mensajeError(e, 'No fue posible cargar las órdenes.'));
        this.cargando.set(false);
      },
    });
  }

  private cargarControles(): void {
    this.service.controles().subscribe({ next: (c) => this.controles.set(c) });
  }

  /** Estado del control del día por área: el último registrado manda. */
  protected estadoControl(area: string): ControlCalidad | undefined {
    return this.controles().find((c) => c.area === area);
  }

  protected elegirArea(area: string): void {
    this.control.area = area;
    this.control.equipo = this.areas.find((a) => a.valor === area)?.equipo ?? '';
  }

  protected guardarControl(): void {
    const c = this.control;
    if (!c.equipo.trim() || !c.niveles.trim() || !c.loteControl.trim()) {
      this.errorControl.set('Complete equipo, niveles y lote del control.');
      return;
    }
    this.service
      .registrarControl({ area: c.area, equipo: c.equipo.trim(), niveles: c.niveles.trim(), loteControl: c.loteControl.trim(),
        aceptado: c.aceptado, observacion: c.observacion.trim() || null })
      .subscribe({
        next: () => {
          this.registrandoControl.set(false);
          this.errorControl.set(null);
          this.control = { ...c, loteControl: '', observacion: '', aceptado: true };
          this.cargarControles();
        },
        error: (e: HttpErrorResponse) => this.errorControl.set(mensajeError(e, 'No fue posible registrar el control.')),
      });
  }

  protected minutosRestantes(o: OrdenResumen): number {
    return Math.round((new Date(o.limiteTat).getTime() - Date.now()) / 60_000);
  }
}
