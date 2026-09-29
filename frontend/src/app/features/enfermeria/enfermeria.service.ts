import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import { environment } from '../../../environments/environment';
import {
  EjecucionRequest,
  Entrega,
  EventoAdverso,
  Nota,
  OrdenClinica,
  OrdenClinicaRequest,
  OrdenesPaciente,
  PacienteCenso,
  ResumenTurno,
} from './enfermeria.models';

@Injectable({ providedIn: 'root' })
export class EnfermeriaService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/enfermeria`;

  censo() {
    return this.http.get<PacienteCenso[]>(`${this.url}/censo`);
  }

  ordenesPaciente(pacienteId: number) {
    return this.http.get<OrdenesPaciente>(`${this.url}/pacientes/${pacienteId}/ordenes`);
  }

  deConsulta(consultaId: number) {
    return this.http.get<OrdenClinica[]>(`${this.url}/ordenes`, { params: { consultaId } });
  }

  crearOrden(datos: OrdenClinicaRequest) {
    return this.http.post<OrdenClinica>(`${this.url}/ordenes`, datos);
  }

  suspender(id: number, motivo: string) {
    return this.http.post<OrdenClinica>(`${this.url}/ordenes/${id}/suspender`, { motivo });
  }

  ejecutar(id: number, datos: EjecucionRequest) {
    return this.http.post<OrdenClinica>(`${this.url}/ordenes/${id}/ejecuciones`, datos);
  }

  eventos(pacienteId: number) {
    return this.http.get<EventoAdverso[]>(`${this.url}/pacientes/${pacienteId}/eventos`);
  }

  reportarEvento(
    pacienteId: number,
    datos: { tipo: string; severidad: string; ocurridoEn: string; lugar: string | null; descripcion: string;
      accionesInmediatas: string; medicoNotificado: string | null; ordenId: number | null },
  ) {
    return this.http.post<EventoAdverso>(`${this.url}/pacientes/${pacienteId}/eventos`, datos);
  }

  notas(pacienteId: number) {
    return this.http.get<Nota[]>(`${this.url}/pacientes/${pacienteId}/notas`);
  }

  agregarNota(pacienteId: number, texto: string) {
    return this.http.post<Nota>(`${this.url}/pacientes/${pacienteId}/notas`, { texto });
  }

  resumenTurno(pacienteId: number) {
    return this.http.get<ResumenTurno>(`${this.url}/pacientes/${pacienteId}/resumen-turno`);
  }

  entregas(pacienteId: number) {
    return this.http.get<Entrega[]>(`${this.url}/pacientes/${pacienteId}/entregas`);
  }

  entregar(pacienteId: number, nota: string | null) {
    return this.http.post<Entrega>(`${this.url}/pacientes/${pacienteId}/entregas`, { nota });
  }
}
