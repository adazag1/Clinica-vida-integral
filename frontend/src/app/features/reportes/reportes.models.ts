export interface Resumen {
  casos: number;
  promedio: number | null;
  mediana: number | null;
  p90: number | null;
}

export interface MesClinico {
  mes: string;
  casos: number;
  promedioHoras: number | null;
  medianaHoras: number | null;
}

export interface ComponenteTiempo {
  nombre: string;
  descripcion: string;
  casos: number;
  promedioHoras: number | null;
}

export interface TableroClinico {
  lineaBaseHoras: number | null;
  metaReduccionPct: number;
  metaHoras: number | null;
  plazoMeses: number;
  inicio: string;
  mesesTranscurridos: number;
  ultimos30Dias: Resumen;
  reduccionPct: number | null;
  estado: 'META_ALCANZADA' | 'EN_PROGRESO' | 'SIN_MEJORA' | 'SIN_DATOS';
  serie: MesClinico[];
  componentes: ComponenteTiempo[];
  porDiagnostico: { codigo: string; nombre: string; casos: number; promedioHoras: number | null }[];
  recientes: { diagnosticadoEn: string; codigo: string; nombre: string; horas: number; desdeAdmision: boolean }[];
  prefijosCriticos: string[];
  parametrosDePrueba: boolean;
  calculadoEn: string;
}

export interface MesFinanciero {
  mes: string;
  costosRegistrados: boolean;
  costoAdministrativo: number | null;
  costoPlataforma: number | null;
  ahorro: number | null;
  variacionCostosPct: number | null;
  atenciones: number;
  medicos: number;
  atencionesPorMedico: number | null;
  variacionCapacidadPct: number | null;
  facturado: number;
  recaudado: number;
  cargosAutomaticosPct: number | null;
  beneficioCapacidad: number;
  beneficioNeto: number | null;
}

export interface Roi {
  inversion: number;
  inversionUsd: number;
  acumulado: number;
  promedioReciente: number | null;
  mesesTranscurridos: number;
  plazoMeses: number;
  acumuladoAlPlazo: number | null;
  roiAlPlazoPct: number | null;
  mesRecuperacion: number | null;
  positivoEnPlazo: boolean;
  curva: number[];
}

export interface TableroFinanciero {
  costosLineaBase: number;
  costosMeta: number;
  costosMetaReduccionPct: number;
  capacidadLineaBase: number;
  capacidadMeta: number;
  capacidadMetaAumentoPct: number;
  meses: MesFinanciero[];
  roi: Roi;
  parametrosDePrueba: boolean;
  calculadoEn: string;
}

export interface Parametro {
  clave: string;
  valor: string;
  descripcion: string;
  unidad: string | null;
  prueba: boolean;
  modificadoPor: string | null;
  modificadoEn: string | null;
}

export interface Costo {
  mes: string;
  categoria: string;
  categoriaDescripcion: string;
  administrativa: boolean;
  valor: number;
  nota: string | null;
  registradoPor: string;
  registradoEn: string;
}

export interface Categoria {
  valor: string;
  descripcion: string;
  administrativa: boolean;
}

export const pesos = (v: number) => v.toLocaleString('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });
export const horas = (v: number) => `${v.toLocaleString('es-CO', { maximumFractionDigits: 1 })} h`;
