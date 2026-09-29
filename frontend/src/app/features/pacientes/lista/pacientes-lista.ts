import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { mensajeError } from '../../../core/api/api';
import { AuthService } from '../../../core/auth/auth.service';
import { FiltrosPaciente, PacienteResumen } from '../pacientes.models';
import { PacientesService } from '../pacientes.service';

/** RFADM-002 Búsqueda avanzada de pacientes. */
@Component({
  selector: 'app-pacientes-lista',
  imports: [FormsModule, RouterLink, DatePipe],
  templateUrl: './pacientes-lista.html',
  styleUrl: './pacientes-lista.scss',
})
export class PacientesLista {
  private readonly service = inject(PacientesService);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);

  /** Búsqueda que llega del buscador superior (?texto=...). */
  readonly texto = input<string>();

  protected filtros: FiltrosPaciente = {};
  protected readonly avanzada = signal(false);
  protected readonly resultados = signal<PacienteResumen[] | null>(null);
  protected readonly total = signal(0);
  protected readonly pagina = signal(0);
  protected readonly totalPaginas = signal(0);
  protected readonly cargando = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    effect(() => {
      const texto = this.texto();
      if (texto) {
        this.filtros = { texto };
        this.avanzada.set(false);
        this.buscar(0);
      }
    });
  }

  protected get hayFiltros(): boolean {
    return Object.values(this.filtros).some((v) => v?.trim());
  }

  protected buscar(pagina: number): void {
    if (!this.hayFiltros) {
      this.error.set('Escriba al menos un criterio de búsqueda.');
      return;
    }
    this.cargando.set(true);
    this.error.set(null);
    this.service.buscar(this.filtros, pagina).subscribe({
      next: (res) => {
        this.resultados.set(res.content);
        this.total.set(res.page.totalElements);
        this.pagina.set(res.page.number);
        this.totalPaginas.set(res.page.totalPages);
        this.cargando.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.error.set(mensajeError(e, 'No fue posible buscar pacientes.'));
        this.cargando.set(false);
      },
    });
  }

  protected limpiar(): void {
    this.filtros = {};
    this.resultados.set(null);
    this.error.set(null);
    this.router.navigate([], { queryParams: {} });
  }

  protected cambiarModo(): void {
    this.avanzada.set(!this.avanzada());
    this.filtros = {};
  }
}
