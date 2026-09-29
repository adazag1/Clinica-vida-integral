import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';
import { EMPTY, catchError, switchMap, timer } from 'rxjs';

import { mensajeError } from '../../../core/api/api';
import { AuthService } from '../../../core/auth/auth.service';
import { Episodio, ListaEspera as Lista, ROMANO } from '../admisiones.models';
import { AdmisionesService } from '../admisiones.service';

const REFRESCO_MS = 20_000;

/** RFADM-013 Lista dinámica de pacientes en espera de triage y de consulta. */
@Component({
  selector: 'app-lista-espera',
  imports: [RouterLink, NgTemplateOutlet],
  templateUrl: './lista-espera.html',
  styleUrl: './lista-espera.scss',
})
export class ListaEspera {
  private readonly service = inject(AdmisionesService);
  protected readonly auth = inject(AuthService);

  protected readonly lista = signal<Lista | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly actualizado = signal<Date | null>(null);
  protected readonly ahora = signal(Date.now());
  protected readonly romano = ROMANO;

  constructor() {
    timer(0, REFRESCO_MS)
      .pipe(
        // Un fallo de red no detiene la actualización: se reintenta en el siguiente ciclo.
        switchMap(() =>
          this.service.listaEspera().pipe(
            catchError((e: HttpErrorResponse) => {
              this.error.set(mensajeError(e, 'No fue posible actualizar la lista de espera.'));
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((lista) => {
        this.lista.set(lista);
        this.error.set(null);
        this.actualizado.set(new Date());
        this.ahora.set(Date.now());
      });
  }

  /** Minutos desde que llegó (o desde que quedó listo, en espera de consulta). */
  protected espera(e: Episodio): string {
    const desde = new Date(e.fechaListo ?? e.fechaIngreso).getTime();
    const minutos = Math.max(0, Math.floor((this.ahora() - desde) / 60_000));
    if (minutos < 60) return `${minutos} min`;
    return `${Math.floor(minutos / 60)} h ${minutos % 60} min`;
  }

  /** Triage II debe atenderse en máximo 30 minutos (Resolución 5596 de 2015). */
  protected demorado(e: Episodio): boolean {
    const desde = new Date(e.fechaListo ?? e.fechaIngreso).getTime();
    const minutos = (this.ahora() - desde) / 60_000;
    return (e.triage?.nivelAsignado ?? 9) <= 2 && minutos > 30;
  }
}
