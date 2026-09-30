import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import { environment } from '../../../environments/environment';
import { CuentaResumen } from '../facturacion/facturacion.models';
import { CambioCatalogo, Codificacion, DiagnosticoCatalogo, Expediente, Tablero } from './auditoria.models';

@Injectable({ providedIn: 'root' })
export class AuditoriaClinicaService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/auditoria-clinica`;

  cola(estado: string | null) {
    const params = estado ? new HttpParams().set('estado', estado) : undefined;
    return this.http.get<CuentaResumen[]>(`${this.url}/cuentas`, { params });
  }

  expediente(id: number) {
    return this.http.get<Expediente>(`${this.url}/cuentas/${id}`);
  }

  codificar(id: number, diagnosticoPrincipal: string, relacionados: string[], motivo: string | null) {
    return this.http.put<Codificacion>(`${this.url}/cuentas/${id}/codificacion`, { diagnosticoPrincipal, relacionados, motivo });
  }

  aprobar(id: number) {
    return this.http.post<Expediente>(`${this.url}/cuentas/${id}/aprobar`, {});
  }

  devolver(id: number, motivo: string, detalle: string, dirigidaA: string) {
    return this.http.post<Expediente>(`${this.url}/cuentas/${id}/devolver`, { motivo, detalle, dirigidaA });
  }

  tablero(desde: string, hasta: string) {
    return this.http.get<Tablero>(`${this.url}/tablero`, { params: { desde, hasta } });
  }

  diagnosticos(texto: string) {
    return this.http.get<DiagnosticoCatalogo[]>(`${this.url}/catalogos/diagnosticos`, { params: { texto } });
  }

  crearDiagnostico(codigo: string, nombre: string) {
    return this.http.post<DiagnosticoCatalogo>(`${this.url}/catalogos/diagnosticos`, { codigo, nombre });
  }

  actualizarDiagnostico(id: number, nombre: string, activo: boolean, motivo: string) {
    return this.http.put<DiagnosticoCatalogo>(`${this.url}/catalogos/diagnosticos/${id}`, { nombre, activo, motivo });
  }

  version(catalogo: string) {
    return this.http.get<{ catalogo: string; version: number; actualizadoEn: string; actualizadoPor: string }>(`${this.url}/catalogos/${catalogo}/version`);
  }

  historial(catalogo: string) {
    return this.http.get<CambioCatalogo[]>(`${this.url}/catalogos/${catalogo}/historial`);
  }
}
