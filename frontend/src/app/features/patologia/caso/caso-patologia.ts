import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { codigoError, erroresCampos, mensajeError } from '../../../core/api/api';
import { AuthService } from '../../../core/auth/auth.service';
import { clasePrioridad } from '../../imagenes/imagenes.models';
import {
  CampoPlantilla,
  Caso,
  MATERIALES,
  MOVIMIENTOS,
  PlantillaPatologia,
  VersionInforme,
  claseEstadoCaso,
} from '../patologia.models';
import { PatologiaService } from '../patologia.service';

/** Caso de patología de principio a fin, en una sola pantalla que sigue el estado del caso (RNFPAT-002). */
@Component({
  selector: 'app-caso-patologia',
  imports: [DatePipe, FormsModule, RouterLink],
  templateUrl: './caso-patologia.html',
  styleUrl: './caso-patologia.scss',
})
export class CasoPatologia {
  private readonly service = inject(PatologiaService);
  private readonly auth = inject(AuthService);

  /** Viene de la ruta /patologia/casos/:id. */
  readonly id = input.required<string>();

  protected readonly clasePrioridad = clasePrioridad;
  protected readonly claseEstado = claseEstadoCaso;
  protected readonly materiales = MATERIALES;
  protected readonly movimientos = MOVIMIENTOS;
  protected readonly pasos = ['SOLICITADO', 'RECIBIDO', 'MACROSCOPIA', 'INFORMADO'];
  protected readonly nombresPasos = ['Solicitud', 'Recepción', 'Macroscopía', 'Microscopía e informe'];

