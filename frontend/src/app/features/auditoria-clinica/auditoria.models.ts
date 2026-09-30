import { Consulta } from '../consulta/consulta.models';
import { DocumentoResumen } from '../documentos/documentos.models';
import { Cuenta } from '../facturacion/facturacion.models';
import { EstudioResumen } from '../imagenes/imagenes.models';
import { OrdenResumen } from '../laboratorio/laboratorio.models';
import { CasoResumen } from '../patologia/patologia.models';

export const MOTIVOS_DEVOLUCION = [
  { valor: 'HISTORIA_INCOMPLETA', etiqueta: 'Historia clínica incompleta o sin firmar', destino: 'MEDICO' },
  { valor: 'SIN_ORDEN_MEDICA', etiqueta: 'Procedimiento sin orden médica', destino: 'MEDICO' },
  { valor: 'SOPORTE_FALTANTE', etiqueta: 'Falta soporte del cargo', destino: 'FACTURACION' },
  { valor: 'CODIFICACION', etiqueta: 'Codificación inconsistente', destino: 'MEDICO' },
  { valor: 'AUTORIZACION', etiqueta: 'Falta autorización', destino: 'ADMISIONES' },
  { valor: 'PERTINENCIA', etiqueta: 'Pertinencia clínica en duda', destino: 'MEDICO' },
  { valor: 'OTRO', etiqueta: 'Otro', destino: 'FACTURACION' },
];

export const DESTINOS = [
  { valor: 'FACTURACION', etiqueta: 'Facturación' },
  { valor: 'MEDICO', etiqueta: 'Médico tratante' },
  { valor: 'ADMISIONES', etiqueta: 'Admisiones' },
];

export interface Sugerencia {
  codigo: string;
  nombre: string;
  puntaje: number;
  evidencia: string[];
  registradoPorMedico: boolean;
}

export interface Codificacion {
  diagnosticoPrincipal: string | null;
  nombrePrincipal: string | null;
  relacionados: string[];
  nombresRelacionados: string[];
  modificadoPor: string | null;
  modificadoEn: string | null;
  historial: { campo: string; anterior: string | null; nuevo: string | null; motivo: string | null; usuario: string; fecha: string }[];
}

export interface Devolucion {
  id: number;
  motivo: string;
  motivoDescripcion: string;
  detalle: string;
  dirigidaA: string;
  devueltaPor: string;
  devueltaEn: string;
  corregidaEn: string | null;
  corregidaPor: string | null;
  respuesta: string | null;
  horasCorreccion: number | null;
}

export interface Consentimiento {
  id: number;
  tipoDescripcion: string;
  otorgadoPor: string;
  parentesco: string | null;
  aceptado: boolean;
  registradoEn: string;
}

export interface Expediente {
  cuenta: Cuenta;
  episodio: string;
  consentimientos: Consentimiento[];
  consultas: Consulta[];
  laboratorio: OrdenResumen[];
  imagenes: EstudioResumen[];
  patologia: CasoResumen[];
  documentos: DocumentoResumen[];
  verificacion: { punto: string; cumple: boolean; detalle: string }[];
  codificacion: Codificacion;
  sugerencias: Sugerencia[];
  devoluciones: Devolucion[];
}

export interface Conteo {
  clave: string;
  etiqueta: string;
  cantidad: number;
  valor: number | null;
  promedioHoras: number | null;
}

export interface Tablero {
  cuentasEnAuditoria: number;
  cuentasDevueltas: number;
  auditadasPeriodo: number;
  devolucionesPeriodo: number;
  tasaDevolucionPct: number | null;
  horasPromedioCorreccion: number | null;
  devolucionesPorMotivo: Conteo[];
  consultasSinFirmar: number;
  cargosSinSoporte: number;
  glosasPorConcepto: Conteo[];
  glosasClinicas: number;
  valorGlosasClinicas: number;
}

export interface DiagnosticoCatalogo {
  id: number;
  codigo: string;
  nombre: string;
  activo: boolean;
}

export interface CambioCatalogo {
  version: number;
  codigo: string;
  accion: string;
  detalle: string | null;
  usuario: string;
  fecha: string;
}
