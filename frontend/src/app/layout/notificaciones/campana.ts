import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, DestroyRef, ElementRef, HostListener, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { EMPTY, catchError, switchMap, timer } from 'rxjs';

import { environment } from '../../../environments/environment';

interface Notificacion {
  id: number;
  titulo: string;
  mensaje: string;
  enlace: string | null;
  fecha: string;
  leida: boolean;
}

interface Bandeja {
  noLeidas: number;
  notificaciones: Notificacion[];
}

const REFRESCO_MS = 30_000;

/** Campana de avisos internos (paciente listo, preparación de procedimientos, cupos reasignados...). */
@Component({
  selector: 'app-campana',
  imports: [DatePipe],
  template: `
    <button type="button" class="campana" (click)="abierta.set(!abierta())" [attr.aria-expanded]="abierta()"
      [attr.aria-label]="'Notificaciones' + (bandeja().noLeidas ? ', ' + bandeja().noLeidas + ' sin leer' : '')">
      <span class="icono">notifications</span>
      @if (bandeja().noLeidas) {
        <span class="contador">{{ bandeja().noLeidas > 9 ? '9+' : bandeja().noLeidas }}</span>
      }
    </button>
    @if (abierta()) {
      <div class="menu" role="dialog" aria-label="Notificaciones">
        <div class="menu__cabecera">
          <strong>Notificaciones</strong>
          @if (bandeja().noLeidas) {
            <button type="button" class="enlace" (click)="marcarTodas()">Marcar todas como leídas</button>
          }
        </div>
        @for (n of bandeja().notificaciones; track n.id) {
          <button type="button" class="aviso" [class.aviso--nuevo]="!n.leida" (click)="abrir(n)">
            <strong>{{ n.titulo }}</strong>
            <span>{{ n.mensaje }}</span>
            <small>{{ n.fecha | date: 'dd/MM h:mm a' }}</small>
          </button>
        } @empty {
          <p class="vacio">No tiene notificaciones.</p>
        }
      </div>
    }
  `,
  styles: `
    :host { position: relative; }
    .campana { position: relative; display: grid; place-items: center; width: 44px; height: 44px; border: 0;
      border-radius: 50%; background: none; cursor: pointer; color: var(--c-texto-2); }
    .campana:hover { background: var(--c-azul-suave); }
    .contador { position: absolute; top: 4px; right: 2px; min-width: 18px; height: 18px; padding: 0 4px; border-radius: 9px;
      background: var(--c-critico); color: #fff; font-size: 11px; font-weight: 700; display: grid; place-items: center; }
    .menu { position: absolute; right: 0; top: 50px; z-index: 50; width: min(380px, 92vw); max-height: 70vh; overflow-y: auto;
      background: var(--c-superficie); border: 1px solid var(--c-borde); border-radius: var(--radio); box-shadow: var(--sombra); }
    .menu__cabecera { display: flex; justify-content: space-between; align-items: center; padding: 12px 14px;
      border-bottom: 1px solid var(--c-borde); position: sticky; top: 0; background: var(--c-superficie); }
    .menu__cabecera .enlace { font-size: 13px; }
    .aviso { display: flex; flex-direction: column; gap: 2px; width: 100%; padding: 10px 14px; border: 0;
      border-bottom: 1px solid var(--c-borde); background: none; text-align: left; font: inherit; cursor: pointer; }
    .aviso:hover { background: #f8fafd; }
    .aviso span { font-size: 13px; color: var(--c-texto-2); }
    .aviso small { font-size: 11px; color: var(--c-texto-3); }
    .aviso--nuevo { background: var(--c-azul-suave); }
    .aviso--nuevo:hover { background: #dde9fb; }
    .vacio { padding: 16px; margin: 0; color: var(--c-texto-2); }
  `,
})
export class Campana {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly elemento = inject(ElementRef);
  private readonly url = `${environment.apiUrl}/notificaciones`;

  protected readonly bandeja = signal<Bandeja>({ noLeidas: 0, notificaciones: [] });
  protected readonly abierta = signal(false);

  constructor() {
    timer(0, REFRESCO_MS)
      .pipe(
        switchMap(() => this.http.get<Bandeja>(this.url).pipe(catchError(() => EMPTY))),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((b) => this.bandeja.set(b));
  }

  @HostListener('document:click', ['$event'])
  protected cerrarAlHacerClicFuera(evento: MouseEvent): void {
    if (this.abierta() && !this.elemento.nativeElement.contains(evento.target)) this.abierta.set(false);
  }

  protected abrir(n: Notificacion): void {
    this.abierta.set(false);
    if (!n.leida) {
      this.http.post<void>(`${this.url}/${n.id}/leida`, {}).subscribe(() => this.recargar());
    }
    if (n.enlace) this.router.navigateByUrl(n.enlace);
  }

  protected marcarTodas(): void {
    this.http.post<void>(`${this.url}/leidas`, {}).subscribe(() => this.recargar());
  }

  private recargar(): void {
    this.http.get<Bandeja>(this.url).subscribe((b) => this.bandeja.set(b));
  }
}
