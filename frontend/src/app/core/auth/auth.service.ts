import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

import { environment } from '../../../environments/environment';
import { LoginResponse, Rol, UsuarioActual } from './auth.models';

const CLAVE_SESION = 'cvi.sesion';

interface SesionGuardada {
  token: string;
  expiraEn: string;
  usuario: UsuarioActual;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly sesion = signal<SesionGuardada | null>(this.leerSesion());

  readonly usuario = computed(() => this.sesion()?.usuario ?? null);
  readonly autenticado = computed(() => this.sesion() !== null);

  get token(): string | null {
    return this.sesion()?.token ?? null;
  }

  /**
   * "Recordarme" guarda la sesión en localStorage (sobrevive al cierre del navegador);
   * si no, en sessionStorage (se borra al cerrar la pestaña).
   */
  login(usuario: string, password: string, recordarme: boolean): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${environment.apiUrl}/auth/login`, { usuario, password })
      .pipe(
        tap((res) => {
          const sesion: SesionGuardada = { token: res.token, expiraEn: res.expiraEn, usuario: res.usuario };
          this.limpiarAlmacenamiento();
          (recordarme ? localStorage : sessionStorage).setItem(CLAVE_SESION, JSON.stringify(sesion));
          this.sesion.set(sesion);
        }),
      );
  }

  logout(): void {
    this.limpiarAlmacenamiento();
    this.sesion.set(null);
    this.router.navigate(['/login']);
  }

  tieneRol(...roles: Rol[]): boolean {
    const rol = this.usuario()?.rol;
    return !!rol && (roles.length === 0 || roles.includes(rol) || rol === 'ADMIN');
  }

  private leerSesion(): SesionGuardada | null {
    try {
      const crudo = localStorage.getItem(CLAVE_SESION) ?? sessionStorage.getItem(CLAVE_SESION);
      if (!crudo) return null;
      const sesion = JSON.parse(crudo) as SesionGuardada;
      if (new Date(sesion.expiraEn).getTime() <= Date.now()) {
        this.limpiarAlmacenamiento();
        return null;
      }
      return sesion;
    } catch {
      return null;
    }
  }

  private limpiarAlmacenamiento(): void {
    try {
      localStorage.removeItem(CLAVE_SESION);
      sessionStorage.removeItem(CLAVE_SESION);
    } catch {
      /* almacenamiento no disponible */
    }
  }
}
