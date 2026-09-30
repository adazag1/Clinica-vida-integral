import { Pagina } from '../../core/api/api';
import { PacienteResumen } from '../pacientes/pacientes.models';

export const TIPOS_DOCUMENTO: { valor: string; etiqueta: string; administrativo: boolean }[] = [
  { valor: 'HISTORIA_CLINICA', etiqueta: 'Nota de consulta / historia clínica', administrativo: false },
  { valor: 'EPICRISIS', etiqueta: 'Epicrisis', administrativo: false },
  { valor: 'RESULTADO_LABORATORIO', etiqueta: 'Resultado de laboratorio', administrativo: false },
  { valor: 'INFORME_RADIOLOGIA', etiqueta: 'Informe de imágenes diagnósticas', administrativo: false },
  { valor: 'INFORME_PATOLOGIA', etiqueta: 'Informe de patología', administrativo: false },
  { valor: 'RESULTADO_EXTERNO', etiqueta: 'Resultado o documento externo', administrativo: false },
  { valor: 'ORDEN_MEDICA', etiqueta: 'Orden médica', administrativo: false },
  { valor: 'FORMULA', etiqueta: 'Fórmula médica', administrativo: false },
  { valor: 'CONSENTIMIENTO', etiqueta: 'Consentimiento informado', administrativo: true },
  { valor: 'IDENTIFICACION', etiqueta: 'Documento de identidad', administrativo: true },
  { valor: 'AUTORIZACION', etiqueta: 'Autorización de la aseguradora', administrativo: true },
  { valor: 'OTRO', etiqueta: 'Otro', administrativo: false },
];

export const ESTADOS_DOCUMENTO = [
  { valor: 'ACTIVO', etiqueta: 'Activo' },
  { valor: 'ARCHIVADO', etiqueta: 'Archivado' },
  { valor: 'EN_CUSTODIA', etiqueta: 'En custodia' },
  { valor: 'ELIMINADO', etiqueta: 'Eliminado según normativa' },
];

export interface DocumentoResumen {
  id: number;
  pacienteId: number;
  pacienteNombre: string | null;
  pacienteDocumento: string | null;
  tipo: string;
  tipoDescripcion: string;
  titulo: string;
  fechaDocumento: string;
  servicio: string;
  profesional: string | null;
  soporte: 'DIGITAL' | 'FISICO';
  estado: string;
  estadoDescripcion: string;
  origen: 'CARGADO' | 'GENERADO';
  enlace: string | null;
  versionActual: number;
  tieneArchivo: boolean;
  tipoMime: string | null;
  ubicacion: string | null;
}

export interface VersionDocumento {
  version: number;
  nombre: string;
  tipoMime: string;
  tamano: number;
  sha256: string;
  motivo: string | null;
  usuario: string;
  fecha: string;
}

export interface Documento {
  resumen: DocumentoResumen;
  paciente: PacienteResumen;
  episodioId: number | null;
  descripcion: string | null;
  sala: string | null;
  estanteria: string | null;
  seccion: string | null;
  caja: string | null;
  retencionHasta: string;
  motivoEstado: string | null;
  registradoPor: string | null;
  registradoEn: string;
  versiones: VersionDocumento[];
  eventos: { accion: string; detalle: string | null; usuario: string; fecha: string }[];
}

export interface SolicitudArchivo {
  id: number;
  paciente: PacienteResumen;
  documentoId: number | null;
  documentoTitulo: string | null;
  solicitante: string;
  solicitanteNombre: string;
  motivo: string;
  urgente: boolean;
  estado: 'PENDIENTE' | 'ENTREGADA' | 'DEVUELTA' | 'RECHAZADA';
  estadoDescripcion: string;
  solicitadaEn: string;
  atendidaPor: string | null;
  entregadaEn: string | null;
  devueltaEn: string | null;
  respuesta: string | null;
}

export interface Conteo {
  clave: string;
  etiqueta: string;
  cantidad: number;
}

export interface ReporteDocumental {
  desde: string;
  hasta: string;
  totalDocumentos: number;
  porTipo: Conteo[];
  porEstado: Conteo[];
  porOrigen: Conteo[];
  accionesPeriodo: Conteo[];
  accesosPorUsuario: Conteo[];
  solicitudesPeriodo: number;
  solicitudesPorEstado: Conteo[];
  minutosPromedioEntrega: number | null;
  solicitudesPendientes: number;
}

export type PaginaDocumentos = Pagina<DocumentoResumen>;

export function claseEstadoDocumento(e: string): string {
  return e === 'ACTIVO' ? 'chip--normal' : e === 'ELIMINADO' ? 'chip--critico' : e === 'EN_CUSTODIA' ? 'chip--primario' : 'chip--info';
}

export function tamano(bytes: number): string {
  return bytes < 1024 ? `${bytes} B` : bytes < 1_048_576 ? `${(bytes / 1024).toFixed(0)} KB` : `${(bytes / 1_048_576).toFixed(1)} MB`;
}
