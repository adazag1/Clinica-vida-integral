import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ControlCalidad, EstadoOrden, Examen, Orden, OrdenResumen, PrioridadLab, Rango, ResultadoReciente } from './laboratorio.models';

@Injectable({ providedIn: 'root' })
export class LaboratorioService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/laboratorio`;
  private catalogoCache?: Observable<Examen[]>;

  catalogo(refrescar = false): Observable<Examen[]> {
    if (refrescar || !this.catalogoCache) {
      this.catalogoCache = this.http.get<Examen[]>(`${this.url}/examenes`).pipe(shareReplay(1));
    }
    return this.catalogoCache;
  }

  actualizarRango(id: number, datos: { refMin: number | null; refMax: number | null; criticoMin: number | null; criticoMax: number | null }) {
    return this.http.put<Rango>(`${this.url}/rangos/${id}`, datos);
  }

  crearOrden(datos: { pacienteId: number; consultaId: number | null; prioridad: PrioridadLab; examenIds: number[]; indicaciones: string | null }) {
    return this.http.post<Orden>(`${this.url}/ordenes`, datos);
  }

  cola(estado: EstadoOrden | null, prioridad: PrioridadLab | null) {
    let params = new HttpParams();
    if (estado) params = params.set('estado', estado);
    if (prioridad) params = params.set('prioridad', prioridad);
    return this.http.get<OrdenResumen[]>(`${this.url}/ordenes`, { params });
  }

  dePaciente(pacienteId: number) {
    return this.http.get<OrdenResumen[]>(`${this.url}/pacientes/${pacienteId}/ordenes`);
  }

  recientes(pacienteId: number) {
    return this.http.get<ResultadoReciente[]>(`${this.url}/pacientes/${pacienteId}/resultados-recientes`);
  }

  obtener(id: number) {
    return this.http.get<Orden>(`${this.url}/ordenes/${id}`);
  }

  recibirMuestra(id: number, tomadaPor: string, tomadaEn: string | null) {
    return this.http.post<Orden>(`${this.url}/ordenes/${id}/muestra`, { tomadaPor, tomadaEn });
  }

  rechazar(id: number, motivo: string, detalle: string | null) {
    return this.http.post<Orden>(`${this.url}/ordenes/${id}/rechazar`, { motivo, detalle });
  }

  nuevaMuestra(id: number) {
    return this.http.post<Orden>(`${this.url}/ordenes/${id}/nueva-muestra`, {});
  }

  guardarResultados(
    id: number,
    datos: {
      trazabilidad: { ordenExamenId: number; equipo: string | null; loteReactivo: string | null; calibracion: string | null }[];
      valores: { analitoId: number; valor: number }[];
    },
  ) {
    return this.http.put<Orden>(`${this.url}/ordenes/${id}/resultados`, datos);
  }

  repetir(id: number, analitoId: number, valor: number, motivo: string, detalle: string | null) {
    return this.http.post<Orden>(`${this.url}/ordenes/${id}/resultados/${analitoId}/repetir`, { valor, motivo, detalle });
  }

  validar(id: number, contactoCritico: string | null, medioCritico: string | null) {
    return this.http.post<Orden>(`${this.url}/ordenes/${id}/validar`, { contactoCritico, medioCritico });
  }

  controles() {
    return this.http.get<ControlCalidad[]>(`${this.url}/control-calidad`);
  }

  registrarControl(datos: { area: string; equipo: string; niveles: string; loteControl: string; aceptado: boolean; observacion: string | null }) {
    return this.http.post<ControlCalidad>(`${this.url}/control-calidad`, datos);
  }
}
