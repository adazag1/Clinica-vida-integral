import { Component, DestroyRef, ElementRef, computed, effect, inject, input, output, signal, viewChild } from '@angular/core';

import { ImagenEstudio } from '../imagenes.models';
import { ImagenesService } from '../imagenes.service';

/**
 * Visor del mockup: zoom, desplazamiento, brillo/contraste, rotación, espejo y pantalla completa,
 * con miniaturas. Las imágenes se piden con el token y se muestran como blob. Un DICOM no se
 * dibuja en el navegador: se abre en el visor del PACS.
 */
@Component({
  selector: 'app-visor-imagenes',
  template: `
    <div class="herramientas" role="toolbar" aria-label="Herramientas del visor">
      <button type="button" title="Acercar" (click)="zoom.set(zoom() * 1.25)"><span class="icono">zoom_in</span></button>
      <button type="button" title="Alejar" (click)="zoom.set(Math.max(0.25, zoom() / 1.25))"><span class="icono">zoom_out</span></button>
      <button type="button" title="Arrastrar para desplazar" [class.activa]="modo() === 'mover'" (click)="modo.set('mover')"><span class="icono">pan_tool</span></button>
      <button type="button" title="Brillo y contraste: arrastre horizontal = brillo, vertical = contraste"
        [class.activa]="modo() === 'ventana'" (click)="modo.set('ventana')"><span class="icono">brightness_6</span></button>
      <button type="button" title="Rotar 90°" (click)="rotacion.set((rotacion() + 90) % 360)"><span class="icono">rotate_right</span></button>
      <button type="button" title="Espejo horizontal" (click)="espejo.set(!espejo())"><span class="icono">flip</span></button>
      <button type="button" title="Restablecer" (click)="restablecer()"><span class="icono">restart_alt</span></button>
      <button type="button" title="Pantalla completa" (click)="pantallaCompleta()"><span class="icono">fullscreen</span></button>
    </div>

    <div #lienzo class="lienzo" [class.lienzo--mover]="modo() === 'mover'" (pointerdown)="empezar($event)" (pointermove)="mover($event)"
      (pointerup)="soltar()" (pointerleave)="soltar()" (wheel)="rueda($event)">
      @if (actual(); as img) {
        <span class="lado">R</span>
        @if (url()) {
          <img [src]="url()" [alt]="img.nombre" draggable="false" [style.transform]="transformacion()" [style.filter]="filtro()" />
        } @else if (!img.visible) {
          <p class="aviso">Archivo DICOM ({{ img.nombre }}). Ábralo en el visor del PACS.</p>
        } @else {
          <p class="aviso">Cargando imagen…</p>
        }
        <span class="ventana">Brillo {{ brillo() }} % · Contraste {{ contraste() }} %</span>
        <span class="posicion">{{ indice() + 1 }} / {{ imagenes().length }}</span>
        @if (img.clave) { <span class="clave"><span class="icono">star</span>Imagen clave</span> }
      } @else {
        <p class="aviso">Aún no hay imágenes en este estudio.</p>
      }
    </div>

    @if (imagenes().length) {
      <div class="miniaturas">
        <button type="button" class="flecha" (click)="elegir(indice() - 1)" [disabled]="indice() === 0" aria-label="Anterior"><span class="icono">chevron_left</span></button>
        @for (img of imagenes(); track img.id; let i = $index) {
          <button type="button" class="miniatura" [class.miniatura--actual]="i === indice()" (click)="elegir(i)" [title]="img.descripcion ?? img.nombre">
            @if (miniaturas()[img.id]; as m) { <img [src]="m" alt="" /> } @else { <span class="icono">image</span> }
            <span class="numero">{{ i + 1 }}</span>
            @if (img.clave) { <span class="icono estrella">star</span> }
          </button>
        }
        <button type="button" class="flecha" (click)="elegir(indice() + 1)" [disabled]="indice() === imagenes().length - 1" aria-label="Siguiente"><span class="icono">chevron_right</span></button>
      </div>
      @if (editable() && actual(); as img) {
        <button type="button" class="enlace marcar" (click)="marcarClave.emit(img)">
          <span class="icono">{{ img.clave ? 'star' : 'star_outline' }}</span>{{ img.clave ? 'Quitar de imágenes clave' : 'Marcar como imagen clave del informe' }}
        </button>
      }
    }
  `,
  styles: `
    :host { display: flex; flex-direction: column; gap: 10px; }
    .herramientas { display: flex; flex-wrap: wrap; gap: 4px; padding: 6px; border: 1px solid var(--c-borde); border-radius: var(--radio-sm); }
    .herramientas button { width: 38px; height: 34px; display: grid; place-items: center; border: 0; border-radius: 8px; background: none;
      color: var(--c-texto); cursor: pointer; }
    .herramientas button:hover, .herramientas .activa { background: var(--c-azul-suave); color: var(--c-azul); }
    .lienzo { position: relative; height: 440px; background: #1b2230; border-radius: var(--radio-sm); overflow: hidden; display: grid;
      place-items: center; cursor: crosshair; touch-action: none; user-select: none; }
    .lienzo--mover { cursor: grab; }
    .lienzo img { max-width: 100%; max-height: 100%; transition: transform 0.05s linear; pointer-events: none; }
    .lienzo:fullscreen { height: 100vh; border-radius: 0; }
    .aviso { color: #c8d1de; text-align: center; padding: 0 20px; }
    .lado, .ventana, .posicion, .clave { position: absolute; color: #e8edf5; font-size: 13px; text-shadow: 0 1px 2px #000; }
    .lado { top: 10px; left: 12px; font-weight: 600; }
    .ventana { bottom: 10px; left: 12px; }
    .posicion { bottom: 10px; right: 12px; }
    .clave { top: 10px; right: 12px; display: flex; align-items: center; gap: 4px; color: #ffd166; }
    .clave .icono { font-size: 16px; }
    .miniaturas { display: flex; align-items: center; gap: 8px; overflow-x: auto; padding: 8px; border: 1px solid var(--c-borde);
      border-radius: var(--radio-sm); }
    .miniatura { position: relative; flex: 0 0 72px; height: 72px; border: 2px solid transparent; border-radius: 8px; background: #2a3342;
      color: #c8d1de; display: grid; place-items: center; cursor: pointer; overflow: hidden; padding: 0; }
    .miniatura img { width: 100%; height: 100%; object-fit: cover; }
    .miniatura--actual { border-color: var(--c-azul); }
    .numero { position: absolute; bottom: 2px; left: 5px; font-size: 11px; color: #fff; text-shadow: 0 1px 2px #000; }
    .estrella { position: absolute; top: 2px; right: 3px; font-size: 15px; color: #ffd166; }
    .flecha { border: 0; background: none; cursor: pointer; color: var(--c-texto-2); }
    .flecha:disabled { opacity: 0.3; cursor: default; }
    .marcar { display: inline-flex; align-items: center; gap: 6px; align-self: flex-start; }
  `,
})
export class VisorImagenes {
  private readonly service = inject(ImagenesService);
  private readonly destroyRef = inject(DestroyRef);

