import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { erroresCampos, mensajeError } from '../../../core/api/api';
import { AuthService } from '../../../core/auth/auth.service';
import { Signos, SignosRequest } from '../../consulta/consulta.models';
import { ConsultaService } from '../../consulta/consulta.service';
import { TarjetasSignos } from '../../consulta/componentes/tarjetas-signos';
import { claseEstadoOrden } from '../../laboratorio/laboratorio.models';
import { DialogoAdministrar } from '../componentes/dialogo-administrar';
import {
  ETIQUETA_HORARIO,
  Entrega,
  EventoAdverso,
  Horario,
  Nota,
  OrdenClinica,
  OrdenesPaciente,
  ResumenTurno,
  SEVERIDADES_EVENTO,
  TIPOS_EVENTO,
  TIPOS_ORDEN,
  claseHorario,
} from '../enfermeria.models';
import { EnfermeriaService } from '../enfermeria.service';

type Pestana = 'ordenes' | 'signos' | 'notas' | 'eventos' | 'turno';

const signosVacios = (): SignosRequest => ({
  consultaId: null, presionSistolica: null, presionDiastolica: null, frecuenciaCardiaca: null, frecuenciaRespiratoria: null,
  temperatura: null, saturacionOxigeno: null, peso: null, talla: null,
});

/** Hoja de enfermería del paciente: órdenes con horario, signos, notas, eventos adversos y entrega de turno. */
@Component({
  selector: 'app-paciente-enfermeria',
  imports: [DatePipe, FormsModule, RouterLink, TarjetasSignos, DialogoAdministrar],
  templateUrl: './paciente-enfermeria.html',
  styleUrl: './paciente-enfermeria.scss',
})
export class PacienteEnfermeria implements OnInit {
  private readonly service = inject(EnfermeriaService);
  private readonly consultas = inject(ConsultaService);
  private readonly auth = inject(AuthService);

  /** Viene de la ruta /enfermeria/pacientes/:id. */
  readonly id = input.required<string>();
  protected readonly pacienteId = computed(() => Number(this.id()));

  protected readonly pestanas: { valor: Pestana; etiqueta: string; icono: string }[] = [
    { valor: 'ordenes', etiqueta: 'Órdenes', icono: 'medication' },
    { valor: 'signos', etiqueta: 'Signos vitales', icono: 'ecg_heart' },
    { valor: 'notas', etiqueta: 'Notas', icono: 'edit_note' },
    { valor: 'eventos', etiqueta: 'Eventos adversos', icono: 'report' },
    { valor: 'turno', etiqueta: 'Entrega de turno', icono: 'swap_horiz' },
  ];
  protected readonly pestana = signal<Pestana>('ordenes');

  protected readonly etiquetaHorario = ETIQUETA_HORARIO;
  protected readonly claseHorario = claseHorario;
  protected readonly claseLab = claseEstadoOrden;
  protected readonly tiposEvento = TIPOS_EVENTO;
  protected readonly severidades = SEVERIDADES_EVENTO;

  /** Enfermería registra; el médico y auditoría consultan. */
  protected readonly puedeRegistrar = computed(() => this.auth.tieneRol('ENFERMERIA'));
  protected readonly esEnfermeria = computed(() => this.auth.usuario()?.rol === 'ENFERMERIA');

  protected readonly datos = signal<OrdenesPaciente | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  protected readonly administrando = signal<{ orden: OrdenClinica; horario: Horario } | null>(null);
  protected readonly verInactivas = signal(false);

  protected readonly ordenesVisibles = computed(() =>
    (this.datos()?.ordenes ?? []).filter((o) => this.verInactivas() || o.situacion === 'ACTIVA'),
  );
  protected readonly inactivas = computed(() => (this.datos()?.ordenes ?? []).filter((o) => o.situacion !== 'ACTIVA').length);

  // Signos
  protected readonly signos = signal<Signos[]>([]);
  protected readonly ultimaToma = computed<Signos | null>(() => this.signos().at(0) ?? null);
  protected nuevosSignos: SignosRequest = signosVacios();
  protected readonly errorSignos = signal<string | null>(null);
  protected readonly alertasSignos = signal<string[]>([]);

