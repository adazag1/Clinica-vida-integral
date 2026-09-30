export type SeveridadIa = 'INFO' | 'ALERTA' | 'CRITICA';
export type Retroalimentacion = 'UTIL' | 'NO_UTIL';
export type TipoAsistente = 'RESUMEN' | 'DIFERENCIAL';

export interface AlertaIa {
  id: number;
  regla: string;
  reglaNombre: string;
  referencia: string | null;
  severidad: SeveridadIa;
  severidadDescripcion: string;
  puntaje: number | null;
  escala: string | null;
  titulo: string;
  detalle: string;
  evidencia: string[];
  recomendacion: string | null;
  generadaEn: string;
  vigente: boolean;
  cerradaEn: string | null;
  retroalimentacion: Retroalimentacion | null;
  comentario: string | null;
  retroPor: string | null;
}

export interface ReglaIa {
  codigo: string;
  nombre: string;
  descripcion: string;
  referencia: string;
  activa: boolean;
  modificadoPor: string | null;
  modificadoEn: string | null;
  alertas: number;
  utiles: number;
  noUtiles: number;
  /** Porcentaje de alertas calificadas como útiles; null si nadie ha calificado. */
  precision: number | null;
  comentariosRecientes: string[];
}

export interface EstadoIa {
  reglasActivas: boolean;
  totalReglas: number;
  asistenteHabilitado: boolean;
  asistenteConfigurado: boolean;
  modelo: string | null;
  motivo: string | null;
  consultasAsistente30d: number;
  costoAsistente30dUsd: number;
}

export interface RespuestaAsistente {
  tipo: TipoAsistente;
  texto: string;
  modelo: string;
  tokensEntrada: number | null;
  tokensSalida: number | null;
  costoUsd: number | null;
  advertencia: string;
  textoEnviado: string;
}

export function claseSeveridad(s: SeveridadIa): string {
  return s === 'CRITICA' ? 'chip--critico' : s === 'ALERTA' ? 'chip--alerta' : 'chip--info';
}
