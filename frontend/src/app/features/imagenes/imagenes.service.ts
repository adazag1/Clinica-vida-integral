import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  Estudio,
  EstudioResumen,
  MetricaImagenes,
  PrioridadEstudio,
  Radiologo,
  TipoEstudio,
  VistaTrabajo,
} from './imagenes.models';

@Injectable({ providedIn: 'root' })
export class ImagenesService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/imagenes`;
  private tiposCache?: Observable<TipoEstudio[]>;

  tipos(refrescar = false): Observable<TipoEstudio[]> {
    if (refrescar || !this.tiposCache) {
      this.tiposCache = this.http.get<TipoEstudio[]>(`${this.url}/tipos`).pipe(shareReplay(1));
    }
    return this.tiposCache;
  }

  configurarTipo(id: number, datos: Omit<TipoEstudio, 'id' | 'codigo' | 'nombre' | 'modalidad' | 'modalidadDescripcion' | 'region' | 'modificadoPor' | 'modificadoEn'>) {
    return this.http.put<TipoEstudio>(`${this.url}/tipos/${id}`, datos);
  }

  radiologos() {
    return this.http.get<Radiologo[]>(`${this.url}/radiologos`);
  }

  ordenar(datos: { pacienteId: number; consultaId: number | null; tipoEstudioId: number; prioridad: PrioridadEstudio; motivo: string;
    diagnosticoPresuntivo: string | null }) {
    return this.http.post<Estudio>(`${this.url}/estudios`, datos);
  }

  listaTrabajo(vista: VistaTrabajo, modalidad: string | null, prioridad: string | null) {
    let params = new HttpParams().set('vista', vista);
    if (modalidad) params = params.set('modalidad', modalidad);
    if (prioridad) params = params.set('prioridad', prioridad);
    return this.http.get<EstudioResumen[]>(`${this.url}/estudios`, { params });
  }

  dePaciente(pacienteId: number) {
    return this.http.get<EstudioResumen[]>(`${this.url}/estudios/pacientes/${pacienteId}`);
  }

  deConsulta(consultaId: number) {
    return this.http.get<EstudioResumen[]>(`${this.url}/estudios`, { params: { consultaId } });
  }

  obtener(id: number) {
    return this.http.get<Estudio>(`${this.url}/estudios/${id}`);
  }

  cargarImagenes(id: number, archivos: File[], studyInstanceUid: string | null) {
    const datos = new FormData();
    archivos.forEach((a) => datos.append('archivos', a, a.name));
    if (studyInstanceUid) datos.append('studyInstanceUid', studyInstanceUid);
    return this.http.post<Estudio>(`${this.url}/estudios/${id}/imagenes`, datos);
  }

  /** La imagen se pide con el token (no sirve un <img src> directo) y se muestra como blob. */
  imagen(id: number, imagenId: number) {
    return this.http.get(`${this.url}/estudios/${id}/imagenes/${imagenId}`, { responseType: 'blob' });
  }

  marcarClave(id: number, imagenId: number, clave: boolean, descripcion: string | null) {
    return this.http.put<Estudio>(`${this.url}/estudios/${id}/imagenes/${imagenId}`, { clave, descripcion });
  }

  guardarInforme(id: number, datos: { hallazgos: string | null; impresion: string | null; recomendaciones: string | null }) {
    return this.http.put<Estudio>(`${this.url}/estudios/${id}/informe`, datos);
  }

  firmar(id: number, datos: { password: string; critico: boolean; criticoReceptor: string | null; criticoMedio: string | null }) {
    return this.http.post<Estudio>(`${this.url}/estudios/${id}/firmar`, datos);
  }

  enmendar(id: number, motivo: string) {
    return this.http.post<Estudio>(`${this.url}/estudios/${id}/enmendar`, { motivo });
  }

  asignar(id: number, usuario: string) {
    return this.http.post<Estudio>(`${this.url}/estudios/${id}/asignar`, { usuario });
  }

  pedirSegundaOpinion(id: number, asignadaA: string, pregunta: string) {
    return this.http.post<Estudio>(`${this.url}/estudios/${id}/segunda-opinion`, { asignadaA, pregunta });
  }

  responderSegundaOpinion(id: number, opinionId: number, concordancia: string, comentario: string | null) {
    return this.http.post<Estudio>(`${this.url}/estudios/${id}/segunda-opinion/${opinionId}`, { concordancia, comentario });
  }

  bloquear(id: number, motivo: string) {
    return this.http.post<Estudio>(`${this.url}/estudios/${id}/bloquear`, { motivo });
  }

  desbloquear(id: number, motivo: string) {
    return this.http.post<Estudio>(`${this.url}/estudios/${id}/desbloquear`, { motivo });
  }

  metricas(desde: string, hasta: string) {
    return this.http.get<MetricaImagenes[]>(`${this.url}/metricas`, { params: { desde, hasta } });
  }
}
