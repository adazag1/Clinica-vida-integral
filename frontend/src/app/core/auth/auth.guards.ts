import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { Rol } from './auth.models';
import { AuthService } from './auth.service';

/** Exige sesión iniciada. */
export const autenticadoGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.autenticado() || inject(Router).createUrlTree(['/login']);
};

/** Evita volver al login si ya hay sesión. */
export const invitadoGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return !auth.autenticado() || inject(Router).createUrlTree(['/inicio']);
};

/** Restringe la ruta a los roles de `data.roles` (RNF Seguridad por Rol). */
export const rolGuard: CanActivateFn = (route) => {
  const roles = (route.data['roles'] as Rol[] | undefined) ?? [];
  return inject(AuthService).tieneRol(...roles) || inject(Router).createUrlTree(['/inicio']);
};
