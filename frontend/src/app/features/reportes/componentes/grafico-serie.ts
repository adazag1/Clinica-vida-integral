import { Component, computed, input, signal } from '@angular/core';

export interface PuntoSerie {
  etiqueta: string;
  valor: number | null;
  detalle?: string;
}

export interface Referencia {
  valor: number;
  etiqueta: string;
  tono: 'base' | 'meta';
}

const ANCHO = 640;
const ALTO = 220;
const M = { arriba: 16, derecha: 96, abajo: 28, izquierda: 56 };

/**
 * Serie mensual en SVG (línea o barras) con líneas de referencia rotuladas (línea base, meta).
 * Una sola escala; al pasar el mouse muestra el valor del mes. Los meses sin dato quedan vacíos.
 */
@Component({
  selector: 'app-grafico-serie',
  template: `
    <div class="grafico" (mouseleave)="activo.set(null)">
      <svg [attr.viewBox]="'0 0 ' + ancho + ' ' + alto" role="img" [attr.aria-label]="titulo()">
        @for (t of ticks(); track t.valor) {
          <line class="rejilla" [attr.x1]="m.izquierda" [attr.x2]="ancho - m.derecha" [attr.y1]="t.y" [attr.y2]="t.y" />
          <text class="eje" [attr.x]="m.izquierda - 8" [attr.y]="t.y + 4" text-anchor="end">{{ t.texto }}</text>
        }
        @if (cero() !== null && tipo() === 'linea') {
          <line class="cero" [attr.x1]="m.izquierda" [attr.x2]="ancho - m.derecha" [attr.y1]="cero()" [attr.y2]="cero()" />
        }
        @if (tipo() === 'barras') {
          @for (p of geometria(); track p.etiqueta) {
            @if (p.y !== null) {
              <path class="barra" [class.barra--activa]="activo() === $index" [attr.d]="p.barra" />
            }
          }
        } @else {
          <path class="linea" [attr.d]="trazo()" />
          @for (p of geometria(); track p.etiqueta) {
            @if (p.y !== null) {
              <circle class="punto" [class.punto--activo]="activo() === $index" [attr.cx]="p.x" [attr.cy]="p.y" [attr.r]="activo() === $index ? 5 : 4" />
            }
          }
        }
        @for (r of refs(); track r.etiqueta) {
          <line class="ref" [class.ref--meta]="r.tono === 'meta'" [attr.x1]="m.izquierda" [attr.x2]="ancho - m.derecha" [attr.y1]="r.y" [attr.y2]="r.y" />
          <text class="ref-texto" [attr.x]="ancho - m.derecha + 6" [attr.y]="r.ty">{{ r.etiqueta }}</text>
        }
        @for (p of geometria(); track p.etiqueta; let i = $index) {
          @if (mostrarEtiqueta(i)) {
            <text class="eje" [attr.x]="p.x" [attr.y]="alto - 8" text-anchor="middle">{{ p.corta }}</text>
          }
          <rect class="zona" [attr.x]="p.x - paso() / 2" [attr.y]="m.arriba" [attr.width]="paso()" [attr.height]="alto - m.arriba - m.abajo"
            (mouseenter)="activo.set(i)" />
        }
        @if (activo() !== null && geometria()[activo()!]; as p) {
          <line class="guia" [attr.x1]="p.x" [attr.x2]="p.x" [attr.y1]="m.arriba" [attr.y2]="alto - m.abajo" />
        }
      </svg>
      @if (activo() !== null && geometria()[activo()!]; as p) {
        <div class="tooltip" [style.left.%]="(p.x / ancho) * 100" [class.tooltip--izq]="p.x > ancho * 0.6">
          <strong>{{ mesLargo(p.etiqueta) }}</strong>
          <span>{{ p.valor === null ? 'Sin datos' : formato()(p.valor) }}</span>
          @if (p.detalle) { <small>{{ p.detalle }}</small> }
        </div>
      }
    </div>
  `,
  styles: `
    .grafico { position: relative; }
    svg { width: 100%; height: auto; display: block; overflow: visible; }
    .rejilla { stroke: var(--c-borde); stroke-width: 1; }
    .cero { stroke: var(--c-texto-3); stroke-width: 1; }
    .eje { fill: var(--c-texto-3); font-size: 11px; }
    .linea { fill: none; stroke: var(--c-primario); stroke-width: 2; stroke-linejoin: round; stroke-linecap: round; }
    .punto { fill: var(--c-primario); stroke: var(--c-superficie); stroke-width: 2; }
    .barra { fill: var(--c-primario); opacity: 0.85; }
    .barra--activa { opacity: 1; }
    .ref { stroke: var(--c-texto-2); stroke-width: 1.5; stroke-dasharray: 5 4; }
    .ref--meta { stroke: var(--c-azul); }
    .ref-texto { fill: var(--c-texto-2); font-size: 11px; font-weight: 600; }
    .zona { fill: transparent; cursor: crosshair; }
    .guia { stroke: var(--c-texto-3); stroke-width: 1; pointer-events: none; }
    .tooltip { position: absolute; top: 0; transform: translateX(12px); pointer-events: none; display: flex; flex-direction: column;
      gap: 2px; padding: 8px 10px; background: var(--c-superficie); border: 1px solid var(--c-borde); border-radius: 8px;
      box-shadow: 0 6px 18px rgba(16, 42, 76, 0.12); font-size: 12.5px; white-space: nowrap; z-index: 2; }
    .tooltip--izq { transform: translateX(calc(-100% - 12px)); }
    .tooltip small { color: var(--c-texto-2); }
  `,
})
export class GraficoSerie {
  readonly titulo = input.required<string>();
  readonly puntos = input.required<PuntoSerie[]>();
  readonly referencias = input<Referencia[]>([]);
  readonly tipo = input<'linea' | 'barras'>('linea');
  readonly formato = input<(v: number) => string>((v) => v.toLocaleString('es-CO'));
  /** Incluir el cero en la escala (barras siempre lo incluyen). */
  readonly desdeCero = input(true);

