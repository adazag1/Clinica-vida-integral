import { Signos } from '../consulta/consulta.models';
import { OrdenResumen } from '../laboratorio/laboratorio.models';
import { PacienteResumen } from '../pacientes/pacientes.models';

export type TipoOrden = 'MEDICAMENTO' | 'DIETA' | 'CUIDADO';
export type ResultadoEjecucion = 'ADMINISTRADA' | 'REALIZADO' | 'OMITIDA' | 'RECHAZADA_POR_PACIENTE';
export type EstadoHorario = 'PROGRAMADA' | 'PENDIENTE' | 'ATRASADA' | 'NO_APLICA' | ResultadoEjecucion;
export type SituacionOrden = 'ACTIVA' | 'VENCIDA' | 'SUSPENDIDA';

export const TIPOS_ORDEN: { valor: TipoOrden; etiqueta: string; icono: string }[] = [
  { valor: 'MEDICAMENTO', etiqueta: 'Medicamento', icono: 'medication' },
  { valor: 'CUIDADO', etiqueta: 'Cuidado de enfermería', icono: 'healing' },
  { valor: 'DIETA', etiqueta: 'Dieta', icono: 'restaurant' },
];

export const TIPOS_EVENTO = [
  { valor: 'CAIDA', etiqueta: 'Caída' },
  { valor: 'ERROR_MEDICACION', etiqueta: 'Error de medicación' },
  { valor: 'REACCION_ADVERSA_MEDICAMENTO', etiqueta: 'Reacción adversa a medicamento' },
  { valor: 'LESION_POR_PRESION', etiqueta: 'Lesión por presión' },
  { valor: 'FLEBITIS', etiqueta: 'Flebitis o extravasación' },
  { valor: 'INFECCION_ASOCIADA', etiqueta: 'Infección asociada a la atención' },
  { valor: 'IDENTIFICACION_INCORRECTA', etiqueta: 'Identificación incorrecta del paciente' },
  { valor: 'OTRO', etiqueta: 'Otro' },
];

export const SEVERIDADES_EVENTO = [
  { valor: 'INCIDENTE_SIN_DANO', etiqueta: 'Incidente sin daño' },
  { valor: 'LEVE', etiqueta: 'Daño leve' },
  { valor: 'MODERADO', etiqueta: 'Daño moderado' },
  { valor: 'GRAVE', etiqueta: 'Daño grave' },
  { valor: 'MORTAL', etiqueta: 'Muerte' },
];

export interface Ejecucion {
  programadaPara: string;
  realizadaEn: string;
  resultado: ResultadoEjecucion;
  resultadoDescripcion: string;
  motivo: string | null;
  justificacionRetraso: string | null;
  verificaciones: string | null;
  dobleValidacionPor: string | null;
  usuario: string;
}

export interface Horario {
  programadaPara: string;
  estado: EstadoHorario;
  ejecucion: Ejecucion | null;
}

export interface OrdenClinica {
  id: number;
  tipo: TipoOrden;
  tipoDescripcion: string;
  descripcion: string;
  medicamentoId: number | null;
  medicamento: string | null;
  dosis: string | null;
  via: string | null;
  frecuenciaHoras: number | null;
  inicio: string;
  fin: string;
  altoRiesgo: boolean;
  indicaciones: string | null;
  situacion: SituacionOrden;
  motivoSuspension: string | null;
  suspendidaPor: string | null;
  suspendidaEn: string | null;
  medicoNombre: string;
  horarios: Horario[];
}

export interface OrdenesPaciente {
  paciente: PacienteResumen;
  alergias: string[];
  ordenes: OrdenClinica[];
  examenes: OrdenResumen[];
}

export interface PacienteCenso {
  paciente: PacienteResumen;
  ubicacion: string;
  dosisAtrasadas: number;
  proximasDosHoras: number;
  alertas: string[];
  alergias: number;
  prioridad: 'CRITICO' | 'ALERTA' | 'NORMAL';
}

export interface EventoAdverso {
  id: number;
  pacienteId: number;
  tipo: string;
  tipoDescripcion: string;
  severidad: string;
  severidadDescripcion: string;
  ocurridoEn: string;
  lugar: string | null;
  descripcion: string;
  accionesInmediatas: string;
  medicoNotificado: string | null;
  reportadoPor: string;
  reportadoEn: string;
}

export interface Nota {
  id: number;
  texto: string;
  usuario: string;
  fecha: string;
}

export interface ResumenTurno {
  paciente: PacienteResumen;
  turno: string;
  desde: string;
  hasta: string;
  alergias: string[];
  diagnosticos: string[];
  ultimosSignos: Signos | null;
  alertas: string[];
  administrado: string[];
  pendientes: string[];
  ordenesActivas: string[];
  examenes: string[];
  eventos: string[];
  notas: string[];
  texto: string;
}

export interface Entrega {
  id: number;
  turno: string;
  resumen: string;
  nota: string | null;
  entregadoPor: string;
  fecha: string;
}

export interface OrdenClinicaRequest {
  pacienteId: number;
  consultaId: number | null;
  tipo: TipoOrden;
  descripcion: string | null;
  medicamentoId: number | null;
  dosis: string | null;
  via: string | null;
  frecuenciaHoras: number | null;
  inicio: string | null;
  duracionHoras: number;
  indicaciones: string | null;
}

export interface EjecucionRequest {
  programadaPara: string;
  resultado: ResultadoEjecucion;
  verificacion: { documentoPaciente: string; medicamentoId: number; dosis: string; via: string } | null;
  motivo: string | null;
  justificacionRetraso: string | null;
  dobleValidacion: { usuario: string; password: string } | null;
}

/** Color del chip de cada hora programada. */
export function claseHorario(e: EstadoHorario): string {
  switch (e) {
    case 'ADMINISTRADA':
    case 'REALIZADO':
      return 'chip--normal';
    case 'ATRASADA':
      return 'chip--critico';
    case 'PENDIENTE':
    case 'OMITIDA':
    case 'RECHAZADA_POR_PACIENTE':
      return 'chip--alerta';
    case 'NO_APLICA':
      return '';
    default:
      return 'chip--info';
  }
}

export const ETIQUETA_HORARIO: Record<EstadoHorario, string> = {
  PROGRAMADA: 'Programada',
  PENDIENTE: 'Pendiente',
  ATRASADA: 'Atrasada',
  NO_APLICA: 'No aplica',
  ADMINISTRADA: 'Administrada',
  REALIZADO: 'Realizado',
  OMITIDA: 'Omitida',
  RECHAZADA_POR_PACIENTE: 'Rechazada',
};

/** Minutos de tolerancia alrededor de la hora programada (igual que el backend). */
export const TOLERANCIA_MIN = 30;
