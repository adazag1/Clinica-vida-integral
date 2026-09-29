import { PacienteResumen } from '../pacientes/pacientes.models';
import { Evento } from '../imagenes/imagenes.models';

export type TipoCaso = 'BIOPSIA' | 'CITOLOGIA' | 'PIEZA_QUIRURGICA';
export type PrioridadCaso = 'INTRAOPERATORIO' | 'URGENTE' | 'RUTINA';
export type EstadoCaso = 'SOLICITADO' | 'RECIBIDO' | 'MACROSCOPIA' | 'INFORMADO';

export const TIPOS_CASO: { valor: TipoCaso; etiqueta: string }[] = [
  { valor: 'BIOPSIA', etiqueta: 'Biopsia' },
  { valor: 'CITOLOGIA', etiqueta: 'Citología' },
  { valor: 'PIEZA_QUIRURGICA', etiqueta: 'Pieza quirúrgica' },
];

export const PRIORIDADES_CASO: { valor: PrioridadCaso; etiqueta: string }[] = [
  { valor: 'INTRAOPERATORIO', etiqueta: 'Intraoperatorio (congelación)' },
  { valor: 'URGENTE', etiqueta: 'Urgente' },
  { valor: 'RUTINA', etiqueta: 'Rutina' },
];

export const MATERIALES = [
  { valor: 'PIEZA', etiqueta: 'Pieza o tejido remanente' },
  { valor: 'BLOQUE', etiqueta: 'Bloques de parafina' },
  { valor: 'LAMINA', etiqueta: 'Láminas' },
];

export const MOVIMIENTOS = [
  { valor: 'ARCHIVADO', etiqueta: 'Archivar' },
  { valor: 'PRESTADO', etiqueta: 'Prestar' },
  { valor: 'DEVUELTO', etiqueta: 'Registrar devolución' },
  { valor: 'DESCARTADO', etiqueta: 'Descartar (retención cumplida)' },
];

export interface CampoPlantilla {
  clave: string;
  etiqueta: string;
  seccion: 'MACRO' | 'MICRO';
  tipo: 'texto' | 'numero' | 'opcion';
  opciones: string[] | null;
  requerido: boolean;
  unidad: string | null;
}

export interface PlantillaPatologia {
  id: number;
  tipo: TipoCaso;
  tipoDescripcion: string;
  servicio: string | null;
  nombre: string;
  campos: CampoPlantilla[];
  modificadoPor: string | null;
  modificadoEn: string | null;
}

export interface CasoResumen {
  id: number;
  numero: string;
  paciente: PacienteResumen;
  tipo: TipoCaso;
  tipoDescripcion: string;
  prioridad: PrioridadCaso;
  prioridadDescripcion: string;
  estado: EstadoCaso;
  estadoDescripcion: string;
  sitioAnatomico: string;
  medicoNombre: string;
  solicitadoEn: string;
  recibidoEn: string | null;
  limiteRespuesta: string;
  vencido: boolean;
  validadoEn: string | null;
  diagnostico: string | null;
}

export interface VersionInforme {
  version: number;
  macroscopia: Record<string, string>;
  microscopia: Record<string, string>;
  diagnostico: string;
  motivoCambio: string | null;
  autor: string;
  autorNombre: string;
  validadoEn: string;
  firmaHash: string;
  integro: boolean;
}

export interface Custodia {
  material: string;
  materialDescripcion: string;
  accion: string;
  accionDescripcion: string;
  cantidad: number;
  ubicacion: string | null;
  destinatario: string | null;
  motivo: string | null;
  usuario: string;
  fecha: string;
}

export interface Caso {
  resumen: CasoResumen;
  consultaId: number | null;
  datosClinicos: string | null;
  numeroMuestras: number;
  medico: string;
  recibidoPor: string | null;
  plantilla: PlantillaPatologia | null;
  macroscopia: Record<string, string>;
  microscopia: Record<string, string>;
  diagnostico: string | null;
  macroscopiaPor: string | null;
  macroscopiaEn: string | null;
  validadoPor: string | null;
  retencionHasta: string | null;
  cambiosSinValidar: boolean;
  versiones: VersionInforme[];
  custodia: Custodia[];
  ubicacionMaterial: string[];
  eventos: Evento[];
  alergias: string[];
  diagnosticosConsulta: string[];
  previos: CasoResumen[];
}

export function claseEstadoCaso(e: EstadoCaso): string {
  return e === 'INFORMADO' ? 'chip--normal' : e === 'SOLICITADO' ? 'chip--info' : e === 'RECIBIDO' ? 'chip--alerta' : 'chip--primario';
}
