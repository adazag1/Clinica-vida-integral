import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  EstadoCobertura,
  Episodio,
  ListaEspera,
  SignosVitales,
  SugerenciaTriage,
  TipoAtencion,
  TipoConsentimiento,
} from './admisiones.models';

@Injectable({ providedIn: 'root' })
export class AdmisionesService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/episodios`;

  listaEspera(): Observable<ListaEspera> {
    return this.http.get<ListaEspera>(`${this.url}/lista-espera`);
  }

  dePaciente(pacienteId: number): Observable<Episodio[]> {
    return this.http.get<Episodio[]>(this.url, { params: { pacienteId } });
  }

  obtener(id: number): Observable<Episodio> {
    return this.http.get<Episodio>(`${this.url}/${id}`);
  }

  crear(pacienteId: number, tipoAtencion: TipoAtencion, motivoConsulta: string): Observable<Episodio> {
    return this.http.post<Episodio>(this.url, { pacienteId, tipoAtencion, motivoConsulta });
  }

  cobertura(
    id: number,
    datos: {
      resultado: EstadoCobertura;
      aseguradora: string | null;
      plan: string | null;
      numeroAutorizacion: string | null;
      observacion: string | null;
    },
  ): Observable<Episodio> {
    return this.http.put<Episodio>(`${this.url}/${id}/cobertura`, datos);
  }

  excepcionUrgencia(id: number, justificacion: string): Observable<Episodio> {
    return this.http.put<Episodio>(`${this.url}/${id}/excepcion-urgencia`, { justificacion });
  }

  consentimiento(
    id: number,
    datos: {
      tipo: TipoConsentimiento;
      otorgadoPor: string;
      parentesco: string | null;
      aceptado: boolean;
      observaciones: string | null;
    },
  ): Observable<Episodio> {
    return this.http.post<Episodio>(`${this.url}/${id}/consentimientos`, datos);
  }

  sugerirTriage(signos: SignosVitales, edad: number | null): Observable<SugerenciaTriage> {
    return this.http.post<SugerenciaTriage>(`${this.url}/triage/sugerencia`, { signos, edad });
  }

  triage(
    id: number,
    datos: { signos: SignosVitales; sintomaPrincipal: string; nivelAsignado: number; justificacionAjuste: string | null },
  ): Observable<Episodio> {
    return this.http.put<Episodio>(`${this.url}/${id}/triage`, datos);
  }

  enviarAConsulta(id: number): Observable<Episodio> {
    return this.http.post<Episodio>(`${this.url}/${id}/enviar-a-consulta`, {});
  }

  anular(id: number, justificacion: string): Observable<Episodio> {
    return this.http.post<Episodio>(`${this.url}/${id}/anular`, { justificacion });
  }
}
