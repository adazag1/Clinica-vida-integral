import { Routes } from '@angular/router';

import { autenticadoGuard, invitadoGuard, rolGuard } from './core/auth/auth.guards';
import { Rol } from './core/auth/auth.models';
import { MODULOS } from './core/navegacion/modulos';
import { EnConstruccion } from './features/en-construccion/en-construccion';

/** Fase en la que se construye cada módulo pendiente (ver README). */
const FASES: Record<string, string> = {
  imagenes: 'Fase 7',
  patologia: 'Fase 7',
  documentos: 'Fase 7',
  facturacion: 'Fase 8',
  'auditoria-clinica': 'Fase 8',
  reportes: 'Fase 10',
};

const rolesDe = (ruta: string) => MODULOS.find((m) => m.ruta === ruta)?.roles ?? [];

/** RNFMT-003: la historia clínica solo la ven el personal asistencial y auditoría. */
const HISTORIA: Rol[] = ['MEDICO', 'ENFERMERIA', 'AUDITORIA'];

const modulosPendientes: Routes = MODULOS.filter((m) => FASES[m.ruta]).map((m) => ({
  path: m.ruta,
  component: EnConstruccion,
  canActivate: [rolGuard],
  data: { roles: m.roles, titulo: m.titulo, fase: FASES[m.ruta] },
}));

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [invitadoGuard],
    loadComponent: () => import('./features/login/login').then((m) => m.Login),
  },
  {
    // RFAGE-010: enlace del recordatorio; el paciente no tiene cuenta en el sistema
    path: 'cita/:token',
    loadComponent: () => import('./features/agenda/cita-publica/cita-publica').then((m) => m.CitaPublica),
  },
  {
    path: '',
    canActivate: [autenticadoGuard],
    loadComponent: () => import('./layout/shell/shell').then((m) => m.Shell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'inicio' },
      { path: 'inicio', loadComponent: () => import('./features/inicio/inicio').then((m) => m.Inicio) },
      {
        path: 'pacientes',
        canActivate: [rolGuard],
        data: { roles: rolesDe('pacientes') },
        children: [
          {
            path: '',
            loadComponent: () => import('./features/pacientes/lista/pacientes-lista').then((m) => m.PacientesLista),
          },
          {
            path: 'nuevo',
            canActivate: [rolGuard],
            data: { roles: ['ADMISIONES'] },
            loadComponent: () =>
              import('./features/pacientes/formulario/paciente-formulario').then((m) => m.PacienteFormulario),
          },
          {
            path: ':id',
            loadComponent: () => import('./features/pacientes/detalle/paciente-detalle').then((m) => m.PacienteDetalle),
          },
          {
            path: ':id/editar',
            canActivate: [rolGuard],
            data: { roles: ['ADMISIONES'] },
            loadComponent: () =>
              import('./features/pacientes/formulario/paciente-formulario').then((m) => m.PacienteFormulario),
          },
        ],
      },
      {
        path: 'admisiones',
        canActivate: [rolGuard],
        data: { roles: rolesDe('admisiones') },
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./features/admisiones/lista-espera/lista-espera').then((m) => m.ListaEspera),
          },
          {
            path: ':id',
            loadComponent: () =>
              import('./features/admisiones/episodio/episodio-detalle').then((m) => m.EpisodioDetalle),
          },
        ],
      },
      {
        path: 'agenda',
        canActivate: [rolGuard],
        data: { roles: rolesDe('agenda') },
        children: [
          { path: '', loadComponent: () => import('./features/agenda/agenda').then((m) => m.Agenda) },
          {
            path: 'lista-espera',
            loadComponent: () =>
              import('./features/agenda/lista-espera/lista-espera-citas').then((m) => m.ListaEsperaCitas),
          },
          {
            path: 'configuracion',
            canActivate: [rolGuard],
            data: { roles: ['AGENDAMIENTO'] },
            loadComponent: () =>
              import('./features/agenda/configuracion/configuracion-agenda').then((m) => m.ConfiguracionAgenda),
          },
        ],
      },
      {
        path: 'consulta',
        children: [
          {
            path: '',
            canActivate: [rolGuard],
            data: { roles: ['MEDICO'] },
            loadComponent: () => import('./features/consulta/lista/por-atender').then((m) => m.PorAtender),
          },
          {
            path: ':id',
            canActivate: [rolGuard],
            data: { roles: HISTORIA },
            loadComponent: () => import('./features/consulta/pagina/consulta-pagina').then((m) => m.ConsultaPagina),
          },
        ],
      },
      {
        path: 'laboratorio',
        children: [
          {
            path: '',
            canActivate: [rolGuard],
            data: { roles: ['LABORATORIO'] },
            loadComponent: () => import('./features/laboratorio/cola/cola-laboratorio').then((m) => m.ColaLaboratorio),
          },
          {
            path: 'configuracion',
            canActivate: [rolGuard],
            data: { roles: ['LABORATORIO'] },
            loadComponent: () =>
              import('./features/laboratorio/configuracion/rangos-laboratorio').then((m) => m.RangosLaboratorio),
          },
          {
            path: 'ordenes/:id',
            canActivate: [rolGuard],
            data: { roles: ['LABORATORIO', ...HISTORIA] },
            loadComponent: () => import('./features/laboratorio/orden/orden-laboratorio').then((m) => m.OrdenLaboratorio),
          },
        ],
      },
      {
        path: 'enfermeria',
        children: [
          {
            path: '',
            canActivate: [rolGuard],
            data: { roles: ['ENFERMERIA', 'MEDICO'] },
            loadComponent: () => import('./features/enfermeria/censo/censo-enfermeria').then((m) => m.CensoEnfermeria),
          },
          {
            path: 'pacientes/:id',
            canActivate: [rolGuard],
            data: { roles: HISTORIA },
            loadComponent: () =>
              import('./features/enfermeria/paciente/paciente-enfermeria').then((m) => m.PacienteEnfermeria),
          },
        ],
      },
      {
        path: 'historia/:pacienteId',
        canActivate: [rolGuard],
        data: { roles: HISTORIA },
        loadComponent: () => import('./features/consulta/historia/historia-clinica').then((m) => m.HistoriaClinica),
      },
      {
        path: 'registro-accesos',
        canActivate: [rolGuard],
        data: { roles: ['AUDITORIA'] },
        loadComponent: () =>
          import('./features/registro-accesos/registro-accesos').then((m) => m.RegistroAccesos),
      },
      ...modulosPendientes,
    ],
  },
  { path: '**', redirectTo: '' },
];
