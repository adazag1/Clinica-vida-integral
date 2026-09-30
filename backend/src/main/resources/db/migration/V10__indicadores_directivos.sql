-- Fase 10: tableros de directivos (Épica 5 · US 5.1 y US 5.2)

-- Metas y líneas base de los indicadores. Los valores iniciales son DE PRUEBA (prueba = TRUE): el Product
-- Owner documenta la línea base real (tarea 5.1.2) y la dirección financiera los costos; al editarlos dejan de serlo.
CREATE TABLE parametro_indicador (
    clave          VARCHAR(60)   PRIMARY KEY,
    valor          VARCHAR(300)  NOT NULL,
    descripcion    VARCHAR(300)  NOT NULL,
    unidad         VARCHAR(30),
    prueba         BOOLEAN       NOT NULL DEFAULT TRUE,
    orden          INT           NOT NULL,
    modificado_por VARCHAR(60),
    modificado_en  TIMESTAMPTZ
);

INSERT INTO parametro_indicador (clave, valor, descripcion, unidad, orden) VALUES
    ('diagnostico.linea_base_horas', '48', 'Tiempo promedio de diagnóstico de enfermedades críticas antes de la plataforma', 'horas', 1),
    ('diagnostico.meta_reduccion_pct', '30', 'Reducción esperada del tiempo de diagnóstico', '%', 2),
    ('diagnostico.plazo_meses', '18', 'Plazo para alcanzar la meta de diagnóstico', 'meses', 3),
    ('diagnostico.inicio', '2026-01-01', 'Fecha de puesta en marcha de la plataforma (inicio de la medición)', 'fecha', 4),
    ('diagnostico.cie10_criticos', 'I20,I21,I50,I63,I64,J18,A41,K35,N17,C',
        'Prefijos CIE-10 que se consideran enfermedad crítica', 'códigos', 5),
    ('costos.linea_base_mensual', '180000000', 'Costo administrativo y de gestión de historias por mes antes de la plataforma', 'COP', 10),
    ('costos.meta_reduccion_pct', '25', 'Reducción esperada de los costos administrativos', '%', 11),
    ('costos.plazo_meses', '24', 'Plazo para alcanzar la meta de costos', 'meses', 12),
    ('capacidad.linea_base_atenciones_medico', '320', 'Atenciones por médico al mes antes de la plataforma', 'atenciones', 20),
    ('capacidad.meta_aumento_pct', '15', 'Aumento esperado de atenciones por médico', '%', 21),
    ('roi.inversion_usd', '750000', 'Inversión en la plataforma (presupuesto del proyecto)', 'USD', 30),
    ('roi.trm', '4000', 'Tasa de cambio para convertir la inversión', 'COP por USD', 31),
    ('roi.plazo_meses', '36', 'Plazo para que el ROI sea positivo', 'meses', 32),
    ('roi.valor_atencion', '45000', 'Ingreso promedio de una atención adicional', 'COP', 33),
    ('roi.margen_atencion_pct', '35', 'Margen de contribución de una atención adicional', '%', 34);

-- US 5.2: costos operativos del mes por categoría, registrados por la dirección financiera
CREATE TABLE costo_operativo (
    id             BIGSERIAL     PRIMARY KEY,
    mes            DATE          NOT NULL,
    categoria      VARCHAR(30)   NOT NULL,
    valor          NUMERIC(16,2) NOT NULL CHECK (valor >= 0),
    nota           VARCHAR(300),
    registrado_por VARCHAR(60)   NOT NULL,
    registrado_en  TIMESTAMPTZ   NOT NULL,
    UNIQUE (mes, categoria)
);
