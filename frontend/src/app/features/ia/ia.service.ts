import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import { environment } from '../../../environments/environment';
import { AlertaIa, EstadoIa, ReglaIa, RespuestaAsistente, Retroalimentacion, TipoAsistente } from './ia.models';

@Injectable({ providedIn: 'root' })
export class IaService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/ia`;

  alertas(pacienteId: number, historial = false) {
    return this.http.get<AlertaIa[]>(`${this.url}/pacientes/${pacienteId}/alertas`, { params: { historial } });
  }

  retroalimentar(alertaId: number, valor: Retroalimentacion, comentario: string | null) {
    return this.http.post<AlertaIa>(`${this.url}/alertas/${alertaId}/retroalimentacion`, { valor, comentario });
  }

  reglas() {
    return this.http.get<ReglaIa[]>(`${this.url}/reglas`);
  }

  cambiarRegla(codigo: string, activa: boolean) {
    return this.http.put<ReglaIa>(`${this.url}/reglas/${codigo}`, { activa });
  }

  estado() {
    return this.http.get<EstadoIa>(`${this.url}/estado`);
  }

  vistaPrevia(pacienteId: number) {
    return this.http.get<{ texto: string }>(`${this.url}/pacientes/${pacienteId}/asistente/vista-previa`);
  }

  asistente(pacienteId: number, tipo: TipoAsistente) {
    return this.http.post<RespuestaAsistente>(`${this.url}/pacientes/${pacienteId}/asistente`, { tipo });
  }

  dataset() {
    return this.http.get(`${this.url}/dataset`, { responseType: 'blob', observe: 'response' });
  }
}
