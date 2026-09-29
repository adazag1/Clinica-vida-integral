import { DatePipe } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { environment } from '../../../environments/environment';

interface RegistroAuditoria {
  id: number;
  fecha: string;
  usuario: string | null;
  accion: string;
  recurso: string;
  metodoHttp: string | null;
  estadoHttp: number | null;
  ip: string | null;
  detalle: string | null;
}

interface Pagina<T> {
  content: T[];
  page: { totalElements: number; totalPages: number; number: number };
}

@Component({
  selector: 'app-registro-accesos',
  imports: [DatePipe, FormsModule],
  templateUrl: './registro-accesos.html',
  styleUrl: './registro-accesos.scss',
})
export class RegistroAccesos {
  private readonly http = inject(HttpClient);

  protected readonly registros = signal<RegistroAuditoria[]>([]);
  protected readonly pagina = signal(0);
  protected readonly totalPaginas = signal(0);
  protected readonly cargando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected filtroUsuario = '';

  constructor() {
    this.cargar(0);
  }

  protected cargar(pagina: number): void {
    this.cargando.set(true);
    this.error.set(null);
    let params = new HttpParams().set('pagina', pagina).set('tamano', 25);
    if (this.filtroUsuario.trim()) params = params.set('usuario', this.filtroUsuario.trim());

    this.http.get<Pagina<RegistroAuditoria>>(`${environment.apiUrl}/auditoria`, { params }).subscribe({
      next: (res) => {
        this.registros.set(res.content);
        this.pagina.set(res.page.number);
        this.totalPaginas.set(res.page.totalPages);
        this.cargando.set(false);
      },
      error: () => {
        this.error.set('No fue posible cargar el registro de accesos.');
        this.cargando.set(false);
      },
    });
  }

  protected claseAccion(accion: string): string {
    if (accion.includes('FALLIDO') || accion.includes('BLOQUEADO') || accion === 'ELIMINACION') return 'chip--critico';
    if (accion === 'CREACION' || accion === 'MODIFICACION') return 'chip--alerta';
    if (accion === 'LOGIN_EXITOSO') return 'chip--normal';
    return 'chip--info';
  }
}
