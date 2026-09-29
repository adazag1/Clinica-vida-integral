import { Routes } from '@angular/router';

import { autenticadoGuard, invitadoGuard, rolGuard } from './core/auth/auth.guards';
import { MODULOS } from './core/navegacion/modulos';
import { EnConstruccion } from './features/en-construccion/en-construccion';

/** Fase en la que se construye cada módulo pendiente (ver README). */
const FASES: Record<string, string> = {
  agenda: 'Fase 3',
  consulta: 'Fase 4',
  laboratorio: 'Fase 5',
  enfermeria: 'Fase 6',
  imagenes: 'Fase 7',
  patologia: 'Fase 7',
  documentos: 'Fase 7',
  facturacion: 'Fase 8',
  'auditoria-clinica': 'Fase 8',
  reportes: 'Fase 10',
};

const rolesDe = (ruta: string) => MODULOS.find((m) => m.ruta === ruta)?.roles ?? [];

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
