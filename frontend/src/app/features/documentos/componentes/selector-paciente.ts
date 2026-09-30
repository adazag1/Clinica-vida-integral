import { Component, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged, switchMap } from 'rxjs';

import { PacienteResumen } from '../../pacientes/pacientes.models';
import { PacientesService } from '../../pacientes/pacientes.service';

/** Busca un paciente por documento o nombre y lo devuelve al elegirlo. */
@Component({
  selector: 'app-selector-paciente',
  imports: [FormsModule],
  template: `
    @if (elegido(); as p) {
      <div class="elegido">
        <span class="icono">person</span>
        <span><strong>{{ p.nombreCompleto }}</strong><small>{{ p.tipoDocumento }} {{ p.numeroDocumento }}</small></span>
        <button type="button" class="enlace" (click)="limpiar()">Cambiar</button>
      </div>
    } @else {
      <input class="control" [ngModel]="texto()" (ngModelChange)="buscar($event)" placeholder="Documento o nombre del paciente"
        autocomplete="off" aria-label="Buscar paciente" />
      @if (resultados().length) {
        <ul class="resultados">
          @for (p of resultados(); track p.id) {
            <li><button type="button" (click)="elegir(p)">{{ p.nombreCompleto }} <small>{{ p.tipoDocumento }} {{ p.numeroDocumento }}</small></button></li>
          }
        </ul>
      }
    }
  `,
  styles: `
    .elegido { display: flex; align-items: center; gap: 10px; padding: 8px 10px; border: 1px solid var(--c-borde); border-radius: 8px; }
    .elegido > span:nth-child(2) { flex: 1; display: flex; flex-direction: column; }
    .elegido small, .resultados small { color: var(--c-texto-2); }
    .resultados { list-style: none; margin: 4px 0 0; padding: 4px; border: 1px solid var(--c-borde); border-radius: 8px; max-height: 180px; overflow: auto; }
    .resultados button { width: 100%; text-align: left; padding: 6px 8px; border: 0; background: none; font: inherit; cursor: pointer; border-radius: 6px; }
    .resultados button:hover { background: var(--c-azul-suave); }
  `,
})
export class SelectorPaciente {
  private readonly pacientes = inject(PacientesService);

  readonly seleccion = output<PacienteResumen | null>();

  protected readonly texto = signal('');
  protected readonly resultados = signal<PacienteResumen[]>([]);
  protected readonly elegido = signal<PacienteResumen | null>(null);
  private readonly busqueda = new Subject<string>();

  constructor() {
    this.busqueda
      .pipe(debounceTime(250), distinctUntilChanged(), switchMap((t) => this.pacientes.buscar({ texto: t }, 0, 8)))
      .subscribe((p) => this.resultados.set(p.content));
  }

  protected buscar(t: string): void {
    this.texto.set(t);
    if (t.trim().length >= 3) this.busqueda.next(t.trim());
    else this.resultados.set([]);
  }

  protected elegir(p: PacienteResumen): void {
    this.elegido.set(p);
    this.resultados.set([]);
    this.seleccion.emit(p);
  }

  protected limpiar(): void {
    this.elegido.set(null);
    this.texto.set('');
    this.seleccion.emit(null);
  }
}
