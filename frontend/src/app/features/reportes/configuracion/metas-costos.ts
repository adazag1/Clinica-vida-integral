import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';

import { erroresCampos, mensajeError } from '../../../core/api/api';
import { mesLargo } from '../componentes/grafico-serie';
import { Categoria, Parametro, pesos } from '../reportes.models';
import { ReportesService } from '../reportes.service';

interface FilaCosto {
  categoria: Categoria;
  valor: number | null;
  nota: string;
  registradoPor: string | null;
  original: number | null;
}

/** Líneas base, metas y supuestos del ROI, y registro de los costos operativos de cada mes. */
@Component({
  selector: 'app-metas-costos',
  imports: [DatePipe, FormsModule],
  template: `
    @if (aviso()) { <div class="alerta alerta--exito" role="status"><span class="icono">check_circle</span>{{ aviso() }}</div> }
    @if (error()) { <div class="alerta alerta--error" role="alert"><span class="icono">error</span>{{ error() }}</div> }

    <section class="tarjeta">
      <div class="titulo-accion">
        <div class="tarjeta__titulo"><span class="icono">payments</span>Costos operativos del mes</div>
        <label class="mes">Mes <input class="control" type="month" [(ngModel)]="mes" (ngModelChange)="cargarCostos()" [max]="mesActual" /></label>
      </div>
      <p class="nota">Registre lo que costó cada concepto en {{ mesLargo(mes) }}. Las categorías administrativas son las que la meta
        del CFO espera reducir; «Plataforma» es el costo nuevo (nube, licencias, soporte) y se descuenta del beneficio.</p>
      <div class="tabla-contenedor">
        <table class="tabla">
          <thead><tr><th>Categoría</th><th class="num">Valor (COP)</th><th>Nota</th><th></th></tr></thead>
          <tbody>
            @for (f of filas(); track f.categoria.valor) {
              <tr>
                <td>{{ f.categoria.descripcion }}
                  @if (!f.categoria.administrativa) { <span class="chip chip--info">Costo nuevo</span> }</td>
                <td class="num"><input class="control control--num" type="number" min="0" step="1000" [(ngModel)]="f.valor"
                  [attr.aria-label]="'Valor de ' + f.categoria.descripcion" /></td>
                <td><input class="control" [(ngModel)]="f.nota" maxlength="300" placeholder="Opcional" [attr.aria-label]="'Nota de ' + f.categoria.descripcion" /></td>
                <td class="nowrap"><small>{{ f.registradoPor ? 'Registró ' + f.registradoPor : '' }}</small></td>
              </tr>
            }
          </tbody>
          <tfoot><tr><th>Total administrativo</th><th class="num">{{ pesos(totalAdministrativo()) }}</th><th colspan="2"></th></tr></tfoot>
        </table>
      </div>
      <div class="acciones">
        <button type="button" class="btn btn--primario btn--pequeno" (click)="guardarCostos()" [disabled]="guardando()">
          <span class="icono">save</span>Guardar costos de {{ mesLargo(mes) }}</button>
      </div>
    </section>

    <section class="tarjeta">
      <div class="tarjeta__titulo"><span class="icono">flag</span>Líneas base y metas</div>
      <p class="nota">Los valores marcados «de prueba» vienen con el sistema: reemplácelos por los medidos en la clínica. Cada cambio
        queda con usuario y fecha.</p>
      <div class="tabla-contenedor">
        <table class="tabla">
          <thead><tr><th>Indicador</th><th>Valor</th><th>Unidad</th><th>Último cambio</th><th></th></tr></thead>
          <tbody>
            @for (p of parametros(); track p.clave) {
              <tr>
                <td>{{ p.descripcion }} @if (p.prueba) { <span class="chip chip--alerta">De prueba</span> }</td>
                <td>
                  @if (editando() === p.clave) {
                    <input class="control control--num" [(ngModel)]="valorEditado" [attr.aria-label]="p.descripcion"
                      [type]="p.unidad === 'fecha' ? 'date' : 'text'" (keydown.enter)="guardarParametro(p)" />
                    @if (errorParametro()) { <p class="campo__error">{{ errorParametro() }}</p> }
                  } @else {
                    <strong>{{ mostrar(p) }}</strong>
                  }
                </td>
                <td>{{ p.unidad }}</td>
                <td><small>{{ p.modificadoPor ? p.modificadoPor + ' · ' : '' }}{{ p.modificadoEn | date: 'dd/MM/yyyy' }}</small></td>
                <td class="nowrap">
                  @if (editando() === p.clave) {
                    <button type="button" class="enlace" (click)="guardarParametro(p)">Guardar</button> ·
                    <button type="button" class="enlace" (click)="editando.set(null)">Cancelar</button>
                  } @else {
                    <button type="button" class="enlace" (click)="editar(p)">Editar</button>
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </section>
  `,
  styles: `
    .alerta { margin-bottom: 12px; }
    section.tarjeta + section.tarjeta { margin-top: 14px; }
    .mes { display: flex; align-items: center; gap: 8px; font-size: 13px; color: var(--c-texto-2); }
    .mes .control { width: 170px; }
    .nota { font-size: 12.5px; color: var(--c-texto-2); margin: 4px 0 10px; }
    .num { text-align: right; }
    .control--num { width: 170px; text-align: right; }
    .acciones { display: flex; justify-content: flex-end; margin-top: 10px; }
    .chip { margin-left: 6px; }
    tfoot th { text-align: left; }
    tfoot .num { text-align: right; }
  `,
})
export class MetasCostos {
  private readonly service = inject(ReportesService);

