import { PacienteResumen } from '../pacientes/pacientes.models';

export type PrioridadEstudio = 'URGENTE' | 'HOSPITALIZADO' | 'AMBULATORIO';
export type EstadoEstudio = 'ORDENADO' | 'ADQUIRIDO' | 'EN_LECTURA' | 'INFORMADO' | 'BLOQUEADO';
export type VistaTrabajo = 'PENDIENTES' | 'MIOS' | 'INFORMADOS';

export const MODALIDADES = [
  { valor: 'RX', etiqueta: 'Radiografía' },
  { valor: 'TC', etiqueta: 'Tomografía' },
  { valor: 'RM', etiqueta: 'Resonancia' },
  { valor: 'ECO', etiqueta: 'Ecografía' },
  { valor: 'MG', etiqueta: 'Mamografía' },
];

export const PRIORIDADES_ESTUDIO: { valor: PrioridadEstudio; etiqueta: string }[] = [
  { valor: 'URGENTE', etiqueta: 'Urgente' },
  { valor: 'HOSPITALIZADO', etiqueta: 'Hospitalizado' },
  { valor: 'AMBULATORIO', etiqueta: 'Ambulatorio' },
];

export const CONCORDANCIAS = [
  { valor: 'CONCORDANTE', etiqueta: 'Concordante' },
  { valor: 'DISCREPANCIA_MENOR', etiqueta: 'Discrepancia menor' },
  { valor: 'DISCREPANCIA_MAYOR', etiqueta: 'Discrepancia mayor (cambia la conducta)' },
];

export interface TipoEstudio {
  id: number;
  codigo: string;
  nombre: string;
  modalidad: string;
  modalidadDescripcion: string;
  region: string;
  tatUrgenteMin: number;
  tatHospitalizadoMin: number;
  tatAmbulatorioMin: number;
  plantillaHallazgos: string | null;
  plantillaImpresion: string | null;
  plantillaRecomendaciones: string | null;
  modificadoPor: string | null;
  modificadoEn: string | null;
}

export interface EstudioResumen {
  id: number;
  numero: string;
  paciente: PacienteResumen;
  estudio: string;
  modalidad: string;
  modalidadDescripcion: string;
  prioridad: PrioridadEstudio;
  prioridadDescripcion: string;
  estado: EstadoEstudio;
  estadoDescripcion: string;
  ordenadoEn: string;
  limiteTat: string;
  tatVencido: boolean;
  asignadoA: string | null;
  medicoNombre: string;
  imagenes: number;
  critico: boolean;
  segundaOpinionPendiente: boolean;
  informadoEn: string | null;
  impresion: string | null;
}

export interface ImagenEstudio {
  id: number;
  orden: number;
  nombre: string;
  tipoMime: string;
  tamano: number;
  visible: boolean;
  clave: boolean;
  descripcion: string | null;
  sha256: string;
}

export interface InformeImagen {
  version: number;
  hallazgos: string | null;
  impresion: string | null;
  recomendaciones: string | null;
  estado: 'BORRADOR' | 'FIRMADO';
  motivoCambio: string | null;
  autor: string;
  autorNombre: string;
  creadoEn: string;
  modificadoEn: string;
  firmadoEn: string | null;
  firmaHash: string | null;
  integro: boolean | null;
}

export interface SegundaOpinion {
  id: number;
  solicitadaPor: string;
  asignadaA: string;
  asignadaANombre: string;
  pregunta: string;
  solicitadaEn: string;
  concordancia: string | null;
  concordanciaDescripcion: string | null;
  comentario: string | null;
  respondidaEn: string | null;
}

export interface Evento {
  accion: string;
  detalle: string | null;
  usuario: string;
  fecha: string;
}

export interface Estudio {
  resumen: EstudioResumen;
  consultaId: number | null;
  motivo: string;
  diagnosticoPresuntivo: string | null;
  medico: string;
  adquiridoEn: string | null;
  adquiridoPor: string | null;
  studyInstanceUid: string | null;
  urlPacs: string | null;
  bloqueoMotivo: string | null;
  bloqueadoPor: string | null;
  bloqueadoEn: string | null;
  criticoReceptor: string | null;
  criticoMedio: string | null;
  criticoNotificadoEn: string | null;
  imagenes: ImagenEstudio[];
  borrador: InformeImagen | null;
  vigente: InformeImagen | null;
  versiones: InformeImagen[];
  plantilla: { hallazgos: string | null; impresion: string | null; recomendaciones: string | null };
  segundasOpiniones: SegundaOpinion[];
  eventos: Evento[];
  previos: EstudioResumen[];
  contexto: { motivoConsulta: string | null; diagnosticos: string[]; alergias: string[]; antecedentes: string[] };
}

export interface MetricaImagenes {
  estudio: string;
  modalidad: string;
  informados: number;
  promedioMin: number | null;
  medianaMin: number | null;
  promedioLecturaMin: number | null;
  dentroDeTatPct: number | null;
  pendientes: number;
  pendientesVencidos: number;
}

export interface Radiologo {
  usuario: string;
  nombre: string;
}

export function clasePrioridad(p: string): string {
  return p === 'URGENTE' || p === 'INTRAOPERATORIO' ? 'chip--critico' : p === 'HOSPITALIZADO' ? 'chip--alerta' : 'chip--info';
}

export function claseEstadoEstudio(e: EstadoEstudio): string {
  switch (e) {
    case 'INFORMADO':
      return 'chip--normal';
    case 'BLOQUEADO':
      return 'chip--critico';
    case 'EN_LECTURA':
      return 'chip--primario';
    case 'ADQUIRIDO':
      return 'chip--alerta';
    default:
      return 'chip--info';
  }
}

/** "2 h 15 min" o "3 d 4 h": para tiempos de respuesta. */
export function duracion(minutos: number | null): string {
  if (minutos === null || minutos === undefined) return '—';
  const m = Math.round(minutos);
  if (m < 60) return `${m} min`;
  if (m < 1440) return `${Math.floor(m / 60)} h ${m % 60} min`;
  return `${Math.floor(m / 1440)} d ${Math.floor((m % 1440) / 60)} h`;
}
