import { PacienteResumen } from '../pacientes/pacientes.models';

export type EstadoCita = 'PROGRAMADA' | 'CONFIRMADA' | 'ATENDIDA' | 'NO_ASISTIO' | 'CANCELADA' | 'REPROGRAMADA';
export type PrioridadCita = 'URGENTE' | 'PRIORITARIA' | 'NORMAL';

export const PRIORIDADES: { valor: PrioridadCita; etiqueta: string }[] = [
  { valor: 'NORMAL', etiqueta: 'Normal' },
  { valor: 'PRIORITARIA', etiqueta: 'Prioritaria' },
  { valor: 'URGENTE', etiqueta: 'Urgente' },
];

export const ESTADOS_CITA: { valor: EstadoCita; etiqueta: string }[] = [
  { valor: 'PROGRAMADA', etiqueta: 'Programada' },
  { valor: 'CONFIRMADA', etiqueta: 'Confirmada' },
  { valor: 'ATENDIDA', etiqueta: 'Atendida' },
  { valor: 'NO_ASISTIO', etiqueta: 'No asistió' },
  { valor: 'CANCELADA', etiqueta: 'Cancelada' },
];

export const MOTIVOS_CANCELACION = [
  { valor: 'SOLICITUD_PACIENTE', etiqueta: 'Solicitud del paciente' },
  { valor: 'ENFERMEDAD_PACIENTE', etiqueta: 'Enfermedad o imprevisto del paciente' },
  { valor: 'AUSENCIA_PROFESIONAL', etiqueta: 'Ausencia del profesional' },
  { valor: 'AUTORIZACION_PENDIENTE', etiqueta: 'Autorización o cobertura pendiente' },
  { valor: 'ERROR_AGENDAMIENTO', etiqueta: 'Error al agendar' },
  { valor: 'OTRO', etiqueta: 'Otro' },
];

export const MOTIVOS_BLOQUEO = [
  { valor: 'VACACIONES', etiqueta: 'Vacaciones' },
  { valor: 'PERMISO', etiqueta: 'Permiso' },
  { valor: 'INCAPACIDAD', etiqueta: 'Incapacidad' },
  { valor: 'CAPACITACION', etiqueta: 'Capacitación' },
  { valor: 'REUNION', etiqueta: 'Reunión' },
  { valor: 'OTRO', etiqueta: 'Otro' },
];

export const DIAS_SEMANA = ['', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'];

export interface Sede {
  id: number;
  nombre: string;
  ciudad: string;
  direccion: string | null;
}

export interface Especialidad {
  id: number;
  nombre: string;
}

export interface TipoCita {
  id: number;
  codigo: string;
  nombre: string;
  duracionMinutos: number;
  requierePreparacion: boolean;
  instruccionesPreparacion: string | null;
}

export interface Profesional {
  id: number;
  nombre: string;
  especialidadId: number;
  especialidad: string;
  sedeId: number;
  sede: string;
  consultorio: string | null;
  usuarioId: number | null;
}

export interface Catalogos {
  sedes: Sede[];
  especialidades: Especialidad[];
  tipos: TipoCita[];
  profesionales: Profesional[];
  miProfesionalId: number | null;
}

export interface EventoCita {
  accion: string;
  descripcion: string;
  detalle: string | null;
  usuario: string;
  fecha: string;
}

export interface RecordatorioCita {
  canal: string;
  destino: string | null;
  resultado: string;
  detalle: string | null;
  fecha: string;
}

export interface Cita {
  id: number;
  paciente: PacienteResumen;
  profesional: Profesional;
  tipo: TipoCita;
  inicio: string;
  fin: string;
  estado: EstadoCita;
  estadoDescripcion: string;
  prioridad: PrioridadCita;
  prioridadDescripcion: string;
  motivo: string | null;
  motivoCancelacion: string | null;
  motivoCancelacionDescripcion: string | null;
  detalleCancelacion: string | null;
  reprogramadaDesdeId: number | null;
  confirmadaEn: string | null;
  recordatorioEnviadoEn: string | null;
  creadoPor: string | null;
  creadoEn: string;
  modificadoPor: string | null;
  modificadoEn: string;
  historial: EventoCita[];
  recordatorios: RecordatorioCita[];
  enlacePaciente: string | null;
}

export interface Franja {
  inicio: string;
  fin: string;
  reservada: boolean;
}

export interface Bloqueo {
  id: number;
  profesionalId: number;
  inicio: string;
  fin: string;
  motivo: string;
  motivoDescripcion: string;
  descripcion: string | null;
  creadoPor: string | null;
}

export interface ColumnaAgenda {
  profesional: Profesional;
  franjas: Franja[];
  bloqueos: Bloqueo[];
  citas: Cita[];
}

export interface AgendaDia {
  fecha: string;
  profesionales: ColumnaAgenda[];
}

export interface Cupo {
  inicio: string;
  fin: string;
  reservado: boolean;
  profesionalId: number;
  profesional: string;
  especialidad: string;
  sede: string;
}

export interface Indicadores {
  fecha: string;
  citas: number;
  confirmadas: number;
  canceladas: number;
  enListaEspera: number;
  asistencia: number | null;
  carga: {
    profesionalId: number;
    profesional: string;
    especialidad: string;
    citas: number;
    minutosAgendados: number;
    minutosDisponibles: number;
    ocupacion: number | null;
  }[];
}

export interface ResultadoCupoLiberado {
  cita: Cita;
  asignadaListaEspera: Cita | null;
}

export interface EntradaListaEspera {
  id: number;
  paciente: PacienteResumen;
  especialidad: string;
  profesional: Profesional | null;
  tipoCita: string;
  duracionMinutos: number;
  prioridad: PrioridadCita;
  prioridadDescripcion: string;
  motivo: string | null;
  desde: string;
  registradoPor: string | null;
}

export interface FranjaHorario {
  dia: number;
  horaInicio: string;
  horaFin: string;
  tipo: 'NORMAL' | 'RESERVADA_PRIORIDAD';
}

export interface CitaPublica {
  paciente: string;
  inicio: string;
  fin: string;
  profesional: string;
  especialidad: string;
  sede: string;
  direccion: string | null;
  consultorio: string | null;
  tipo: string;
  preparacion: string | null;
  estado: EstadoCita;
  estadoDescripcion: string;
  puedeResponder: boolean;
}

/** Fecha local YYYY-MM-DD (no toISOString: esa usa UTC y cambia de día en la noche). */
export function fechaLocal(d: Date): string {
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const dia = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${m}-${dia}`;
}

export function deFechaLocal(texto: string): Date {
  const [a, m, d] = texto.split('-').map(Number);
  return new Date(a, m - 1, d);
}
