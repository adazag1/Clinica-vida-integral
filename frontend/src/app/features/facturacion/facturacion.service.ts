import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import { environment } from '../../../environments/environment';
import { Cartera, Contrato, Cuenta, CuentaResumen, Liquidacion, Servicio } from './facturacion.models';

@Injectable({ providedIn: 'root' })
export class FacturacionService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/facturacion`;

  cuentas(estado: string | null, texto: string) {
    let params = new HttpParams();
    if (estado) params = params.set('estado', estado);
    if (texto.trim()) params = params.set('texto', texto.trim());
    return this.http.get<CuentaResumen[]>(`${this.url}/cuentas`, { params });
  }

  abrir(pacienteId: number) {
    return this.http.post<Cuenta>(`${this.url}/cuentas`, { pacienteId });
  }

  obtener(id: number) {
    return this.http.get<Cuenta>(`${this.url}/cuentas/${id}`);
  }

  agregarCargo(id: number, datos: { servicioId: number; cantidad: number; soporte: string | null; soporteEnlace: string | null }) {
    return this.http.post<Cuenta>(`${this.url}/cuentas/${id}/cargos`, datos);
  }

  registrarSoporte(id: number, cargoId: number, soporte: string) {
    return this.http.put<Cuenta>(`${this.url}/cuentas/${id}/cargos/${cargoId}/soporte`, { soporte });
  }

  anularCargo(id: number, cargoId: number, motivo: string) {
    return this.http.post<Cuenta>(`${this.url}/cuentas/${id}/cargos/${cargoId}/anular`, { motivo });
  }

  autorizacion(id: number, numero: string) {
    return this.http.put<Cuenta>(`${this.url}/cuentas/${id}/autorizacion`, { numero });
  }

  enviarAAuditoria(id: number, respuesta: string | null) {
    return this.http.post<Cuenta>(`${this.url}/cuentas/${id}/enviar-auditoria`, { respuesta });
  }

  simular(id: number) {
    return this.http.get<Liquidacion>(`${this.url}/cuentas/${id}/simulacion`);
  }

  facturar(id: number) {
    return this.http.post<Cuenta>(`${this.url}/cuentas/${id}/facturar`, {});
  }

  xml(id: number) {
    return this.http.get(`${this.url}/cuentas/${id}/factura.xml`, { responseType: 'blob' });
  }

  registrarGlosa(id: number, datos: { concepto: string; codigo: string; descripcion: string; valor: number; cargoId: number | null; recibidaEn: string }) {
    return this.http.post<Cuenta>(`${this.url}/cuentas/${id}/glosas`, datos);
  }

  responderGlosa(id: number, glosaId: number, valorAceptado: number, respuesta: string) {
    return this.http.post<Cuenta>(`${this.url}/cuentas/${id}/glosas/${glosaId}/respuesta`, { valorAceptado, respuesta });
  }

  registrarPago(id: number, datos: { valor: number; fecha: string; medio: string; referencia: string | null }) {
    return this.http.post<Cuenta>(`${this.url}/cuentas/${id}/pagos`, datos);
  }

  cerrar(id: number) {
    return this.http.post<Cuenta>(`${this.url}/cuentas/${id}/cerrar`, {});
  }

  cartera() {
    return this.http.get<Cartera>(`${this.url}/cartera`);
  }

  servicios() {
    return this.http.get<Servicio[]>(`${this.url}/servicios`);
  }

  guardarServicio(id: number | null, datos: { codigo: string; nombre: string; tipo: string; tarifaBase: number; requiereAutorizacion: boolean; activo: boolean }) {
    return id ? this.http.put<Servicio>(`${this.url}/servicios/${id}`, datos) : this.http.post<Servicio>(`${this.url}/servicios`, datos);
  }

  contratos() {
    return this.http.get<Contrato[]>(`${this.url}/contratos`);
  }

  guardarContrato(id: number | null, datos: Record<string, unknown>) {
    return id ? this.http.put<Contrato>(`${this.url}/contratos/${id}`, datos) : this.http.post<Contrato>(`${this.url}/contratos`, datos);
  }

  agregarRegla(id: number, datos: { servicioId: number; tipo: string; valor: number | null; cantidadMax: number | null }) {
    return this.http.post<Contrato>(`${this.url}/contratos/${id}/reglas`, datos);
  }

  quitarRegla(id: number, reglaId: number) {
    return this.http.delete<Contrato>(`${this.url}/contratos/${id}/reglas/${reglaId}`);
  }

  agregarPaquete(id: number, datos: { codigo: string; nombre: string; precio: number; servicioIds: number[] }) {
    return this.http.post<Contrato>(`${this.url}/contratos/${id}/paquetes`, datos);
  }

  quitarPaquete(id: number, paqueteId: number) {
    return this.http.delete<Contrato>(`${this.url}/contratos/${id}/paquetes/${paqueteId}`);
  }
}