  readonly estudioId = input.required<number>();
  readonly imagenes = input<ImagenEstudio[]>([]);
  readonly editable = input(false);
  readonly marcarClave = output<ImagenEstudio>();

  private readonly lienzo = viewChild.required<ElementRef<HTMLElement>>('lienzo');
  protected readonly Math = Math;

  protected readonly indice = signal(0);
  protected readonly actual = computed(() => this.imagenes()[Math.min(this.indice(), this.imagenes().length - 1)] ?? null);
  protected readonly url = signal<string | null>(null);
  protected readonly miniaturas = signal<Record<number, string>>({});

  protected readonly zoom = signal(1);
  protected readonly desplazamiento = signal({ x: 0, y: 0 });
  protected readonly rotacion = signal(0);
  protected readonly espejo = signal(false);
  protected readonly brillo = signal(100);
  protected readonly contraste = signal(100);
  protected readonly modo = signal<'mover' | 'ventana'>('mover');

  protected readonly transformacion = computed(() => {
    const d = this.desplazamiento();
    return `translate(${d.x}px, ${d.y}px) scale(${this.zoom()}) rotate(${this.rotacion()}deg) scaleX(${this.espejo() ? -1 : 1})`;
  });
  protected readonly filtro = computed(() => `brightness(${this.brillo()}%) contrast(${this.contraste()}%)`);

  /** Blobs ya descargados, por id de imagen; se liberan al salir. */
  private readonly blobs = new Map<number, string>();
  private arrastre: { x: number; y: number } | null = null;

  constructor() {
    effect(() => {
      const img = this.actual();
      this.url.set(null);
      if (img?.visible) this.obtener(img.id, (u) => this.url.set(u));
    });
    effect(() => {
      for (const img of this.imagenes()) {
        if (img.visible && !this.miniaturas()[img.id]) {
          this.obtener(img.id, (u) => this.miniaturas.update((m) => ({ ...m, [img.id]: u })));
        }
      }
    });
    this.destroyRef.onDestroy(() => this.blobs.forEach((u) => URL.revokeObjectURL(u)));
  }

  private obtener(imagenId: number, listo: (url: string) => void): void {
    const guardada = this.blobs.get(imagenId);
    if (guardada) {
      listo(guardada);
      return;
    }
    this.service.imagen(this.estudioId(), imagenId).subscribe((blob) => {
      const u = URL.createObjectURL(blob);
      this.blobs.set(imagenId, u);
      listo(u);
    });
  }

  protected elegir(i: number): void {
    if (i < 0 || i >= this.imagenes().length) return;
    this.indice.set(i);
  }

  protected restablecer(): void {
    this.zoom.set(1);
    this.desplazamiento.set({ x: 0, y: 0 });
    this.rotacion.set(0);
    this.espejo.set(false);
    this.brillo.set(100);
    this.contraste.set(100);
  }

  protected pantallaCompleta(): void {
    const el = this.lienzo().nativeElement;
    if (document.fullscreenElement) document.exitFullscreen();
    else el.requestFullscreen?.();
  }

  protected empezar(e: PointerEvent): void {
    this.arrastre = { x: e.clientX, y: e.clientY };
    (e.target as HTMLElement).setPointerCapture?.(e.pointerId);
  }

  protected mover(e: PointerEvent): void {
    if (!this.arrastre) return;
    const dx = e.clientX - this.arrastre.x;
    const dy = e.clientY - this.arrastre.y;
    this.arrastre = { x: e.clientX, y: e.clientY };
    if (this.modo() === 'mover') {
      this.desplazamiento.update((d) => ({ x: d.x + dx, y: d.y + dy }));
    } else {
      this.brillo.update((b) => Math.min(300, Math.max(10, Math.round(b + dx / 2))));
      this.contraste.update((c) => Math.min(300, Math.max(10, Math.round(c - dy / 2))));
    }
  }

  protected soltar(): void {
    this.arrastre = null;
  }

  protected rueda(e: WheelEvent): void {
    e.preventDefault();
    this.zoom.update((z) => Math.min(8, Math.max(0.25, z * (e.deltaY < 0 ? 1.1 : 1 / 1.1))));
  }
}
