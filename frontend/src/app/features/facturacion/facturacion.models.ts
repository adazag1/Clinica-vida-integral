import { PacienteResumen } from '../pacientes/pacientes.models';

export type EstadoCuenta = 'ABIERTA' | 'EN_AUDITORIA' | 'DEVUELTA' | 'AUDITADA' | 'FACTURADA' | 'GLOSADA' | 'PAGADA' | 'CERRADA';

export const ESTADOS_CUENTA: { valor: EstadoCuenta; etiqueta: string }[] = [
  { valor: 'ABIERTA', etiqueta: 'Abiertas' },
  { valor: 'DEVUELTA', etiqueta: 'Devueltas' },
  { valor: 'EN_AUDITORIA', etiqueta: 'En auditoría' },
  { valor: 'AUDITADA', etiqueta: 'Por facturar' },
  { valor: 'FACTURADA', etiqueta: 'Facturadas' },
  { valor: 'GLOSADA', etiqueta: 'Glosadas' },
  { valor: 'PAGADA', etiqueta: 'Pagadas' },
  { valor: 'CERRADA', etiqueta: 'Cerradas' },
];

export const CONCEPTOS_GLOSA = [
  { valor: 'FACTURACION', etiqueta: '1 · Facturación', codigo: '1' },
  { valor: 'TARIFAS', etiqueta: '2 · Tarifas', codigo: '2' },
  { valor: 'SOPORTES', etiqueta: '3 · Soportes', codigo: '3' },
  { valor: 'AUTORIZACION', etiqueta: '4 · Autorización', codigo: '4' },
  { valor: 'COBERTURA', etiqueta: '5 · Cobertura', codigo: '5' },
  { valor: 'PERTINENCIA', etiqueta: '6 · Pertinencia', codigo: '6' },
];

export const TIPOS_REGLA = [
  { valor: 'TARIFA_PACTADA', etiqueta: 'Tarifa pactada' },
  { valor: 'EXCLUSION', etiqueta: 'Excluido (lo paga el paciente)' },
  { valor: 'TOPE_CANTIDAD', etiqueta: 'Tope de cantidad por cuenta' },
  { valor: 'AUTORIZACION', etiqueta: 'Exige autorización' },
];

export const TIPOS_SERVICIO = [
  { valor: 'CONSULTA', etiqueta: 'Consulta' },
  { valor: 'LABORATORIO', etiqueta: 'Laboratorio clínico' },
  { valor: 'IMAGEN', etiqueta: 'Imágenes diagnósticas' },
  { valor: 'PATOLOGIA', etiqueta: 'Patología' },
  { valor: 'MEDICAMENTO', etiqueta: 'Medicamento' },
  { valor: 'PROCEDIMIENTO', etiqueta: 'Procedimiento' },
  { valor: 'INSUMO', etiqueta: 'Insumo' },
  { valor: 'ESTANCIA', etiqueta: 'Estancia' },
];

export interface Servicio {
  id: number;
  codigo: string;
  nombre: string;
  tipo: string;
  tipoDescripcion: string;
  claveOrigen: string | null;
  tarifaBase: number;
  requiereAutorizacion: boolean;
  activo: boolean;
  modificadoPor: string | null;
  modificadoEn: string | null;
}

export interface Regla {
  id: number;
  servicioId: number;
  servicioCodigo: string;
  servicioNombre: string;
  tipo: string;
  tipoDescripcion: string;
  valor: number | null;
  cantidadMax: number | null;
}

export interface Paquete {
  id: number;
  codigo: string;
  nombre: string;
  precio: number;
  servicios: string[];
}

export interface Contrato {
  id: number;
  aseguradora: string;
  nombre: string;
  modalidad: 'EVENTO' | 'CAPITACION';
  modalidadDescripcion: string;
  factorTarifa: number;
  diasPago: number;
  topeCuenta: number | null;
  vigenteDesde: string;
  vigenteHasta: string | null;
  activo: boolean;
  modificadoPor: string | null;
  modificadoEn: string | null;
  reglas: Regla[];
  paquetes: Paquete[];
}