  protected readonly mesLargo = mesLargo;
  protected readonly pesos = pesos;
  protected readonly mesActual = new Date().toLocaleDateString('en-CA').slice(0, 7);
  protected mes = this.mesActual;

  protected readonly filas = signal<FilaCosto[]>([]);
  protected readonly parametros = signal<Parametro[]>([]);
  protected readonly editando = signal<string | null>(null);
  protected readonly errorParametro = signal<string | null>(null);
  protected valorEditado = '';
  protected readonly guardando = signal(false);
  protected readonly aviso = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  private categorias: Categoria[] = [];

  constructor() {
    this.service.categorias().subscribe((c) => {
      this.categorias = c;
      this.cargarCostos();
    });
    this.cargarParametros();
  }

  protected cargarCostos(): void {
    if (!this.mes) return;
    this.service.costos(this.mes, this.mes).subscribe({
      next: (costos) =>
        this.filas.set(
          this.categorias.map((cat) => {
            const c = costos.find((x) => x.categoria === cat.valor);
            return { categoria: cat, valor: c?.valor ?? null, nota: c?.nota ?? '', registradoPor: c?.registradoPor ?? null, original: c?.valor ?? null };
          }),
        ),
      error: (e: HttpErrorResponse) => this.error.set(mensajeError(e, 'No fue posible cargar los costos.')),
    });
  }

  protected totalAdministrativo(): number {
    return this.filas().filter((f) => f.categoria.administrativa).reduce((s, f) => s + (Number(f.valor) || 0), 0);
  }

  /** Guarda solo las categorías con valor; dejar una en blanco no borra lo que ya estaba. */
  protected guardarCostos(): void {
    const aGuardar = this.filas().filter((f) => f.valor !== null && `${f.valor}` !== '');
    if (!aGuardar.length) {
      this.error.set('Ingrese al menos un valor.');
      return;
    }
    this.guardando.set(true);
    this.error.set(null);
    forkJoin(aGuardar.map((f) => this.service.registrarCosto(this.mes, f.categoria.valor, Number(f.valor), f.nota.trim() || null))).subscribe({
      next: () => {
        this.guardando.set(false);
        this.aviso.set(`Costos de ${mesLargo(this.mes)} guardados.`);
        this.cargarCostos();
      },
      error: (e: HttpErrorResponse) => {
        this.guardando.set(false);
        const campos = erroresCampos(e);
        this.error.set(Object.keys(campos).length ? Object.values(campos).join('. ') : mensajeError(e, 'No fue posible guardar los costos.'));
      },
    });
  }

  private cargarParametros(): void {
    this.service.parametros().subscribe({ next: (p) => this.parametros.set(p) });
  }

  protected editar(p: Parametro): void {
    this.valorEditado = p.valor;
    this.errorParametro.set(null);
    this.editando.set(p.clave);
  }

  protected guardarParametro(p: Parametro): void {
    this.service.cambiarParametro(p.clave, this.valorEditado).subscribe({
      next: (nuevo) => {
        this.parametros.update((l) => l.map((x) => (x.clave === nuevo.clave ? nuevo : x)));
        this.editando.set(null);
        this.aviso.set(`«${nuevo.descripcion}» actualizado.`);
      },
      error: (e: HttpErrorResponse) => this.errorParametro.set(erroresCampos(e)['valor'] ?? mensajeError(e, 'No fue posible guardar.')),
    });
  }

  protected mostrar(p: Parametro): string {
    if (p.unidad === 'COP' || p.unidad === 'USD') return Number(p.valor).toLocaleString('es-CO');
    if (p.unidad === 'fecha') return new Date(p.valor + 'T00:00:00').toLocaleDateString('es-CO');
    return p.valor.replaceAll(',', ', ');
  }
}
