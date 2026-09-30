import { Rol } from '../auth/auth.models';

export interface Modulo {
  ruta: string;
  titulo: string;
  icono: string;
  descripcion: string;
  roles: Rol[]; // vacío = todos los roles
}

/** Módulos del sistema (uno por microservicio del documento). El menú se filtra por rol. */
export const MODULOS: Modulo[] = [
  { ruta: 'inicio', titulo: 'Inicio', icono: 'home', descripcion: 'Resumen de su jornada', roles: [] },
  {
    ruta: 'pacientes',
    titulo: 'Pacientes',
    icono: 'group',
    descripcion: 'Registro, búsqueda e historial de atenciones',
    roles: ['ADMISIONES', 'MEDICO', 'ENFERMERIA', 'AGENDAMIENTO'],
  },
  {
    ruta: 'admisiones',
    titulo: 'Admisiones y triage',
    icono: 'emergency',
    descripcion: 'Lista de espera, triage, cobertura y consentimientos',
    roles: ['ADMISIONES', 'ENFERMERIA', 'MEDICO'],
  },
  {
    ruta: 'agenda',
    titulo: 'Agendamiento',
    icono: 'calendar_month',
    descripcion: 'Citas, disponibilidad y lista de espera',
    roles: ['AGENDAMIENTO', 'ADMISIONES', 'MEDICO'],
  },
  {
    ruta: 'consulta',
    titulo: 'Consulta médica',
    icono: 'stethoscope',
    descripcion: 'Pacientes por atender, historia clínica, diagnóstico y fórmula',
    roles: ['MEDICO'],
  },
  {
    ruta: 'enfermeria',
    titulo: 'Enfermería',
    icono: 'vaccines',
    descripcion: 'Signos vitales, administración de medicamentos y turnos',
    roles: ['ENFERMERIA'],
  },
  {
    ruta: 'laboratorio',
    titulo: 'Laboratorio clínico',
    icono: 'science',
    descripcion: 'Cola de órdenes, muestras, resultados, control de calidad y valores críticos',
    roles: ['LABORATORIO'],
  },
  {
    ruta: 'imagenes',
    titulo: 'Imágenes diagnósticas',
    icono: 'radiology',
    descripcion: 'Estudios, visor e informes radiológicos',
    roles: ['RADIOLOGIA'],
  },
  {
    ruta: 'patologia',
    titulo: 'Patología',
    icono: 'biotech',
    descripcion: 'Casos, macroscopía, microscopía e informes',
    roles: ['PATOLOGIA'],
  },
  {
    ruta: 'facturacion',
    titulo: 'Facturación',
    icono: 'receipt_long',
    descripcion: 'Cargos, cuentas, glosas y cartera',
    roles: ['FACTURACION'],
  },
  {
    ruta: 'auditoria-clinica',
    titulo: 'Auditoría clínica',
    icono: 'fact_check',
    descripcion: 'Revisión de historias y codificación',
    roles: ['AUDITORIA'],
  },
  {
    ruta: 'documentos',
    titulo: 'Gestión documental',
    icono: 'folder_open',
    descripcion: 'Documentos clínicos, versiones y archivo',
    roles: ['ARCHIVO', 'AUDITORIA', 'ADMISIONES'],
  },
  {
    ruta: 'reportes',
    titulo: 'Indicadores',
    icono: 'monitoring',
    descripcion: 'Tiempos de diagnóstico, costos y ROI',
    roles: ['DIRECTIVO'],
  },
  {
    ruta: 'registro-accesos',
    titulo: 'Registro de accesos',
    icono: 'history',
    descripcion: 'Trazabilidad de quién consultó o modificó información',
    roles: ['AUDITORIA'],
  },
];
