export type Rol =
  | 'ADMIN'
  | 'MEDICO'
  | 'ENFERMERIA'
  | 'LABORATORIO'
  | 'RADIOLOGIA'
  | 'PATOLOGIA'
  | 'ADMISIONES'
  | 'AGENDAMIENTO'
  | 'FACTURACION'
  | 'AUDITORIA'
  | 'ARCHIVO'
  | 'DIRECTIVO'
  | 'PACIENTE';

export interface UsuarioActual {
  id: number;
  username: string;
  email: string;
  nombreCompleto: string;
  rol: Rol;
  rolDescripcion: string;
  cargo: string | null;
}

export interface LoginResponse {
  token: string;
  expiraEn: string;
  usuario: UsuarioActual;
}

/** Formato de error que devuelve el backend (ApiError). */
export interface ApiError {
  fecha: string;
  estado: number;
  codigo: string;
  mensaje: string;
  campos: Record<string, string>;
}
