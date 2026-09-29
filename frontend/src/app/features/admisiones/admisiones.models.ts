import { PacienteResumen } from '../pacientes/pacientes.models';

export type TipoAtencion = 'URGENCIAS' | 'CONSULTA_EXTERNA' | 'HOSPITALIZACION';
export type EstadoEpisodio = 'EN_ADMISION' | 'EN_ESPERA_CONSULTA' | 'EN_ATENCION' | 'CERRADO' | 'ANULADO';
export type EstadoCobertura = 'PENDIENTE' | 'VERIFICADA' | 'NO_CUBIERTA' | 'PARTICULAR' | 'EXCEPCION_URGENCIA';
export type TipoConsentimiento = 'CONSENTIMIENTO_INFORMADO' | 'AUTORIZACION_TRATAMIENTO' | 'TRATAMIENTO_DATOS';

export const TIPOS_ATENCION: { valor: TipoAtencion; etiqueta: string; icono: string }[] = [
  { valor: 'URGENCIAS', etiqueta: 'Urgencias', icono: 'emergency' },
  { valor: 'CONSULTA_EXTERNA', etiqueta: 'Consulta externa', icono: 'stethoscope' },
  { valor: 'HOSPITALIZACION', etiqueta: 'Hospitalización', icono: 'bed' },
];

export const RESULTADOS_COBERTURA: { valor: EstadoCobertura; etiqueta: string }[] = [
  { valor: 'VERIFICADA', etiqueta: 'Cobertura verificada' },
  { valor: 'NO_CUBIERTA', etiqueta: 'Sin cobertura' },
  { valor: 'PARTICULAR', etiqueta: 'Pago particular' },
];

export const TIPOS_CONSENTIMIENTO: { valor: TipoConsentimiento; etiqueta: string }[] = [
  { valor: 'CONSENTIMIENTO_INFORMADO', etiqueta: 'Consentimiento informado de atención' },
  { valor: 'AUTORIZACION_TRATAMIENTO', etiqueta: 'Autorización de tratamiento' },
  { valor: 'TRATAMIENTO_DATOS', etiqueta: 'Tratamiento de datos personales (Ley 1581)' },
];

export const NIVELES_TRIAGE = [
  { nivel: 1, etiqueta: 'I – Atención inmediata' },
  { nivel: 2, etiqueta: 'II – Muy urgente' },
  { nivel: 3, etiqueta: 'III – Urgente' },
  { nivel: 4, etiqueta: 'IV – Prioritario' },
  { nivel: 5, etiqueta: 'V – No urgente' },
];

export const ROMANO = ['', 'I', 'II', 'III', 'IV', 'V'];

export interface SignosVitales {
  frecuenciaCardiaca: number;
  frecuenciaRespiratoria: number;
  presionSistolica: number;
  presionDiastolica: number;
  temperatura: number;
  saturacionOxigeno: number;
  glasgow: number;
  dolor: number;
}

export interface Triage {
  signos: SignosVitales;
  sintomaPrincipal: string;
  nivelSugerido: number;
  nivelAsignado: number;
  descripcionNivel: string;
  justificacionAjuste: string | null;
  clasificadoPor: string | null;
  clasificadoEn: string;
  modificadoPor: string | null;
  modificadoEn: string;
}

export interface SugerenciaTriage {
  nivel: number;
  descripcion: string;
  criterios: string[];
  advertencias: string[];
}

export interface Consentimiento {
  id: number;
  tipo: TipoConsentimiento;
  tipoDescripcion: string;
  otorgadoPor: string;
  parentesco: string | null;
  aceptado: boolean;
  observaciones: string | null;
  registradoPor: string | null;
  registradoEn: string;
}

export interface Pendiente {
  codigo: string;
  mensaje: string;
}

export interface Episodio {
  id: number;
  numero: string;
  paciente: PacienteResumen;
  tipoAtencion: TipoAtencion;
  tipoAtencionDescripcion: string;
  motivoConsulta: string;
  estado: EstadoEpisodio;
  estadoDescripcion: string;
  fechaIngreso: string;
  fechaListo: string | null;
  fechaCierre: string | null;
  motivoAnulacion: string | null;
  admitidoPor: string | null;
  estadoCobertura: EstadoCobertura;
  estadoCoberturaDescripcion: string;
  aseguradora: string | null;
  planCobertura: string | null;
  numeroAutorizacion: string | null;
  observacionCobertura: string | null;
  coberturaVerificadaPor: string | null;
  coberturaVerificadaEn: string | null;
  triage: Triage | null;
  consentimientos: Consentimiento[];
  pendientes: Pendiente[];
}

export interface ListaEspera {
  pendientesTriage: Episodio[];
  enAdmision: Episodio[];
  esperaConsulta: Episodio[];
}
