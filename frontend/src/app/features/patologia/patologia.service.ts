import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import { environment } from '../../../environments/environment';
import { CampoPlantilla, Caso, CasoResumen, PlantillaPatologia, PrioridadCaso, TipoCaso } from './patologia.models';

@Injectable({ providedIn: 'root' })
export class PatologiaService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/patologia`;

  solicitar(datos: { pacienteId: number; consultaId: number | null; tipo: TipoCaso; prioridad: PrioridadCaso; sitioAnatomico: string;
    datosClinicos: string | null; numeroMuestras: number }) {
    return this.http.post<Caso>(`${this.url}/casos`, datos);
  }

  cola(vista: 'PENDIENTES' | 'INFORMADOS', tipo: string | null) {
    let params = new HttpParams().set('vista', vista);
    if (tipo) params = params.set('tipo', tipo);
    return this.http.get<CasoResumen[]>(`${this.url}/casos`, { params });
  }

  deConsulta(consultaId: number) {
    return this.http.get<CasoResumen[]>(`${this.url}/casos`, { params: { consultaId } });
  }

  dePaciente(pacienteId: number) {
    return this.http.get<CasoResumen[]>(`${this.url}/casos/pacientes/${pacienteId}`);
  }

  obtener(id: number) {
    return this.http.get<Caso>(`${this.url}/casos/${id}`);
  }

  recibir(id: number, datos: { documentoRotulo: string; muestrasRecibidas: number; plantillaId: number | null; observacion: string | null }) {
    return this.http.post<Caso>(`${this.url}/casos/${id}/recibir`, datos);
  }

  macroscopia(id: number, valores: Record<string, string>) {
    return this.http.put<Caso>(`${this.url}/casos/${id}/macroscopia`, { valores });
  }

  microscopia(id: number, valores: Record<string, string>, diagnostico: string | null) {
    return this.http.put<Caso>(`${this.url}/casos/${id}/microscopia`, { valores, diagnostico });
  }

  validar(id: number, password: string, motivoCambio: string | null) {
    return this.http.post<Caso>(`${this.url}/casos/${id}/validar`, { password, motivoCambio });
  }

  custodia(id: number, datos: { material: string; accion: string; cantidad: number; ubicacion: string | null; destinatario: string | null;
    motivo: string | null }) {
    return this.http.post<Caso>(`${this.url}/casos/${id}/custodia`, datos);
  }

  plantillas() {
    return this.http.get<PlantillaPatologia[]>(`${this.url}/plantillas`);
  }

  crearPlantilla(datos: { tipo: TipoCaso; servicio: string | null; nombre: string; campos: CampoPlantilla[] }) {
    return this.http.post<PlantillaPatologia>(`${this.url}/plantillas`, datos);
  }

  actualizarPlantilla(id: number, datos: { tipo: TipoCaso; servicio: string | null; nombre: string; campos: CampoPlantilla[] }) {
    return this.http.put<PlantillaPatologia>(`${this.url}/plantillas/${id}`, datos);
  }
}
