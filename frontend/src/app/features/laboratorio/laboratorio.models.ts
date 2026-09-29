import { PacienteResumen } from '../pacientes/pacientes.models';

export type EstadoOrden = 'ORDENADA' | 'MUESTRA_RECIBIDA' | 'EN_PROCESO' | 'VALIDADA' | 'RECHAZADA';
export type PrioridadLab = 'URGENTE' | 'HOSPITALIZADO' | 'AMBULATORIO';
export type Interpretacion = 'NORMAL' | 'BAJO' | 'ALTO' | 'CRITICO_BAJO' | 'CRITICO_ALTO';

export const ESTADOS_ORDEN: { valor: EstadoOrden; etiqueta: string }[] = [
  { valor: 'ORDENADA', etiqueta: 'Pendiente de muestra' },
  { valor: 'MUESTRA_RECIBIDA', etiqueta: 'Muestra recibida' },
  { valor: 'EN_PROCESO', etiqueta: 'En proceso' },
  { valor: 'RECHAZADA', etiqueta: 'Muestra rechazada' },
  { valor: 'VALIDADA', etiqueta: 'Validada (últimas 48 h)' },
];

export const PRIORIDADES_LAB: { valor: PrioridadLab; etiqueta: string }[] = [
  { valor: 'URGENTE', etiqueta: 'Urgente' },
  { valor: 'HOSPITALIZADO', etiqueta: 'Hospitalizado' },
  { valor: 'AMBULATORIO', etiqueta: 'Ambulatorio' },
];

export const MOTIVOS_RECHAZO = [
  { valor: 'HEMOLIZADA', etiqueta: 'Muestra hemolizada' },
  { valor: 'COAGULADA', etiqueta: 'Muestra coagulada' },
  { valor: 'INSUFICIENTE', etiqueta: 'Volumen insuficiente' },
  { valor: 'MAL_IDENTIFICADA', etiqueta: 'Identificación incorrecta' },
  { valor: 'RECIPIENTE_INCORRECTO', etiqueta: 'Tubo o recipiente incorrecto' },
  { valor: 'TIEMPO_EXCEDIDO', etiqueta: 'Tiempo de transporte excedido' },
  { valor: 'OTRO', etiqueta: 'Otro' },
];

export const MOTIVOS_REPROCESO = [
  { valor: 'CONFIRMAR_CRITICO', etiqueta: 'Confirmación de valor crítico' },
  { valor: 'DELTA_CHECK', etiqueta: 'Diferencia con resultado previo' },
  { valor: 'INCOHERENCIA', etiqueta: 'Incoherencia entre resultados' },
  { valor: 'FALLA_EQUIPO', etiqueta: 'Falla del equipo' },
  { valor: 'CONTROL_FUERA_DE_RANGO', etiqueta: 'Control de calidad fuera de rango' },
  { valor: 'OTRO', etiqueta: 'Otro' },
];

export const AREAS_LAB = [
  { valor: 'HEMATOLOGIA', etiqueta: 'Hematología', equipo: 'Sysmex XN-1000' },
  { valor: 'QUIMICA', etiqueta: 'Química clínica', equipo: 'Cobas c311' },
  { valor: 'INMUNOLOGIA', etiqueta: 'Inmunología', equipo: 'Architect i1000' },
];

export interface Rango {
  id: number;
  sexo: string | null;
  edadMin: number;
  edadMax: number;
  refMin: number | null;
  refMax: number | null;
  criticoMin: number | null;
  criticoMax: number | null;
  modificadoPor: string | null;
  modificadoEn: string | null;
}

export interface Analito {
  id: number;
  codigo: string;
  nombre: string;
  unidad: string;
  decimales: number;
  deltaPorcentaje: number | null;
  rangos: Rango[];
}

export interface Examen {
  id: number;
  codigo: string;
  nombre: string;
  area: string;
  areaDescripcion: string;
  tipoMuestra: string;
  tatUrgenteMin: number;
  tatRutinaMin: number;
  analitos: Analito[];
}