  // Notas
  protected readonly notas = signal<Nota[]>([]);
  protected textoNota = '';

  // Eventos
  protected readonly eventos = signal<EventoAdverso[]>([]);
  protected readonly reportando = signal(false);
  protected evento = this.eventoVacio();
  protected readonly erroresEvento = signal<Record<string, string>>({});

  // Turno
  protected readonly resumen = signal<ResumenTurno | null>(null);
  protected readonly entregas = signal<Entrega[]>([]);
  protected notaEntrega = '';
  protected readonly entregaVista = signal<number | null>(null);

  protected readonly mensajeEvento = computed(() => Object.values(this.erroresEvento()).join('. '));
  protected readonly seccionesTurno = computed(() => {
    const r = this.resumen();
    if (!r) return [];
    return [
      { t: 'Alergias', l: r.alergias.length ? r.alergias : ['Sin alergias registradas'] },
      { t: 'Diagnósticos', l: r.diagnosticos },
      { t: 'Alertas', l: r.alertas },
      { t: 'Registrado en el turno', l: r.administrado },
      { t: 'Pendiente', l: r.pendientes },
      { t: 'Órdenes', l: r.ordenesActivas },
      { t: 'Laboratorio', l: r.examenes },
      { t: 'Eventos adversos', l: r.eventos },
      { t: 'Notas', l: r.notas },
    ];
  });

  protected critico(s: Signos): boolean {
    return s.alertas.some((a) => a.nivel === 'CRITICO');
  }

  ngOnInit(): void {
    this.cargarOrdenes();
  }

  protected icono(o: OrdenClinica): string {
    return TIPOS_ORDEN.find((t) => t.valor === o.tipo)?.icono ?? 'assignment';
  }

  protected elegir(p: Pestana): void {
    this.pestana.set(p);
    this.aviso.set(null);
    const id = this.pacienteId();
    if (p === 'ordenes') this.cargarOrdenes();
    if (p === 'signos') this.consultas.signos(id).subscribe((s) => this.signos.set(s));
    if (p === 'notas') this.service.notas(id).subscribe((n) => this.notas.set(n));
    if (p === 'eventos') this.service.eventos(id).subscribe((e) => this.eventos.set(e));
    if (p === 'turno') {
      this.service.resumenTurno(id).subscribe((r) => this.resumen.set(r));
      this.service.entregas(id).subscribe((e) => this.entregas.set(e));
    }
  }

  private cargarOrdenes(): void {
    this.service.ordenesPaciente(this.pacienteId()).subscribe({
      next: (d) => {
        this.datos.set(d);
        this.error.set(null);
      },
      error: (e: HttpErrorResponse) => this.error.set(mensajeError(e, 'No fue posible cargar las órdenes del paciente.')),
    });
  }

  // ---------- Órdenes ----------

  protected ejecutable(o: OrdenClinica, h: Horario): boolean {
    return this.puedeRegistrar() && o.situacion === 'ACTIVA' && ['PENDIENTE', 'ATRASADA', 'PROGRAMADA'].includes(h.estado);
  }

  protected detalle(h: Horario): string {
    const e = h.ejecucion;
    if (!e) return this.etiquetaHorario[h.estado];
    const hora = new Date(e.realizadaEn).toLocaleTimeString('es-CO', { hour: 'numeric', minute: '2-digit' });
    return [`${e.resultadoDescripcion} a las ${hora} por ${e.usuario}`,
      e.dobleValidacionPor ? `Verificó: ${e.dobleValidacionPor}` : '',
      e.motivo ? `Motivo: ${e.motivo}` : '', e.justificacionRetraso ? `Retraso: ${e.justificacionRetraso}` : '']
      .filter(Boolean).join('\n');
  }

