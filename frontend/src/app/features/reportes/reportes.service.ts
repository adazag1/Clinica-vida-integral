import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import { environment } from '../../../environments/environment';
import { Categoria, Costo, Parametro, TableroClinico, TableroFinanciero } from './reportes.models';

@Injectable({ providedIn: 'root' })
export class ReportesService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/reportes`;

  clinico() {
    return this.http.get<TableroClinico>(`${this.url}/clinico`);
  }

  financiero() {
    return this.http.get<TableroFinanciero>(`${this.url}/financiero`);
  }

  financieroCsv() {
    return this.http.get(`${this.url}/financiero.csv`, { responseType: 'blob', observe: 'response' });
  }

  parametros() {
    return this.http.get<Parametro[]>(`${this.url}/parametros`);
  }

  cambiarParametro(clave: string, valor: string) {
    return this.http.put<Parametro>(`${this.url}/parametros/${clave}`, { valor });
  }

  costos(desde: string, hasta: string) {
    return this.http.get<Costo[]>(`${this.url}/costos`, { params: { desde, hasta } });
  }

  registrarCosto(mes: string, categoria: string, valor: number, nota: string | null) {
    return this.http.put<Costo>(`${this.url}/costos`, { mes, categoria, valor, nota });
  }

  categorias() {
    return this.http.get<Categoria[]>(`${this.url}/costos/categorias`);
  }
}
