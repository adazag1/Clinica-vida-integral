import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { MODULOS } from '../../core/navegacion/modulos';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);

  protected readonly menuAbierto = signal(false);

  protected readonly modulos = computed(() => {
    this.auth.usuario(); // recalcular si cambia el usuario
    return MODULOS.filter((m) => this.auth.tieneRol(...m.roles));
  });

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((e) => e instanceof NavigationEnd),
      map(() => this.router.url),
    ),
    { initialValue: this.router.url },
  );

  protected readonly titulo = computed(() => {
    const segmento = this.url().split(/[/?#]/)[1];
    return MODULOS.find((m) => m.ruta === segmento)?.titulo ?? '';
  });

  protected readonly iniciales = computed(() =>
    (this.auth.usuario()?.nombreCompleto ?? '')
      .replace(/^(Dr|Dra)\.\s*/, '')
      .split(' ')
      .slice(0, 2)
      .map((p) => p[0])
      .join('')
      .toUpperCase(),
  );

  protected readonly puedeBuscarPacientes = computed(() => this.modulos().some((m) => m.ruta === 'pacientes'));

  protected cerrarMenu(): void {
    this.menuAbierto.set(false);
  }

  protected buscarPaciente(texto: string): void {
    if (texto.trim()) {
      this.router.navigate(['/pacientes'], { queryParams: { texto: texto.trim() } });
    }
  }
}
