import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import { environment } from '../../../environments/environment';
import { Documento, DocumentoResumen, PaginaDocumentos, ReporteDocumental, SolicitudArchivo } from './documentos.models';

export interface FiltrosDocumentos {
  documentoPaciente?: string;
  tipo?: string;
  desde?: string;
  hasta?: string;
  profesional?: string;
  servicio?: string;
  texto?: string;
  estado?: string;
}

@Injectable({ providedIn: 'root' })
export class DocumentosService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/documentos`;

  buscar(filtros: FiltrosDocumentos, pagina: number) {
    let params = new HttpParams().set('pagina', pagina).set('tamano', 20);
    Object.entries(filtros).forEach(([k, v]) => {
      if (v && String(v).trim()) params = params.set(k, String(v).trim());
    });
    return this.http.get<PaginaDocumentos>(this.url, { params });
  }

  dePaciente(pacienteId: number) {
    return this.http.get<DocumentoResumen[]>(`${this.url}/pacientes/${pacienteId}`);
  }

  obtener(id: number) {
    return this.http.get<Documento>(`${this.url}/${id}`);
  }

  /** Metadatos y archivo en un solo envío (multipart). */
  cargar(datos: Record<string, string | number | null>, archivo: File | null) {
    const form = new FormData();
    Object.entries(datos).forEach(([k, v]) => {
      if (v !== null && v !== undefined && String(v).trim() !== '') form.append(k, String(v).trim());
    });
    if (archivo) form.append('archivo', archivo, archivo.name);
    return this.http.post<Documento>(this.url, form);
  }

  nuevaVersion(id: number, archivo: File, motivo: string) {
    const form = new FormData();
    form.append('archivo', archivo, archivo.name);
    form.append('motivo', motivo);
    return this.http.post<Documento>(`${this.url}/${id}/versiones`, form);
  }

  /** El contenido se pide con el token y se abre como blob (la descarga queda registrada). */
  contenido(id: number, version: number | null) {
    const params = version ? new HttpParams().set('version', version) : undefined;
    return this.http.get(`${this.url}/${id}/contenido`, { params, responseType: 'blob' });
  }

  cambiarEstado(id: number, estado: string, motivo: string) {
    return this.http.post<Documento>(`${this.url}/${id}/estado`, { estado, motivo });
  }

  ubicar(id: number, datos: { sala: string; estanteria: string; seccion: string | null; caja: string | null }) {
    return this.http.put<Documento>(`${this.url}/${id}/ubicacion`, datos);
  }

  solicitar(datos: { pacienteId: number; documentoId: number | null; motivo: string; urgente: boolean }) {
    return this.http.post<SolicitudArchivo>(`${this.url}/solicitudes`, datos);
  }

  solicitudesAbiertas() {
    return this.http.get<SolicitudArchivo[]>(`${this.url}/solicitudes`);
  }

  misSolicitudes() {
    return this.http.get<SolicitudArchivo[]>(`${this.url}/solicitudes/mias`);
  }

  atender(id: number, accion: 'entregar' | 'rechazar' | 'devolver', respuesta: string | null) {
    return this.http.post<SolicitudArchivo>(`${this.url}/solicitudes/${id}/${accion}`, { respuesta });
  }

  reporte(desde: string, hasta: string) {
    return this.http.get<ReporteDocumental>(`${this.url}/reportes`, { params: { desde, hasta } });
  }
}
