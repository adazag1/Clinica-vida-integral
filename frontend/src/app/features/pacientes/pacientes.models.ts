export type TipoDocumento = 'CC' | 'TI' | 'RC' | 'CE' | 'PA' | 'PPT' | 'TEMPORAL';
export type Sexo = 'FEMENINO' | 'MASCULINO' | 'INDETERMINADO';
export type Regimen = 'CONTRIBUTIVO' | 'SUBSIDIADO' | 'ESPECIAL' | 'PARTICULAR';

export const TIPOS_DOCUMENTO: { valor: TipoDocumento; etiqueta: string }[] = [
  { valor: 'CC', etiqueta: 'Cédula de ciudadanía' },
  { valor: 'TI', etiqueta: 'Tarjeta de identidad' },
  { valor: 'RC', etiqueta: 'Registro civil' },
  { valor: 'CE', etiqueta: 'Cédula de extranjería' },
  { valor: 'PA', etiqueta: 'Pasaporte' },
  { valor: 'PPT', etiqueta: 'Permiso por protección temporal' },
];

export const SEXOS: { valor: Sexo; etiqueta: string }[] = [
  { valor: 'FEMENINO', etiqueta: 'Femenino' },
  { valor: 'MASCULINO', etiqueta: 'Masculino' },
  { valor: 'INDETERMINADO', etiqueta: 'Indeterminado' },
];

export const REGIMENES: { valor: Regimen; etiqueta: string }[] = [
  { valor: 'CONTRIBUTIVO', etiqueta: 'Contributivo' },
  { valor: 'SUBSIDIADO', etiqueta: 'Subsidiado' },
  { valor: 'ESPECIAL', etiqueta: 'Especial o de excepción' },
  { valor: 'PARTICULAR', etiqueta: 'Particular (sin aseguradora)' },
];

/** Nombres legibles de los campos del historial de cambios. */
export const CAMPOS_PACIENTE: Partial<Record<string, string>> = {
  tipoDocumento: 'Tipo de documento',
  numeroDocumento: 'Número de documento',
  nombres: 'Nombres',
  apellidos: 'Apellidos',
  fechaNacimiento: 'Fecha de nacimiento',
  sexo: 'Sexo',
  telefono: 'Teléfono',
  email: 'Correo electrónico',
  direccion: 'Dirección',
  aseguradora: 'Aseguradora',
  regimen: 'Régimen',
};

export interface PacienteResumen {
  id: number;
  tipoDocumento: TipoDocumento;
  numeroDocumento: string;
  identificacionTemporal: boolean;
  nombreCompleto: string;
  fechaNacimiento: string | null;
  edad: number | null;
  sexo: Sexo | null;
  telefono: string | null;
  aseguradora: string | null;
}

export interface Paciente extends PacienteResumen {
  tipoDocumentoDescripcion: string;
  nombres: string;
  apellidos: string;
  email: string | null;
  direccion: string | null;
  regimen: Regimen | null;
  creadoEn: string;
  creadoPor: string | null;
  modificadoEn: string;
  modificadoPor: string | null;
}

export interface PacienteRequest {
  tipoDocumento: TipoDocumento;
  numeroDocumento: string | null;
  nombres: string;
  apellidos: string;
  fechaNacimiento: string | null;
  sexo: Sexo | null;
  telefono: string | null;
  email: string | null;
  direccion: string | null;
  aseguradora: string | null;
  regimen: Regimen | null;
  confirmarNoDuplicado: boolean;
  motivo?: string | null;
}

export interface PosibleDuplicado {
  paciente: PacienteResumen;
  puntaje: number;
  coincidencias: string[];
}

export interface CambioDemografico {
  id: number;
  campo: string;
  valorAnterior: string | null;
  valorNuevo: string | null;
  motivo: string;
  usuario: string;
  fecha: string;
}

export interface FiltrosPaciente {
  texto?: string;
  documento?: string;
  nombre?: string;
  fechaNacimiento?: string;
  telefono?: string;
}