  protected abrir(o: OrdenClinica, h: Horario): void {
    if (h.estado === 'PROGRAMADA' && new Date(h.programadaPara).getTime() - Date.now() > 30 * 60_000) {
      this.error.set('Aún no es la hora de esta dosis: se puede registrar desde 30 minutos antes.');
      return;
    }
    this.error.set(null);
    this.administrando.set({ orden: o, horario: h });
  }

  protected registrado(o: OrdenClinica): void {
    const d = this.datos();
    if (d) this.datos.set({ ...d, ordenes: d.ordenes.map((x) => (x.id === o.id ? o : x)) });
    this.administrando.set(null);
    this.aviso.set('Registro guardado con la hora actual.');
  }

  // ---------- Signos ----------

  protected guardarSignos(): void {
    this.consultas.registrarSignos(this.pacienteId(), this.nuevosSignos).subscribe({
      next: (s) => {
        this.signos.set([s, ...this.signos()]);
        this.nuevosSignos = signosVacios();
        this.errorSignos.set(null);
        this.alertasSignos.set(s.alertas.map((a) => a.mensaje));
        this.aviso.set(s.alertas.some((a) => a.nivel === 'CRITICO')
          ? 'Signos registrados. Hay valores críticos: se avisó al médico.' : 'Signos registrados.');
      },
      error: (e: HttpErrorResponse) => {
        const campos = erroresCampos(e);
        this.errorSignos.set(Object.keys(campos).length ? Object.values(campos).join('. ') : mensajeError(e, 'No fue posible registrar.'));
      },
    });
  }

  // ---------- Notas ----------

  protected guardarNota(): void {
    if (!this.textoNota.trim()) return;
    this.service.agregarNota(this.pacienteId(), this.textoNota.trim()).subscribe({
      next: (n) => {
        this.notas.set([n, ...this.notas()]);
        this.textoNota = '';
      },
      error: (e: HttpErrorResponse) => this.error.set(mensajeError(e, 'No fue posible guardar la nota.')),
    });
  }

  // ---------- Eventos adversos ----------

  private eventoVacio() {
    const ahora = new Date();
    ahora.setMinutes(ahora.getMinutes() - ahora.getTimezoneOffset());
    return { tipo: '', severidad: '', ocurridoEn: ahora.toISOString().slice(0, 16), lugar: '', descripcion: '',
      accionesInmediatas: '', medicoNotificado: '' };
  }

  protected reportarEvento(): void {
    const ev = this.evento;
    this.service
      .reportarEvento(this.pacienteId(), {
        tipo: ev.tipo, severidad: ev.severidad, ocurridoEn: ev.ocurridoEn ? new Date(ev.ocurridoEn).toISOString() : '',
        lugar: ev.lugar.trim() || null, descripcion: ev.descripcion.trim(), accionesInmediatas: ev.accionesInmediatas.trim(),
        medicoNotificado: ev.medicoNotificado.trim() || null, ordenId: null,
      })
      .subscribe({
        next: (e) => {
          this.eventos.set([e, ...this.eventos()]);
          this.reportando.set(false);
          this.evento = this.eventoVacio();
          this.erroresEvento.set({});
          this.aviso.set(`Evento reportado. Se notificó a seguridad del paciente${['GRAVE', 'MORTAL'].includes(e.severidad) ? ' y a los médicos' : ''}.`);
        },
        error: (e: HttpErrorResponse) => {
          this.erroresEvento.set(erroresCampos(e));
          if (!Object.keys(erroresCampos(e)).length) this.error.set(mensajeError(e, 'No fue posible reportar el evento.'));
        },
      });
  }

  // ---------- Entrega de turno ----------

  protected entregar(): void {
    this.service.entregar(this.pacienteId(), this.notaEntrega.trim() || null).subscribe({
      next: (e) => {
        this.entregas.set([e, ...this.entregas()]);
        this.notaEntrega = '';
        this.aviso.set('Turno entregado. El resumen quedó guardado tal como está ahora.');
      },
      error: (e: HttpErrorResponse) => this.error.set(mensajeError(e, 'No fue posible entregar el turno.')),
    });
  }
}
