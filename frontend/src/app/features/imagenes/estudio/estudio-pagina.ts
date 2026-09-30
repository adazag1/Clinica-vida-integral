import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { codigoError, erroresCampos, mensajeError } from '../../../core/api/api';
import { AuthService } from '../../../core/auth/auth.service';
import {
  CONCORDANCIAS,
  Estudio,
  ImagenEstudio,
  Radiologo,
  claseEstadoEstudio,
  clasePrioridad,
} from '../imagenes.models';
import { ImagenesService } from '../imagenes.service';
import { VisorImagenes } from '../visor/visor-imagenes';

type Dialogo = 'firma' | 'enmienda' | 'bloqueo' | 'desbloqueo' | 'opinion' | null;

/** Pantalla del mockup de Imágenes Diagnósticas: estudios del paciente, visor e informe radiológico. */
@Component({
  selector: 'app-estudio-pagina',
  imports: [DatePipe, FormsModule, RouterLink, VisorImagenes],
  templateUrl: './estudio-pagina.html',
  styleUrl: './estudio-pagina.scss',
})
export class EstudioPagina {
  private readonly service = inject(ImagenesService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Viene de la ruta /imagenes/estudios/:id. */
  readonly id = input.required<string>();

  protected readonly clasePrioridad = clasePrioridad;
  protected readonly claseEstado = claseEstadoEstudio;
  protected readonly concordancias = CONCORDANCIAS;

  protected readonly estudio = signal<Estudio | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  protected readonly ocupado = signal(false);
  protected readonly dialogo = signal<Dialogo>(null);
  protected readonly errorDialogo = signal<string | null>(null);
  protected readonly radiologos = signal<Radiologo[]>([]);

  protected readonly usuario = computed(() => this.auth.usuario()?.username ?? '');
  /** Radiología (técnicos y radiólogos) carga, bloquea y asigna; solo el radiólogo firma. */
  protected readonly esRadiologia = computed(() => this.auth.tieneRol('RADIOLOGIA'));
  protected readonly esRadiologo = computed(() => this.auth.usuario()?.rol === 'RADIOLOGIA');
  protected readonly bloqueado = computed(() => this.estudio()?.resumen.estado === 'BLOQUEADO');

  /** Puede escribir el informe: su borrador, o un estudio adquirido aún sin borrador. */
  protected readonly editable = computed(() => {
    const e = this.estudio();
    if (!e || !this.esRadiologo() || this.bloqueado()) return false;
    if (e.borrador) return e.borrador.autor.toLowerCase() === this.usuario().toLowerCase();
    return e.resumen.estado === 'ADQUIRIDO';
  });
  protected readonly opinionMia = computed(() =>
    this.estudio()?.segundasOpiniones.find((o) => !o.respondidaEn && o.asignadaA.toLowerCase() === this.usuario().toLowerCase()) ?? null,
  );

  protected informe = { hallazgos: '', impresion: '', recomendaciones: '' };
  protected firma = { password: '', critico: false, criticoReceptor: '', criticoMedio: 'Llamada' };
  protected motivo = '';
  protected opinion = { asignadaA: '', pregunta: '' };
  protected respuesta = { concordancia: 'CONCORDANTE', comentario: '' };
  protected uid = '';
  protected archivos: File[] = [];

  constructor() {
    effect(() => this.cargar(Number(this.id())));
  }

  private cargar(id: number): void {
    this.service.obtener(id).subscribe({
      next: (e) => this.mostrar(e),
      error: (e: HttpErrorResponse) => this.error.set(mensajeError(e, 'No fue posible cargar el estudio.')),
    });
  }

  private mostrar(e: Estudio): void {
    const anterior = this.estudio();
    // Marcar una imagen clave, asignar o pedir segunda lectura recargan el estudio: no borrar lo que se está escribiendo
    const mismoInforme = anterior?.resumen.id === e.resumen.id && anterior.resumen.estado === e.resumen.estado
      && anterior.borrador?.version === e.borrador?.version;
    this.estudio.set(e);
    this.error.set(null);
    this.uid = e.studyInstanceUid ?? '';
    if (mismoInforme) return;
    const fuente = e.borrador ?? (e.resumen.estado === 'ADQUIRIDO' ? null : e.vigente);
    this.informe = {
      hallazgos: fuente?.hallazgos ?? e.plantilla.hallazgos ?? '',
      impresion: fuente?.impresion ?? e.plantilla.impresion ?? '',
      recomendaciones: fuente?.recomendaciones ?? e.plantilla.recomendaciones ?? '',
    };
  }

  protected abrirEstudio(id: number): void {
    this.router.navigate(['/imagenes/estudios', id]);
  }

  // ---------- Imágenes ----------

  protected elegirArchivos(ev: Event): void {
    this.archivos = Array.from((ev.target as HTMLInputElement).files ?? []);
  }

  protected cargarImagenes(entrada: HTMLInputElement): void {
    const e = this.estudio();
    if (!e) return;
    this.ejecutar(this.service.cargarImagenes(e.resumen.id, this.archivos, this.uid.trim() || null), () => {
      this.archivos = [];
      entrada.value = '';
      this.aviso.set('Imágenes cargadas: el estudio quedó pendiente de lectura.');
    });
  }

  protected marcarClave(img: ImagenEstudio): void {
    const e = this.estudio();
    if (e) this.ejecutar(this.service.marcarClave(e.resumen.id, img.id, !img.clave, img.descripcion));
  }

  // ---------- Informe ----------

  protected guardar(): void {
    const e = this.estudio();
    if (!e) return;
    this.ejecutar(this.service.guardarInforme(e.resumen.id, this.datosInforme()), () => this.aviso.set('Borrador guardado.'));
  }

  protected abrirFirma(): void {
    const e = this.estudio();
    if (!e) return;
    // Guarda primero lo escrito, luego pide la contraseña
    this.ejecutar(this.service.guardarInforme(e.resumen.id, this.datosInforme()), () => {
      this.firma = { password: '', critico: false, criticoReceptor: '', criticoMedio: 'Llamada' };
      this.abrir('firma');
    });
  }

  protected firmar(): void {
    const e = this.estudio();
    if (!e) return;
    this.enDialogo(this.service.firmar(e.resumen.id, {
      password: this.firma.password, critico: this.firma.critico,
      criticoReceptor: this.firma.critico ? this.firma.criticoReceptor.trim() || null : null,
      criticoMedio: this.firma.critico ? this.firma.criticoMedio : null,
    }), 'Informe firmado. El médico solicitante recibió el aviso.');
  }

  protected enmendar(): void {
    const e = this.estudio();
    if (e) this.enDialogo(this.service.enmendar(e.resumen.id, this.motivo.trim()), 'Nueva versión abierta: la firmada se conserva.');
  }

  private datosInforme() {
    const t = (s: string) => s.trim() || null;
    return { hallazgos: t(this.informe.hallazgos), impresion: t(this.informe.impresion), recomendaciones: t(this.informe.recomendaciones) };
  }

  // ---------- Bloqueo, asignación y segunda lectura ----------

  protected bloquear(): void {
    const e = this.estudio();
    if (e) this.enDialogo(this.service.bloquear(e.resumen.id, this.motivo.trim()), 'Estudio bloqueado. Se avisó a admisiones y al médico.');
  }

  protected desbloquear(): void {
    const e = this.estudio();
    if (e) this.enDialogo(this.service.desbloquear(e.resumen.id, this.motivo.trim()), 'Estudio desbloqueado.');
  }

  protected tomar(): void {
    const e = this.estudio();
    if (e) this.ejecutar(this.service.asignar(e.resumen.id, this.usuario()), () => this.aviso.set('El estudio quedó asignado a usted.'));
  }

  protected abrirOpinion(): void {
    this.service.radiologos().subscribe((r) => this.radiologos.set(r.filter((x) => x.usuario !== this.usuario())));
    this.opinion = { asignadaA: '', pregunta: '' };
    this.abrir('opinion');
  }

  protected pedirOpinion(): void {
    const e = this.estudio();
    if (e) this.enDialogo(this.service.pedirSegundaOpinion(e.resumen.id, this.opinion.asignadaA, this.opinion.pregunta.trim()),
      'Segunda lectura solicitada.');
  }

  protected responderOpinion(opinionId: number): void {
    const e = this.estudio();
    if (!e) return;
    this.ejecutar(this.service.responderSegundaOpinion(e.resumen.id, opinionId, this.respuesta.concordancia,
      this.respuesta.comentario.trim() || null), () => this.aviso.set('Segunda lectura registrada.'));
  }

  // ---------- utilidades ----------

  protected abrir(d: Dialogo): void {
    this.motivo = '';
    this.errorDialogo.set(null);
    this.dialogo.set(d);
  }

  private ejecutar(obs: ReturnType<ImagenesService['obtener']>, despues?: () => void): void {
    this.ocupado.set(true);
    this.aviso.set(null);
    obs.subscribe({
      next: (e) => {
        this.ocupado.set(false);
        this.mostrar(e);
        despues?.();
      },
      error: (err: HttpErrorResponse) => {
        this.ocupado.set(false);
        const campos = erroresCampos(err);
        this.error.set(Object.keys(campos).length ? Object.values(campos).join('. ') : mensajeError(err, 'No fue posible completar la acción.'));
      },
    });
  }

  private enDialogo(obs: ReturnType<ImagenesService['obtener']>, exito: string): void {
    this.ocupado.set(true);
    obs.subscribe({
      next: (e) => {
        this.ocupado.set(false);
        this.dialogo.set(null);
        this.mostrar(e);
        this.aviso.set(exito);
      },
      error: (err: HttpErrorResponse) => {
        this.ocupado.set(false);
        if (codigoError(err) === 'FIRMA_INVALIDA') this.firma.password = '';
        const campos = erroresCampos(err);
        this.errorDialogo.set(Object.keys(campos).length ? Object.values(campos).join('. ') : mensajeError(err, 'No fue posible completar la acción.'));
      },
    });
  }
}
