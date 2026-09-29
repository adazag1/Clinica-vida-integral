import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Pagina } from '../../core/api/api';
import {
  CambioDemografico,
  FiltrosPaciente,
  Paciente,
  PacienteRequest,
  PacienteResumen,
  PosibleDuplicado,
} from './pacientes.models';

@Injectable({ providedIn: 'root' })
export class PacientesService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/pacientes`;

  buscar(filtros: FiltrosPaciente, pagina = 0, tamano = 20): Observable<Pagina<PacienteResumen>> {
    let params = new HttpParams().set('pagina', pagina).set('tamano', tamano);
    for (const [clave, valor] of Object.entries(filtros)) {
      if (valor?.trim()) params = params.set(clave, valor.trim());
    }
    return this.http.get<Pagina<PacienteResumen>>(this.url, { params });
  }

  obtener(id: number): Observable<Paciente> {
    return this.http.get<Paciente>(`${this.url}/${id}`);
  }

  posiblesDuplicados(datos: PacienteRequest, excluirId?: number): Observable<PosibleDuplicado[]> {
    const params = excluirId ? new HttpParams().set('excluirId', excluirId) : undefined;
    return this.http.post<PosibleDuplicado[]>(`${this.url}/posibles-duplicados`, datos, { params });
  }

  crear(datos: PacienteRequest): Observable<Paciente> {
    return this.http.post<Paciente>(this.url, datos);
  }

  actualizar(id: number, datos: PacienteRequest): Observable<Paciente> {
    return this.http.put<Paciente>(`${this.url}/${id}`, datos);
  }

  cambios(id: number): Observable<CambioDemografico[]> {
    return this.http.get<CambioDemografico[]>(`${this.url}/${id}/cambios`);
  }
}
