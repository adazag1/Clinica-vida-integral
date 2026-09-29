import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { ApiError } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly form = inject(NonNullableFormBuilder).group({
    usuario: ['', Validators.required],
    password: ['', Validators.required],
    recordarme: [false],
  });

  protected readonly cargando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  protected readonly verPassword = signal(false);

  protected enviar(): void {
    this.aviso.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { usuario, password, recordarme } = this.form.getRawValue();
    this.cargando.set(true);
    this.error.set(null);

    this.auth.login(usuario, password, recordarme).subscribe({
      next: () => this.router.navigate(['/inicio']),
      error: (e: HttpErrorResponse) => {
        this.cargando.set(false);
        const api = e.error as ApiError | null;
        this.error.set(
          e.status === 0
            ? 'No hay conexión con el servidor. Intente de nuevo en unos minutos.'
            : (api?.mensaje ?? 'No fue posible iniciar sesión.'),
        );
      },
    });
  }

  protected invalido(campo: 'usuario' | 'password'): boolean {
    const c = this.form.controls[campo];
    return c.invalid && c.touched;
  }

  protected olvidoPassword(): void {
    this.aviso.set('Para restablecer su contraseña comuníquese con la mesa de ayuda de Sistemas (ext. 1100).');
  }

  protected solicitarAcceso(): void {
    this.aviso.set('Las cuentas nuevas las crea el área de Sistemas previa autorización del jefe de su servicio.');
  }
}
