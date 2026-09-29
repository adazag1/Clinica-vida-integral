import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { mensajeError } from '../../../core/api/api';
import { Bloqueo, Catalogos, DIAS_SEMANA, FranjaHorario, MOTIVOS_BLOQUEO } from '../agenda.models';
import { AgendaService } from '../agenda.service';

/** RFAGE-015 horarios de atención y RFAGE-014 bloqueos por ausencias. */
@Component({
  selector: 'app-configuracion-agenda',
  imports: [FormsModule, RouterLink, DatePipe],
  templateUrl: './configuracion-agenda.html',
  styleUrl: './configuracion-agenda.scss',
})
export class ConfiguracionAgenda {
  private readonly service = inject(AgendaService);

  protected readonly dias = DIAS_SEMANA;
  protected readonly motivos = MOTIVOS_BLOQUEO;
  protected readonly catalogos = signal<Catalogos | null>(null);
  protected profesionalId = 0;
  protected readonly franjas = signal<FranjaHorario[]>([]);
  protected readonly bloqueos = signal<Bloqueo[]>([]);
  protected readonly errorHorario = signal<string | null>(null);
  protected readonly avisoHorario = signal<string | null>(null);
  protected readonly errorBloqueo = signal<string | null>(null);
  protected readonly guardando = signal(false);

  protected nuevoBloqueo = { profesionalId: 0, desde: '', hasta: '', motivo: 'VACACIONES', descripcion: '' };

  protected readonly porDia = computed(() =>
    [1, 2, 3, 4, 5, 6, 7].map((dia) => ({
      dia,
      franjas: this.franjas()
        .map((f, indice) => ({ ...f, indice }))
        .filter((f) => f.dia === dia)
        .sort((a, b) => a.horaInicio.localeCompare(b.horaInicio)),
    })),
  );

  constructor() {
    this.service.catalogos().subscribe((c) => {
      this.catalogos.set(c);
      if (c.profesionales.length) {
        this.profesionalId = c.profesionales[0].id;
        this.nuevoBloqueo.profesionalId = c.profesionales[0].id;
        this.cargarHorario();
      }
    });
    this.cargarBloqueos();
  }

  protected nombre(profesionalId: number): string {
    return this.catalogos()?.profesionales.find((p) => p.id === profesionalId)?.nombre ?? '';
  }

  protected cargarHorario(): void {
    this.avisoHorario.set(null);
    this.errorHorario.set(null);
    this.service.horario(+this.profesionalId).subscribe({
      next: (f) => this.franjas.set(f.map((x) => ({ ...x, horaInicio: x.horaInicio.slice(0, 5), horaFin: x.horaFin.slice(0, 5) }))),
      error: (e: HttpErrorResponse) => this.errorHorario.set(mensajeError(e, 'No fue posible cargar el horario.')),
    });
  }

  protected agregarFranja(dia: number): void {
    this.franjas.update((l) => [...l, { dia, horaInicio: '08:00', horaFin: '12:00', tipo: 'NORMAL' }]);
  }

  protected quitarFranja(indice: number): void {
    this.franjas.update((l) => l.filter((_, i) => i !== indice));
  }

  protected actualizar(indice: number, campo: keyof FranjaHorario, valor: string): void {
    this.franjas.update((l) => l.map((f, i) => (i === indice ? { ...f, [campo]: valor } : f)));
  }

  protected guardarHorario(): void {
    this.guardando.set(true);
    this.errorHorario.set(null);
    this.avisoHorario.set(null);
    this.service.guardarHorario(+this.profesionalId, this.franjas()).subscribe({
      next: () => {
        this.guardando.set(false);
        this.avisoHorario.set('Horario guardado. Las citas ya agendadas no cambian.');
        this.cargarHorario();
      },
      error: (e: HttpErrorResponse) => {
        this.guardando.set(false);
        this.errorHorario.set(mensajeError(e, 'No fue posible guardar el horario.'));
      },
    });
  }

  private cargarBloqueos(): void {
    this.service.bloqueos().subscribe({ next: (b) => this.bloqueos.set(b) });
  }

  protected bloquear(): void {
    const b = this.nuevoBloqueo;
    if (!b.desde || !b.hasta) {
      this.errorBloqueo.set('Indique desde y hasta cuándo.');
      return;
    }
    this.errorBloqueo.set(null);
    this.service
      .bloquear({
        profesionalId: +b.profesionalId,
        inicio: new Date(b.desde).toISOString(),
        fin: new Date(b.hasta).toISOString(),
        motivo: b.motivo,
        descripcion: b.descripcion.trim() || null,
      })
      .subscribe({
        next: () => {
          this.nuevoBloqueo = { ...b, desde: '', hasta: '', descripcion: '' };
          this.cargarBloqueos();
        },
        error: (e: HttpErrorResponse) => this.errorBloqueo.set(mensajeError(e, 'No fue posible bloquear la agenda.')),
      });
  }

  protected desbloquear(b: Bloqueo): void {
    if (!confirm('¿Quitar este bloqueo? La agenda volverá a aceptar citas en ese periodo.')) return;
    this.service.desbloquear(b.id).subscribe({ next: () => this.cargarBloqueos() });
  }
}