export interface OrdenResumen {
  id: number;
  numero: string;
  paciente: PacienteResumen;
  medicoNombre: string;
  prioridad: PrioridadLab;
  prioridadDescripcion: string;
  estado: EstadoOrden;
  estadoDescripcion: string;
  examenes: string[];
  ordenadaEn: string;
  limiteTat: string;
  tatVencido: boolean;
  tieneCriticos: boolean;
  validadaEn: string | null;
}

export interface Reproceso {
  valorAnterior: number;
  valorNuevo: number;
  motivo: string;
  motivoDescripcion: string;
  detalle: string | null;
  usuario: string;
  fecha: string;
}

export interface ResultadoFila {
  analitoId: number;
  codigo: string;
  nombre: string;
  unidad: string;
  decimales: number;
  valor: number | null;
  interpretacion: Interpretacion | null;
  interpretacionDescripcion: string | null;
  rangoTexto: string;
  valorPrevio: number | null;
  fechaPrevio: string | null;
  delta: number | null;
  alertaDelta: boolean;
  repeticiones: number;
  reprocesos: Reproceso[];
}

export interface OrdenExamen {
  id: number;
  examenId: number;
  codigo: string;
  nombre: string;
  area: string;
  areaDescripcion: string;
  tipoMuestra: string;
  equipo: string | null;
  loteReactivo: string | null;
  calibracion: string | null;
  resultados: ResultadoFila[];
}

export interface Orden {
  id: number;
  numero: string;
  paciente: PacienteResumen;
  consultaId: number | null;
  medico: string;
  medicoNombre: string;
  prioridad: PrioridadLab;
  prioridadDescripcion: string;
  indicaciones: string | null;
  estado: EstadoOrden;
  estadoDescripcion: string;
  ordenadaEn: string;
  muestraTomadaEn: string | null;
  muestraTomadaPor: string | null;
  muestraRecibidaEn: string | null;
  procesoEn: string | null;
  validadaEn: string | null;
  validadaPor: string | null;
  motivoRechazo: string | null;
  motivoRechazoDescripcion: string | null;
  detalleRechazo: string | null;
  criticoContacto: string | null;
  criticoMedio: string | null;
  criticoNotificadoEn: string | null;
  limiteTat: string;
  tatVencido: boolean;
  tieneCriticos: boolean;
  examenes: OrdenExamen[];
  controles: { area: string; areaDescripcion: string; aceptado: boolean | null; equipo: string | null; registradoPor: string | null; registradoEn: string | null }[];
  pendientesValidacion: string[];
  advertencias: string[];
  historial: { accion: string; detalle: string | null; usuario: string; fecha: string }[];
  contexto: { sexo: string | null; edad: number | null; motivoConsulta: string | null; diagnosticos: string[]; alergias: string[] };
}

export interface ControlCalidad {
  id: number;
  area: string;
  areaDescripcion: string;
  equipo: string;
  fecha: string;
  niveles: string;
  loteControl: string;
  aceptado: boolean;
  observacion: string | null;
  registradoPor: string;
  registradoEn: string;
}

export interface ResultadoReciente {
  ordenId: number;
  examen: string;
  analito: string;
  valor: number;
  unidad: string;
  decimales: number;
  interpretacion: Interpretacion;
  interpretacionDescripcion: string;
  rangoTexto: string;
  fecha: string;
}

/** Clase de color del chip según el semáforo del resultado. */
export function claseInterpretacion(i: Interpretacion | null): string {
  if (!i) return 'chip--info';
  if (i === 'NORMAL') return 'chip--normal';
  if (i.startsWith('CRITICO')) return 'chip--critico';
  return 'chip--alerta';
}

export function claseEstadoOrden(e: EstadoOrden): string {
  return ({ VALIDADA: 'chip--normal', RECHAZADA: 'chip--critico', EN_PROCESO: 'chip--info', MUESTRA_RECIBIDA: 'chip--primario' } as Record<string, string>)[e] ?? 'chip--alerta';
}