export interface Cargo {
  id: number;
  servicioId: number;
  servicioCodigo: string;
  servicioNombre: string;
  tipo: string;
  cantidad: number;
  valorUnitario: number;
  valorTotal: number;
  aCargoPaciente: boolean;
  cubiertoCapitacion: boolean;
  requiereAutorizacion: boolean;
  tieneSoporte: boolean;
  soporte: string | null;
  soporteEnlace: string | null;
  origenModulo: string | null;
  fechaServicio: string;
  estado: 'ACTIVO' | 'ANULADO';
  facturado: boolean;
  motivoAnulacion: string | null;
  anuladoPor: string | null;
  registradoPor: string;
}

export interface LineaLiquidacion {
  codigo: string;
  descripcion: string;
  cantidad: number;
  valorUnitario: number;
  valorTotal: number;
  nota: string | null;
}

export interface Liquidacion {
  lineas: LineaLiquidacion[];
  lineasPaciente: LineaLiquidacion[];
  subtotal: number;
  ajustes: number;
  total: number;
  valorPaciente: number;
  advertencias: string[];
  bloqueos: string[];
  facturable: boolean;
}

export interface CuentaResumen {
  id: number;
  numero: string;
  paciente: PacienteResumen;
  aseguradora: string;
  contrato: string;
  modalidad: string;
  estado: EstadoCuenta;
  estadoDescripcion: string;
  abiertaEn: string;
  cargos: number;
  valorCargos: number;
  sinSoporte: number;
  factura: string | null;
  saldo: number | null;
}

export interface Factura {
  id: number;
  numero: string;
  fechaEmision: string;
  fechaVencimiento: string;
  subtotal: number;
  ajustes: number;
  total: number;
  valorPaciente: number;
  saldo: number;
  cufe: string;
  estadoDian: string;
  emitidaPor: string;
  lineas: (LineaLiquidacion & { orden: number })[];
}

export interface Glosa {
  id: number;
  cargoId: number | null;
  concepto: string;
  conceptoDescripcion: string;
  causaClinica: boolean;
  codigo: string;
  descripcion: string;
  valor: number;
  estado: 'RADICADA' | 'ACEPTADA' | 'ACEPTADA_PARCIAL' | 'LEVANTADA';
  estadoDescripcion: string;
  valorAceptado: number | null;
  respuesta: string | null;
  recibidaEn: string;
  limiteRespuesta: string;
  vencida: boolean;
  respondidaEn: string | null;
  respondidaPor: string | null;
  registradaPor: string;
}

export interface Pago {
  id: number;
  valor: number;
  fecha: string;
  medio: string;
  referencia: string | null;
  registradoPor: string;
  registradoEn: string;
}

export interface EventoCuenta {
  accion: string;
  detalle: string | null;
  usuario: string;
  fecha: string;
}

export interface Cuenta {
  resumen: CuentaResumen;
  episodioId: number | null;
  numeroAutorizacion: string | null;
  urgencia: boolean;
  contratoId: number;
  cargos: Cargo[];
  liquidacion: Liquidacion | null;
  factura: Factura | null;
  glosas: Glosa[];
  pagos: Pago[];
  eventos: EventoCuenta[];
}

export interface Cartera {
  porCobrar: number;
  vencido: number;
  rangos: { rango: string; saldo: number; facturas: number }[];
  porAseguradora: { aseguradora: string; saldo: number; vencido: number; facturas: number }[];
  facturas: {
    id: number;
    numero: string;
    cuentaId: number;
    aseguradora: string;
    paciente: string;
    emitida: string;
    vence: string;
    total: number;
    saldo: number;
    diasVencida: number;
    glosasPendientes: number;
  }[];
}

export function claseEstadoCuenta(e: EstadoCuenta): string {
  switch (e) {
    case 'ABIERTA':
      return 'chip--info';
    case 'DEVUELTA':
    case 'GLOSADA':
      return 'chip--critico';
    case 'EN_AUDITORIA':
    case 'AUDITADA':
      return 'chip--alerta';
    case 'FACTURADA':
      return 'chip--primario';
    default:
      return 'chip--normal';
  }
}