  protected readonly ancho = ANCHO;
  protected readonly alto = ALTO;
  protected readonly m = M;
  protected readonly activo = signal<number | null>(null);
  protected readonly mesLargo = mesLargo;

  private readonly rango = computed(() => {
    const valores = [...this.puntos().map((p) => p.valor).filter((v): v is number => v !== null), ...this.referencias().map((r) => r.valor)];
    let min = valores.length ? Math.min(...valores) : 0;
    let max = valores.length ? Math.max(...valores) : 1;
    if (this.desdeCero() || this.tipo() === 'barras') {
      min = Math.min(0, min);
      max = Math.max(0, max);
    }
    if (min === max) max = min + 1;
    const [a, b, paso] = escala(min, max);
    return { min: a, max: b, paso };
  });

  protected readonly paso = computed(() => (ANCHO - M.izquierda - M.derecha) / Math.max(1, this.puntos().length));

  private y(v: number): number {
    const { min, max } = this.rango();
    return M.arriba + (1 - (v - min) / (max - min)) * (ALTO - M.arriba - M.abajo);
  }

  protected readonly ticks = computed(() => {
    const { min, max, paso } = this.rango();
    const lista: { valor: number; y: number; texto: string }[] = [];
    for (let v = min; v <= max + paso / 2; v += paso) lista.push({ valor: v, y: this.y(v), texto: compacto(v) });
    return lista;
  });

  protected readonly cero = computed(() => (this.rango().min < 0 ? this.y(0) : null));

  protected readonly geometria = computed(() => {
    const paso = this.paso();
    const base = this.y(Math.max(0, this.rango().min));
    const anchoBarra = Math.min(28, paso * 0.6);
    return this.puntos().map((p, i) => {
      const x = M.izquierda + paso * i + paso / 2;
      const y = p.valor === null ? null : this.y(p.valor);
      return { ...p, x, y, corta: corta(p.etiqueta), barra: y === null ? '' : barra(x - anchoBarra / 2, anchoBarra, y, base) };
    });
  });

  protected readonly trazo = computed(() => {
    let d = '';
    let abierto = false;
    for (const p of this.geometria()) {
      if (p.y === null) {
        abierto = false;
        continue;
      }
      d += `${abierto ? 'L' : 'M'}${p.x.toFixed(1)},${p.y.toFixed(1)} `;
      abierto = true;
    }
    return d;
  });

  protected readonly refs = computed(() => {
    // Si dos referencias quedan muy juntas, la segunda baja su rótulo para que no se monten
    const lista = this.referencias().map((r) => ({ ...r, y: this.y(r.valor), ty: this.y(r.valor) + 4 })).sort((a, b) => a.y - b.y);
    for (let i = 1; i < lista.length; i++) {
      if (lista[i].ty - lista[i - 1].ty < 13) lista[i] = { ...lista[i], ty: lista[i - 1].ty + 13 };
    }
    return lista;
  });

  /** Con muchos meses se rotula uno de cada varios para que no choquen. */
  protected mostrarEtiqueta(i: number): boolean {
    const cada = Math.ceil(this.puntos().length / 12);
    return i % cada === 0;
  }
}

/** Escala "bonita": 4-5 divisiones con paso 1, 2 o 5 × 10^n. */
function escala(min: number, max: number): [number, number, number] {
  const bruto = (max - min) / 4;
  const mag = Math.pow(10, Math.floor(Math.log10(bruto)));
  const paso = [1, 2, 5, 10].map((f) => f * mag).find((p) => p >= bruto) ?? 10 * mag;
  return [Math.floor(min / paso) * paso, Math.ceil(max / paso) * paso, paso];
}

/** Barra con las esquinas del extremo de datos redondeadas (4 px) y anclada a la base. */
function barra(x: number, ancho: number, y: number, base: number): string {
  const alto = Math.abs(base - y);
  const r = Math.min(4, ancho / 2, alto);
  if (y <= base) {
    return `M${x},${base} V${y + r} Q${x},${y} ${x + r},${y} H${x + ancho - r} Q${x + ancho},${y} ${x + ancho},${y + r} V${base} Z`;
  }
  return `M${x},${base} V${y - r} Q${x},${y} ${x + r},${y} H${x + ancho - r} Q${x + ancho},${y} ${x + ancho},${y - r} V${base} Z`;
}

const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

/** "2026-09" → "sep 26". */
function corta(etiqueta: string): string {
  const m = /^(\d{4})-(\d{2})$/.exec(etiqueta);
  return m ? `${MESES[+m[2] - 1]} ${m[1].slice(2)}` : etiqueta;
}

export function compacto(v: number): string {
  const a = Math.abs(v);
  if (a >= 1e9) return `${(v / 1e9).toLocaleString('es-CO', { maximumFractionDigits: 1 })} mil M`;
  if (a >= 1e6) return `${(v / 1e6).toLocaleString('es-CO', { maximumFractionDigits: 1 })} M`;
  if (a >= 1e3) return `${(v / 1e3).toLocaleString('es-CO', { maximumFractionDigits: 1 })} k`;
  return v.toLocaleString('es-CO', { maximumFractionDigits: 1 });
}

export function mesLargo(etiqueta: string): string {
  const m = /^(\d{4})-(\d{2})$/.exec(etiqueta);
  const nombres = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];
  return m ? `${nombres[+m[2] - 1]} ${m[1]}` : etiqueta;
}
