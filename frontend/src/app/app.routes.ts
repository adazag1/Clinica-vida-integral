import { Routes } from '@angular/router';

import { autenticadoGuard, invitadoGuard, rolGuard } from './core/auth/auth.guards';
import { MODULOS } from './core/navegacion/modulos';
import { EnConstruccion } from './features/en-construccion/en-construccion';

/** Fase en la que se construye cada módulo pendiente (ver README). */
const FASES: Record<string, string> = {
  pacientes: 'Fase 2',
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
