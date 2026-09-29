import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth/auth.service';
import {
  AgendaDia,
  Bloqueo,
  Catalogos,
  Cita,
  CitaPublica,
  Cupo,
  EntradaListaEspera,
  FranjaHorario,
  Indicadores,
  PrioridadCita,
  ResultadoCupoLiberado,
} from './agenda.models';

@Injectable({ providedIn: 'root' })
export class AgendaService {
  private readonly http = inject(HttpClient);
  private readonly url = environment.apiUrl;
  private readonly auth = inject(AuthService);
  private catalogosCache?: { usuario: string | undefined; datos: Observable<Catalogos> };

  /**
   * Los catálogos cambian poco: se piden una vez por usuario (incluyen "mi agenda", que depende
   * de quién inició sesión).
   */
  catalogos(): Observable<Catalogos> {
    const usuario = this.auth.usuario()?.username;
    if (this.catalogosCache?.usuario !== usuario || !this.catalogosCache) {
      this.catalogosCache = {
        usuario,
        datos: this.http.get<Catalogos>(`${this.url}/agenda/catalogos`).pipe(shareReplay(1)),
      };
    }
    return this.catalogosCache.datos;
  }

  dia(fecha: string, filtros: { sedeId?: number | null; especialidadId?: number | null; profesionalId?: number | null }) {
    return this.http.get<AgendaDia>(`${this.url}/agenda/dia`, { params: this.params({ fecha, ...filtros }) });
  }

  indicadores(fecha: string, sedeId?: number | null) {
    return this.http.get<Indicadores>(`${this.url}/agenda/indicadores`, { params: this.params({ fecha, sedeId }) });
  }

  disponibilidad(filtros: {
    profesionalId?: number | null;
    especialidadId?: number | null;
    tipoCitaId: number;
    prioridad: PrioridadCita;
    desde: string;
    dias: number;
  }): Observable<Cupo[]> {
    return this.http.get<Cupo[]>(`${this.url}/agenda/disponibilidad`, { params: this.params(filtros) });
  }

  cita(id: number) {
    return this.http.get<Cita>(`${this.url}/citas/${id}`);
  }

  citasDePaciente(pacienteId: number) {
    return this.http.get<Cita[]>(`${this.url}/citas`, { params: { pacienteId } });
  }

  crear(datos: {
    pacienteId: number;
    profesionalId: number;
    tipoCitaId: number;
    inicio: string;
    prioridad: PrioridadCita;
    motivo: string | null;
  }) {
    return this.http.post<Cita>(`${this.url}/citas`, datos);
  }

  confirmar(id: number) {
    return this.http.post<Cita>(`${this.url}/citas/${id}/confirmar`, {});
  }

  cancelar(id: number, motivo: string, detalle: string | null) {
    return this.http.post<ResultadoCupoLiberado>(`${this.url}/citas/${id}/cancelar`, { motivo, detalle });
  }

  reprogramar(id: number, inicio: string, profesionalId: number | null, detalle: string | null) {
    return this.http.post<ResultadoCupoLiberado>(`${this.url}/citas/${id}/reprogramar`, { inicio, profesionalId, detalle });
  }

  asistencia(id: number, asistio: boolean) {
    return this.http.post<Cita>(`${this.url}/citas/${id}/asistencia`, { asistio });
  }

  recordatorio(id: number) {
    return this.http.post<Cita>(`${this.url}/citas/${id}/recordatorio`, {});
  }

  listaEspera() {
    return this.http.get<EntradaListaEspera[]>(`${this.url}/lista-espera`);
  }

  agregarListaEspera(datos: {
    pacienteId: number;
    especialidadId: number;
    profesionalId: number | null;
    tipoCitaId: number;
    prioridad: PrioridadCita;
    motivo: string | null;
  }) {
    return this.http.post<EntradaListaEspera>(`${this.url}/lista-espera`, datos);
  }

  retirarListaEspera(id: number) {
    return this.http.post<void>(`${this.url}/lista-espera/${id}/retirar`, {});
  }

  horario(profesionalId: number) {
    return this.http.get<FranjaHorario[]>(`${this.url}/agenda/horarios/${profesionalId}`);
  }

  guardarHorario(profesionalId: number, franjas: FranjaHorario[]) {
    return this.http.put<FranjaHorario[]>(`${this.url}/agenda/horarios/${profesionalId}`, { franjas });
  }

  bloqueos() {
    return this.http.get<Bloqueo[]>(`${this.url}/agenda/bloqueos`);
  }

  bloquear(datos: { profesionalId: number; inicio: string; fin: string; motivo: string; descripcion: string | null }) {
    return this.http.post<Bloqueo>(`${this.url}/agenda/bloqueos`, datos);
  }

  desbloquear(id: number) {
    return this.http.delete<void>(`${this.url}/agenda/bloqueos/${id}`);
  }

  // RFAGE-010: páginas del paciente, sin sesión
  publica(token: string) {
    return this.http.get<CitaPublica>(`${this.url}/publico/citas/${token}`);
  }

  confirmarPublica(token: string) {
    return this.http.post<CitaPublica>(`${this.url}/publico/citas/${token}/confirmar`, {});
  }

  cancelarPublica(token: string) {
    return this.http.post<CitaPublica>(`${this.url}/publico/citas/${token}/cancelar`, {});
  }

  private params(valores: Record<string, string | number | null | undefined>): HttpParams {
    let p = new HttpParams();
    for (const [k, v] of Object.entries(valores)) {
      if (v !== null && v !== undefined && v !== '') p = p.set(k, v);
    }
    return p;
  }
}
