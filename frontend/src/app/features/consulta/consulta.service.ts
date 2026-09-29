import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import { environment } from '../../../environments/environment';
import {
  Antecedente,
  Consulta,
  ConsultaHistorial,
  Diagnostico,
  Medicamento,
  PacientePorAtender,
  ResumenClinico,
  Signos,
  SignosRequest,
  TipoAntecedente,
  VerificacionFirma,
} from './consulta.models';

@Injectable({ providedIn: 'root' })
export class ConsultaService {
  private readonly http = inject(HttpClient);
  private readonly historia = `${environment.apiUrl}/historia`;
  private readonly consultas = `${environment.apiUrl}/consultas`;

  // ---------- Historia clínica ----------
  resumen(pacienteId: number) {
    return this.http.get<ResumenClinico>(`${this.historia}/${pacienteId}/resumen`);
  }

  historial(pacienteId: number) {
    return this.http.get<ConsultaHistorial[]>(`${this.historia}/${pacienteId}/consultas`);
  }

  antecedentes(pacienteId: number) {
    return this.http.get<Antecedente[]>(`${this.historia}/${pacienteId}/antecedentes`);
  }

  agregarAntecedente(pacienteId: number, datos: { tipo: TipoAntecedente; descripcion: string; detalle: string | null; severidad: string | null }) {
    return this.http.post<Antecedente>(`${this.historia}/${pacienteId}/antecedentes`, datos);
  }

  inactivarAntecedente(id: number, motivo: string) {
    return this.http.post<Antecedente>(`${this.historia}/antecedentes/${id}/inactivar`, { motivo });
  }

  signos(pacienteId: number) {
    return this.http.get<Signos[]>(`${this.historia}/${pacienteId}/signos`);
  }

  registrarSignos(pacienteId: number, datos: SignosRequest) {
    return this.http.post<Signos>(`${this.historia}/${pacienteId}/signos`, datos);
  }

  diagnosticos(texto: string) {
    return this.http.get<Diagnostico[]>(`${this.historia}/diagnosticos`, { params: { texto } });
  }

  medicamentos(texto: string) {
    return this.http.get<Medicamento[]>(`${this.historia}/medicamentos`, { params: { texto } });
  }

  // ---------- Consulta ----------
  porAtender() {
    return this.http.get<PacientePorAtender[]>(`${this.consultas}/por-atender`);
  }

  iniciar(pacienteId: number, episodioId: number | null, citaId: number | null) {
    return this.http.post<Consulta>(this.consultas, { pacienteId, episodioId, citaId });
  }

  obtener(id: number) {
    return this.http.get<Consulta>(`${this.consultas}/${id}`);
  }

  guardar(
    id: number,
    datos: {
      motivoConsulta: string | null;
      enfermedadActual: string | null;
      examenFisico: string | null;
      plan: string | null;
      diagnosticos: { diagnosticoId: number; principal: boolean; confirmado: boolean }[];
    },
  ) {
    return this.http.put<Consulta>(`${this.consultas}/${id}`, datos);
  }

  agregarPrescripcion(
    id: number,
    datos: { medicamentoId: number; dosis: string; frecuencia: string; duracionDias: number; indicaciones: string | null },
  ) {
    return this.http.post<Consulta>(`${this.consultas}/${id}/prescripciones`, datos);
  }

  quitarPrescripcion(id: number, prescripcionId: number) {
    return this.http.delete<Consulta>(`${this.consultas}/${id}/prescripciones/${prescripcionId}`);
  }

  finalizar(id: number, password: string) {
    return this.http.post<Consulta>(`${this.consultas}/${id}/finalizar`, { password });
  }

  verificarFirma(id: number) {
    return this.http.get<VerificacionFirma>(`${this.consultas}/${id}/verificar-firma`);
  }
}