  protected readonly caso = signal<Caso | null>(null);
  protected readonly plantillas = signal<PlantillaPatologia[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  protected readonly errores = signal<Record<string, string>>({});
  protected readonly ocupado = signal(false);
  protected readonly validando = signal(false);
  protected readonly errorValidacion = signal<string | null>(null);
  protected readonly corrigiendo = signal(false);

  /** Patología (técnicos y patólogos) recibe y describe; solo el patólogo informa y valida. */
  protected readonly esPatologia = computed(() => this.auth.tieneRol('PATOLOGIA'));
  protected readonly esPatologo = computed(() => this.auth.usuario()?.rol === 'PATOLOGIA');
  protected readonly camposMacro = computed(() => this.caso()?.plantilla?.campos.filter((c) => c.seccion === 'MACRO') ?? []);
  protected readonly camposMicro = computed(() => this.caso()?.plantilla?.campos.filter((c) => c.seccion === 'MICRO') ?? []);
  protected readonly macroEditable = computed(() => {
    const e = this.caso()?.resumen.estado;
    return this.esPatologia() && (e === 'RECIBIDO' || e === 'MACROSCOPIA');
  });
  protected readonly microEditable = computed(() => {
    const c = this.caso();
    if (!c || !this.esPatologo()) return false;
    return c.resumen.estado === 'MACROSCOPIA' || (c.resumen.estado === 'INFORMADO' && (this.corrigiendo() || c.cambiosSinValidar));
  });
  protected readonly plantillasDelTipo = computed(() => this.plantillas().filter((p) => p.tipo === this.caso()?.resumen.tipo));

  protected recepcion = { documentoRotulo: '', muestrasRecibidas: 1, plantillaId: null as number | null, observacion: '' };
  protected macro: Record<string, string> = {};
  protected micro: Record<string, string> = {};
  protected diagnostico = '';
  protected password = '';
  protected motivoCambio = '';
  protected mov = { material: 'BLOQUE', accion: 'ARCHIVADO', cantidad: 1, ubicacion: '', destinatario: '', motivo: '' };

  constructor() {
    effect(() => this.cargar(Number(this.id())));
    this.service.plantillas().subscribe({ next: (p) => this.plantillas.set(p), error: () => undefined });
  }

  private cargar(id: number): void {
    this.service.obtener(id).subscribe({
      next: (c) => this.mostrar(c),
      error: (e: HttpErrorResponse) => this.error.set(mensajeError(e, 'No fue posible cargar el caso.')),
    });
  }

  private mostrar(c: Caso): void {
    this.caso.set(c);
    this.error.set(null);
    this.errores.set({});
    this.macro = { ...c.macroscopia };
    this.micro = { ...c.microscopia };
    this.diagnostico = c.diagnostico ?? '';
    this.recepcion.muestrasRecibidas = c.numeroMuestras;
  }

  protected paso(): number {
    return this.pasos.indexOf(this.caso()?.resumen.estado ?? 'SOLICITADO');
  }

  protected valor(c: CampoPlantilla, valores: Record<string, string>): string {
    const v = valores[c.clave];
    return v ? v + (c.unidad ? ' ' + c.unidad : '') : '—';
  }

  /** Campos que cambiaron frente a la versión anterior (RFPAT-007). */
  protected cambios(v: VersionInforme, i: number): string[] {
    const previa = this.caso()?.versiones[i + 1];
    if (!previa) return [];
    const campos = this.caso()?.plantilla?.campos ?? [];
    const lista = campos.filter((c) => (v.macroscopia[c.clave] ?? '') !== (previa.macroscopia[c.clave] ?? '')
      || (v.microscopia[c.clave] ?? '') !== (previa.microscopia[c.clave] ?? '')).map((c) => c.etiqueta);
    if (v.diagnostico !== previa.diagnostico) lista.push('Diagnóstico');
    return lista;
  }

  protected recibir(): void {
    const c = this.caso();
    if (!c) return;
    this.ejecutar(this.service.recibir(c.resumen.id, {
      documentoRotulo: this.recepcion.documentoRotulo.trim(), muestrasRecibidas: Number(this.recepcion.muestrasRecibidas),
      plantillaId: this.recepcion.plantillaId, observacion: this.recepcion.observacion.trim() || null,
    }), 'Muestra recibida con la identificación verificada.');
  }

  protected guardarMacro(): void {
    const c = this.caso();
    if (c) this.ejecutar(this.service.macroscopia(c.resumen.id, this.macro), 'Macroscopía registrada.');
  }

  protected guardarMicro(): void {
    const c = this.caso();
    if (c) this.ejecutar(this.service.microscopia(c.resumen.id, this.micro, this.diagnostico.trim() || null), 'Borrador guardado.');
  }

  protected abrirValidacion(): void {
    const c = this.caso();
    if (!c) return;
    // Guarda lo escrito y luego pide la contraseña
    this.ejecutar(this.service.microscopia(c.resumen.id, this.micro, this.diagnostico.trim() || null), null, () => {
      this.password = '';
      this.motivoCambio = '';
      this.errorValidacion.set(null);
      this.validando.set(true);
    });
  }

  protected validar(): void {
    const c = this.caso();
    if (!c) return;
    this.ocupado.set(true);
    this.service.validar(c.resumen.id, this.password, this.motivoCambio.trim() || null).subscribe({
      next: (r) => {
        this.ocupado.set(false);
        this.validando.set(false);
        this.corrigiendo.set(false);
        this.mostrar(r);
        this.aviso.set(`Informe validado (versión ${r.versiones[0]?.version}). El médico recibió el aviso.`);
      },
      error: (e: HttpErrorResponse) => {
        this.ocupado.set(false);
        if (codigoError(e) === 'FIRMA_INVALIDA') this.password = '';
        const campos = erroresCampos(e);
        this.errorValidacion.set(Object.keys(campos).length ? Object.values(campos).join('. ') : mensajeError(e, 'No fue posible validar.'));
      },
    });
  }

  protected registrarCustodia(): void {
    const c = this.caso();
    if (!c) return;
    const m = this.mov;
    this.ejecutar(this.service.custodia(c.resumen.id, {
      material: m.material, accion: m.accion, cantidad: Number(m.cantidad), ubicacion: m.ubicacion.trim() || null,
      destinatario: m.destinatario.trim() || null, motivo: m.motivo.trim() || null,
    }), 'Movimiento registrado en la cadena de custodia.', () => (this.mov = { ...m, ubicacion: '', destinatario: '', motivo: '' }));
  }

  private ejecutar(obs: Observable<Caso>, exito: string | null, despues?: () => void): void {
    this.ocupado.set(true);
    this.aviso.set(null);
    this.error.set(null);
    obs.subscribe({
      next: (c) => {
        this.ocupado.set(false);
        this.mostrar(c);
        if (exito) this.aviso.set(exito);
        despues?.();
      },
      error: (e: HttpErrorResponse) => {
        this.ocupado.set(false);
        const campos = erroresCampos(e);
        this.errores.set(campos);
        this.error.set(Object.keys(campos).length ? Object.values(campos).join('. ') : mensajeError(e, 'No fue posible completar la acción.'));
      },
    });
  }
}
