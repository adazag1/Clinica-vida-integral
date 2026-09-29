import { ResultadoReciente } from '../laboratorio/laboratorio.models';
import { PacienteResumen } from '../pacientes/pacientes.models';

export type TipoAntecedente = 'ALERGIA' | 'MEDICAMENTO_ACTUAL' | 'PATOLOGICO' | 'QUIRURGICO' | 'FAMILIAR' | 'OTRO';
export type NivelAlerta = 'NORMAL' | 'ANORMAL' | 'CRITICO';

export const TIPOS_ANTECEDENTE: { valor: TipoAntecedente; etiqueta: string; icono: string }[] = [
  { valor: 'ALERGIA', etiqueta: 'Alergia', icono: 'warning' },
  { valor: 'MEDICAMENTO_ACTUAL', etiqueta: 'Medicamento actual', icono: 'medication' },
  { valor: 'PATOLOGICO', etiqueta: 'Enfermedad crónica', icono: 'monitor_heart' },
  { valor: 'QUIRURGICO', etiqueta: 'Quirúrgico', icono: 'surgical' },
  { valor: 'FAMILIAR', etiqueta: 'Familiar', icono: 'family_restroom' },
  { valor: 'OTRO', etiqueta: 'Otro', icono: 'notes' },
];

export interface Diagnostico {
  id: number;
  codigo: string;
  nombre: string;
}

export interface Medicamento {
  id: number;
  nombre: string;
  principioActivo: string;
  familia: string;
  presentacion: string;
  via: string;
}

export interface Antecedente {
  id: number;
  tipo: TipoAntecedente;
  tipoDescripcion: string;
  descripcion: string;
  detalle: string | null;
  severidad: 'LEVE' | 'MODERADA' | 'SEVERA' | null;
  activo: boolean;
  motivoInactivacion: string | null;
  registradoPor: string | null;
  registradoEn: string;
  modificadoPor: string | null;
  modificadoEn: string;
}

export interface AlertaSigno {
  campo: string;
  etiqueta: string;
  valor: string;
  nivel: NivelAlerta;
  mensaje: string;
}

export interface Signos {
  id: number | null;
  origen: 'TRIAGE' | 'REGISTRO';
  presionSistolica: number | null;
  presionDiastolica: number | null;
  frecuenciaCardiaca: number | null;
  frecuenciaRespiratoria: number | null;
  temperatura: number | null;
  saturacionOxigeno: number | null;
  peso: number | null;
  talla: number | null;
  registradoPor: string | null;
  fecha: string;
  alertas: AlertaSigno[];
}

export interface DiagnosticoConsulta {
  diagnosticoId: number;
  codigo: string;
  nombre: string;
  principal: boolean;
  confirmado: boolean;
}

export interface Prescripcion {
  id: number;
  medicamentoId: number;
  medicamento: string;
  presentacion: string;
  via: string;
  dosis: string;
  frecuencia: string;
  duracionDias: number;
  indicaciones: string | null;
}

export interface Consulta {
  id: number;
  paciente: PacienteResumen;
  episodioId: number | null;
  numeroEpisodio: string | null;
  citaId: number | null;
  inicioCita: string | null;
  medico: string;
  medicoNombre: string;
  estado: 'EN_CURSO' | 'FIRMADA';
  estadoDescripcion: string;
  motivoConsulta: string | null;
  enfermedadActual: string | null;
  examenFisico: string | null;
  plan: string | null;
  inicio: string;
  firmadaEn: string | null;
  firmaHash: string | null;
  diagnosticos: DiagnosticoConsulta[];
  prescripciones: Prescripcion[];
  signos: Signos[];
  pendientes: string[];
  modificadoPor: string | null;
  modificadoEn: string;
}

export interface ResumenClinico {
  paciente: PacienteResumen;
  alergias: Antecedente[];
  medicamentosActuales: { nombre: string; detalle: string | null; origen: string; desde: string }[];
  diagnosticosActivos: { codigo: string | null; nombre: string; origen: string; fecha: string }[];
  antecedentesQuirurgicos: Antecedente[];
  otrosAntecedentes: Antecedente[];
  ultimosSignos: Signos | null;
  triage: { nivel: number; descripcion: string; sintomaPrincipal: string; fecha: string } | null;
  alertas: string[];
  resultadosRecientes: ResultadoReciente[];
}

export interface ConsultaHistorial {
  id: number;
  inicio: string;
  firmadaEn: string | null;
  estado: 'EN_CURSO' | 'FIRMADA';
  medicoNombre: string;
  motivoConsulta: string | null;
  plan: string | null;
  diagnosticos: DiagnosticoConsulta[];
  prescripciones: Prescripcion[];
}

export interface PacientePorAtender {
  origen: 'EN_CURSO' | 'URGENCIAS' | 'CONSULTA_EXTERNA' | 'HOSPITALIZACION' | 'CITA';
  episodioId: number | null;
  citaId: number | null;
  consultaId: number | null;
  paciente: PacienteResumen;
  motivo: string | null;
  desde: string | null;
  nivelTriage: number | null;
  prioridad: 'CRITICO' | 'ALERTA' | 'NORMAL';
  motivosPrioridad: string[];
  alergias: number;
}

export interface VerificacionFirma {
  valida: boolean;
  firmadaPor: string;
  firmadaEn: string;
  huellaGuardada: string;
  huellaCalculada: string;
}

export interface SignosRequest {
  consultaId: number | null;
  presionSistolica: number | null;
  presionDiastolica: number | null;
  frecuenciaCardiaca: number | null;
  frecuenciaRespiratoria: number | null;
  temperatura: number | null;
  saturacionOxigeno: number | null;
  peso: number | null;
  talla: number | null;
}
