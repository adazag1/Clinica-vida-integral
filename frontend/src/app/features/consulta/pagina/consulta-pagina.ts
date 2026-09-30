import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged, switchMap, catchError, of } from 'rxjs';

import { codigoError, mensajeError } from '../../../core/api/api';
import { AuthService } from '../../../core/auth/auth.service';
import { AlertasIaPanel } from '../../ia/componentes/alertas-ia';
import { OrdenesConsulta } from '../componentes/ordenes-consulta';
import { OrdenesDiagnosticas } from '../componentes/ordenes-diagnosticas';
import { OrdenesEnfermeria } from '../componentes/ordenes-enfermeria';
import { ResumenClinicoPanel } from '../componentes/resumen-clinico';
import { TarjetasSignos } from '../componentes/tarjetas-signos';
import {
  Consulta,
  Diagnostico,
  DiagnosticoConsulta,
  Medicamento,
  ResumenClinico,
  Signos,
  SignosRequest,
  TIPOS_ANTECEDENTE,
  TipoAntecedente,
  VerificacionFirma,
} from '../consulta.models';
import { ConsultaService } from '../consulta.service';

/** Pantalla "Consulta Medicina General" del mockup (RFMT-002, 003, 007, 010, 011). */
@Component({
  selector: 'app-consulta-pagina',
  imports: [DatePipe, FormsModule, RouterLink, ResumenClinicoPanel, TarjetasSignos, OrdenesConsulta, OrdenesEnfermeria, OrdenesDiagnosticas,
    AlertasIaPanel],
  templateUrl: './consulta-pagina.html',
  styleUrl: './consulta-pagina.scss',
})
export class ConsultaPagina implements OnInit {
  private readonly service = inject(ConsultaService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  readonly id = input.required<string>();

  protected readonly consulta = signal<Consulta | null>(null);
  protected readonly resumen = signal<ResumenClinico | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  protected readonly guardando = signal(false);
  protected readonly sinGuardar = signal(false);
  /** Sube con cada dato nuevo (signos, firma) para que el panel de IA se reevalúe. */
  protected readonly versionIa = signal(0);

  /** Notas editables (se guardan con "Guardar" o antes de firmar). */
  protected notas = { motivoConsulta: '', enfermedadActual: '', examenFisico: '', plan: '' };
  protected readonly diagnosticos = signal<DiagnosticoConsulta[]>([]);

  /** Las órdenes a enfermería siguen vivas después de firmar (paciente en observación u hospitalizado). */
  protected readonly esMedico = computed(() => this.auth.usuario()?.rol === 'MEDICO');
  protected readonly editable = computed(() => {
    const c = this.consulta();
    return !!c && c.estado === 'EN_CURSO' && c.medico.toLowerCase() === this.auth.usuario()?.username.toLowerCase();
  });

  // ---- búsqueda CIE-10 ----
  protected textoDx = '';
  private readonly busquedaDx = new Subject<string>();
  protected readonly resultadosDx = signal<Diagnostico[]>([]);

  // ---- fórmula ----
  protected textoMed = '';
  private readonly busquedaMed = new Subject<string>();
  protected readonly resultadosMed = signal<Medicamento[]>([]);
  protected medicamento: Medicamento | null = null;
  protected receta = { dosis: '', frecuencia: '', duracionDias: 5, indicaciones: '' };
  protected readonly errorReceta = signal<string | null>(null);
  protected readonly alergiaBloqueante = signal<string | null>(null);

  // ---- signos vitales ----
  protected readonly editandoSignos = signal(false);
  protected nuevosSignos: SignosRequest = this.signosVacios();
  protected readonly errorSignos = signal<string | null>(null);

  // ---- antecedente rápido ----
  protected readonly tiposAntecedente = TIPOS_ANTECEDENTE;
  protected readonly agregandoAntecedente = signal(false);
  protected antecedente = { tipo: 'ALERGIA' as TipoAntecedente, descripcion: '', detalle: '', severidad: 'MODERADA' };
  protected readonly errorAntecedente = signal<string | null>(null);

  // ---- firma ----
  protected readonly firmando = signal(false);
  protected password = '';
  protected readonly errorFirma = signal<string | null>(null);
  protected readonly verificacion = signal<VerificacionFirma | null>(null);

  protected readonly ultimosSignos = computed<Signos | null>(() => {
    const c = this.consulta();
    return c?.signos[0] ?? this.resumen()?.ultimosSignos ?? null;
  });

  ngOnInit(): void {
    this.cargar();
    this.busquedaDx
      .pipe(debounceTime(250), distinctUntilChanged(),
        switchMap((t) => (t.trim().length < 2 ? of([]) : this.service.diagnosticos(t).pipe(catchError(() => of([]))))),
        takeUntilDestroyed(this.destroyRef))
      .subscribe((r) => this.resultadosDx.set(r));
    this.busquedaMed
      .pipe(debounceTime(250), distinctUntilChanged(),
        switchMap((t) => (t.trim().length < 2 ? of([]) : this.service.medicamentos(t).pipe(catchError(() => of([]))))),
        takeUntilDestroyed(this.destroyRef))
      .subscribe((r) => this.resultadosMed.set(r));
  }

  private cargar(): void {
    this.service.obtener(+this.id()).subscribe({
      next: (c) => {
        this.aplicar(c, true);
        this.cargarResumen(c.paciente.id);
      },
      error: (e: HttpErrorResponse) => this.error.set(mensajeError(e, 'No fue posible cargar la consulta.')),
    });
  }

  private cargarResumen(pacienteId: number): void {
    this.service.resumen(pacienteId).subscribe({ next: (r) => this.resumen.set(r) });
  }

  /** Con {@code notasTambien} se reemplaza lo digitado; si no, solo fórmula, signos y pendientes. */
  private aplicar(c: Consulta, notasTambien: boolean): void {
    this.consulta.set(c);
    if (notasTambien) {
      this.notas = {
        motivoConsulta: c.motivoConsulta ?? '',
        enfermedadActual: c.enfermedadActual ?? '',
        examenFisico: c.examenFisico ?? '',
        plan: c.plan ?? '',
      };
      this.diagnosticos.set(c.diagnosticos);
      this.sinGuardar.set(false);
    }
  }

  protected marcarCambio(): void {
    this.sinGuardar.set(true);
    this.aviso.set(null);
  }

  // ---------- Diagnósticos ----------

  protected buscarDx(texto: string): void {
    this.busquedaDx.next(texto);
  }

  protected agregarDx(d: Diagnostico): void {
    if (!this.diagnosticos().some((x) => x.diagnosticoId === d.id)) {
      const principal = this.diagnosticos().length === 0;
      this.diagnosticos.update((l) => [...l, { diagnosticoId: d.id, codigo: d.codigo, nombre: d.nombre, principal, confirmado: false }]);
      this.marcarCambio();
    }
    this.textoDx = '';
    this.resultadosDx.set([]);
  }

  protected hacerPrincipal(id: number): void {
    this.diagnosticos.update((l) => l.map((d) => ({ ...d, principal: d.diagnosticoId === id })));
    this.marcarCambio();
  }

  protected alternarConfirmado(id: number): void {
    this.diagnosticos.update((l) => l.map((d) => (d.diagnosticoId === id ? { ...d, confirmado: !d.confirmado } : d)));
    this.marcarCambio();
  }

  protected quitarDx(id: number): void {
    this.diagnosticos.update((l) => l.filter((d) => d.diagnosticoId !== id));
    this.marcarCambio();
  }

  // ---------- Guardar y firmar ----------

  protected guardar(despues?: () => void): void {
    const c = this.consulta();
    if (!c) return;
    this.guardando.set(true);
    this.error.set(null);
    const nulo = (s: string) => (s.trim() ? s.trim() : null);
    this.service
      .guardar(c.id, {
        motivoConsulta: nulo(this.notas.motivoConsulta),
        enfermedadActual: nulo(this.notas.enfermedadActual),
        examenFisico: nulo(this.notas.examenFisico),
        plan: nulo(this.notas.plan),
        diagnosticos: this.diagnosticos().map((d) => ({ diagnosticoId: d.diagnosticoId, principal: d.principal, confirmado: d.confirmado })),
      })
      .subscribe({
        next: (actual) => {
          this.guardando.set(false);
          this.aplicar(actual, true);
          if (despues) despues();
          else this.aviso.set('Cambios guardados.');
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          this.error.set(mensajeError(e, 'No fue posible guardar.'));
        },
      });
  }

  /** Guarda lo digitado y, si no falta nada, pide la contraseña para firmar. */
  protected prepararFirma(): void {
    this.guardar(() => {
      const pendientes = this.consulta()?.pendientes ?? [];
      if (pendientes.length) {
        this.error.set('No se puede finalizar. Falta: ' + pendientes.join(', ') + '.');
        return;
      }
      this.password = '';
      this.errorFirma.set(null);
      this.firmando.set(true);
    });
  }

  protected firmar(): void {
    const c = this.consulta();
    if (!c || !this.password) return;
    this.guardando.set(true);
    this.service.finalizar(c.id, this.password).subscribe({
      next: (firmada) => {
        this.guardando.set(false);
        this.firmando.set(false);
        this.password = '';
        this.aplicar(firmada, true);
        this.aviso.set('Consulta firmada y finalizada.');
        this.versionIa.update((v) => v + 1);
        this.cargarResumen(firmada.paciente.id);
      },
      error: (e: HttpErrorResponse) => {
        this.guardando.set(false);
        this.errorFirma.set(mensajeError(e, 'No fue posible firmar.'));
      },
    });
  }

  protected verificar(): void {
    const c = this.consulta();
    if (c) this.service.verificarFirma(c.id).subscribe({ next: (v) => this.verificacion.set(v) });
  }

  // ---------- Fórmula (RFMT-003) ----------

  protected buscarMed(texto: string): void {
    this.medicamento = null;
    this.busquedaMed.next(texto);
  }

  protected elegirMed(m: Medicamento): void {
    this.medicamento = m;
    this.textoMed = m.nombre;
    this.resultadosMed.set([]);
    this.alergiaBloqueante.set(null);
  }

  protected agregarMedicamento(): void {
    const c = this.consulta();
    const m = this.medicamento;
    this.errorReceta.set(null);
    this.alergiaBloqueante.set(null);
    if (!c || !m) {
      this.errorReceta.set('Seleccione un medicamento del catálogo.');
      return;
    }
    if (!this.receta.dosis.trim() || !this.receta.frecuencia.trim() || !this.receta.duracionDias) {
      this.errorReceta.set('Complete dosis, frecuencia y duración.');
      return;
    }
    this.service
      .agregarPrescripcion(c.id, {
        medicamentoId: m.id,
        dosis: this.receta.dosis.trim(),
        frecuencia: this.receta.frecuencia.trim(),
        duracionDias: +this.receta.duracionDias,
        indicaciones: this.receta.indicaciones.trim() || null,
      })
      .subscribe({
        next: (actual) => {
          this.aplicar(actual, false);
          this.medicamento = null;
          this.textoMed = '';
          this.receta = { dosis: '', frecuencia: '', duracionDias: 5, indicaciones: '' };
        },
        error: (e: HttpErrorResponse) => {
          const msg = mensajeError(e, 'No fue posible agregar el medicamento.');
          if (codigoError(e) === 'ALERGIA_MEDICAMENTO') this.alergiaBloqueante.set(msg);
          else this.errorReceta.set(msg);
        },
      });
  }

  protected quitarMedicamento(prescripcionId: number): void {
    const c = this.consulta();
    if (!c) return;
    this.service.quitarPrescripcion(c.id, prescripcionId).subscribe({
      next: (actual) => this.aplicar(actual, false),
      error: (e: HttpErrorResponse) => this.errorReceta.set(mensajeError(e, 'No fue posible quitar el medicamento.')),
    });
  }

  // ---------- Signos vitales (RFMT-010) ----------

  private signosVacios(): SignosRequest {
    return { consultaId: null, presionSistolica: null, presionDiastolica: null, frecuenciaCardiaca: null,
      frecuenciaRespiratoria: null, temperatura: null, saturacionOxigeno: null, peso: null, talla: null };
  }

  protected guardarSignos(): void {
    const c = this.consulta();
    if (!c) return;
    this.errorSignos.set(null);
    const datos = { ...this.nuevosSignos, consultaId: c.id };
    for (const k of Object.keys(datos) as (keyof SignosRequest)[]) {
      if ((datos[k] as unknown) === '' ) (datos as Record<string, unknown>)[k] = null;
    }
    this.service.registrarSignos(c.paciente.id, datos).subscribe({
      next: () => {
        this.editandoSignos.set(false);
        this.nuevosSignos = this.signosVacios();
        this.versionIa.update((v) => v + 1);
        this.service.obtener(c.id).subscribe((actual) => this.aplicar(actual, false));
        this.cargarResumen(c.paciente.id);
      },
      error: (e: HttpErrorResponse) => {
        const campos = (e.error as { campos?: Record<string, string> } | null)?.campos;
        this.errorSignos.set(campos && Object.keys(campos).length
          ? Object.values(campos).join('. ')
          : mensajeError(e, 'No fue posible registrar los signos.'));
      },
    });
  }

  // ---------- Antecedentes ----------

  protected guardarAntecedente(): void {
    const c = this.consulta();
    const a = this.antecedente;
    if (!c || !a.descripcion.trim()) {
      this.errorAntecedente.set('Ingrese la descripción.');
      return;
    }
    this.service
      .agregarAntecedente(c.paciente.id, {
        tipo: a.tipo,
        descripcion: a.descripcion.trim(),
        detalle: a.detalle.trim() || null,
        severidad: a.tipo === 'ALERGIA' ? a.severidad : null,
      })
      .subscribe({
        next: () => {
          this.agregandoAntecedente.set(false);
          this.antecedente = { tipo: 'ALERGIA', descripcion: '', detalle: '', severidad: 'MODERADA' };
          this.errorAntecedente.set(null);
          this.cargarResumen(c.paciente.id);
        },
        error: (e: HttpErrorResponse) => this.errorAntecedente.set(mensajeError(e, 'No fue posible registrar el antecedente.')),
      });
  }
}
