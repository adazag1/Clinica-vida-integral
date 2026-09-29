-- Fase 5: órdenes y laboratorio clínico (RFLC-002 a RFLC-015, RFMT-004 a RFMT-006)

CREATE TABLE examen_lab (
    id                  BIGSERIAL PRIMARY KEY,
    codigo              VARCHAR(30)  NOT NULL UNIQUE,
    nombre              VARCHAR(120) NOT NULL,
    area                VARCHAR(30)  NOT NULL,
    tipo_muestra        VARCHAR(80)  NOT NULL,
    -- RFLC-012: tiempo máximo esperado de respuesta (TAT), en minutos
    tat_urgente_min     INT          NOT NULL,
    tat_rutina_min      INT          NOT NULL
);

CREATE TABLE analito (
    id                  BIGSERIAL PRIMARY KEY,
    examen_id           BIGINT       NOT NULL REFERENCES examen_lab (id),
    codigo              VARCHAR(30)  NOT NULL UNIQUE,
    nombre              VARCHAR(120) NOT NULL,
    unidad              VARCHAR(30)  NOT NULL,
    decimales           INT          NOT NULL DEFAULT 1,
    orden               INT          NOT NULL,
    -- RFLC-004: diferencia (%) contra el resultado anterior que obliga a revisar (delta check)
    delta_porcentaje    NUMERIC(5,1)
);

-- RFLC-003 / RFLC-006 / RFLC-014: rangos configurables por sexo y edad (null = aplica a todos)
CREATE TABLE rango_referencia (
    id              BIGSERIAL PRIMARY KEY,
    analito_id      BIGINT        NOT NULL REFERENCES analito (id),
    sexo            VARCHAR(20),
    edad_min        INT           NOT NULL DEFAULT 0,
    edad_max        INT           NOT NULL DEFAULT 150,
    ref_min         NUMERIC(10,3),
    ref_max         NUMERIC(10,3),
    critico_min     NUMERIC(10,3),
    critico_max     NUMERIC(10,3),
    modificado_en   TIMESTAMPTZ,
    modificado_por  VARCHAR(60)
);

CREATE SEQUENCE seq_numero_orden_lab START 1;

CREATE TABLE orden_lab (
    id                  BIGSERIAL PRIMARY KEY,
    numero              VARCHAR(20)  NOT NULL UNIQUE,
    paciente_id         BIGINT       NOT NULL REFERENCES paciente (id),
    consulta_id         BIGINT       REFERENCES consulta (id),
    medico              VARCHAR(60)  NOT NULL,
    medico_nombre       VARCHAR(150) NOT NULL,
    prioridad           VARCHAR(20)  NOT NULL,
    indicaciones        VARCHAR(500),
    estado              VARCHAR(30)  NOT NULL,
    ordenada_en         TIMESTAMPTZ  NOT NULL,
    muestra_tomada_en   TIMESTAMPTZ,
    muestra_tomada_por  VARCHAR(60),
    muestra_recibida_en TIMESTAMPTZ,
    proceso_en          TIMESTAMPTZ,
    validada_en         TIMESTAMPTZ,
    validada_por        VARCHAR(60),
    -- RFLC-011: muestra rechazada con motivo estructurado
    motivo_rechazo      VARCHAR(30),
    detalle_rechazo     VARCHAR(300),
    -- Notificación del valor crítico: a quién se contactó, por qué medio y cuándo
    critico_contacto    VARCHAR(150),
    critico_medio       VARCHAR(30),
    critico_notificado_en TIMESTAMPTZ,
    tat_alertado        BOOLEAN      NOT NULL DEFAULT FALSE,
    creado_en           TIMESTAMPTZ  NOT NULL,
    creado_por          VARCHAR(60),
    modificado_en       TIMESTAMPTZ  NOT NULL,
    modificado_por      VARCHAR(60)
);

CREATE INDEX idx_orden_lab_estado   ON orden_lab (estado, prioridad);
CREATE INDEX idx_orden_lab_paciente ON orden_lab (paciente_id);

-- Un examen de la orden, con la trazabilidad técnica de su procesamiento (RFLC-008)
CREATE TABLE orden_examen (
    id                BIGSERIAL PRIMARY KEY,
    orden_id          BIGINT       NOT NULL REFERENCES orden_lab (id),
    examen_id         BIGINT       NOT NULL REFERENCES examen_lab (id),
    equipo            VARCHAR(80),
    lote_reactivo     VARCHAR(60),
    calibracion       VARCHAR(80),
    UNIQUE (orden_id, examen_id)
);

CREATE TABLE resultado_lab (
    id                BIGSERIAL PRIMARY KEY,
    orden_id          BIGINT        NOT NULL REFERENCES orden_lab (id),
    analito_id        BIGINT        NOT NULL REFERENCES analito (id),
    valor             NUMERIC(10,3) NOT NULL,
    interpretacion    VARCHAR(20)   NOT NULL,
    ref_min           NUMERIC(10,3),
    ref_max           NUMERIC(10,3),
    critico_min       NUMERIC(10,3),
    critico_max       NUMERIC(10,3),
    valor_previo      NUMERIC(10,3),
    fecha_previo      TIMESTAMPTZ,
    delta_porcentaje  NUMERIC(8,1),
    alerta_delta      BOOLEAN       NOT NULL DEFAULT FALSE,
    repeticiones      INT           NOT NULL DEFAULT 0,
    registrado_por    VARCHAR(60)   NOT NULL,
    registrado_en     TIMESTAMPTZ   NOT NULL,
    UNIQUE (orden_id, analito_id)
);

CREATE INDEX idx_resultado_analito ON resultado_lab (analito_id);

-- RFLC-009: cada repetición conserva el valor anterior y el motivo
CREATE TABLE reproceso_lab (
    id              BIGSERIAL PRIMARY KEY,
    resultado_id    BIGINT        NOT NULL REFERENCES resultado_lab (id),
    valor_anterior  NUMERIC(10,3) NOT NULL,
    valor_nuevo     NUMERIC(10,3) NOT NULL,
    motivo          VARCHAR(30)   NOT NULL,
    detalle         VARCHAR(300),
    usuario         VARCHAR(60)   NOT NULL,
    fecha           TIMESTAMPTZ   NOT NULL
);

-- RFLC-007: controles de calidad del día por área y equipo
CREATE TABLE control_calidad (
    id              BIGSERIAL PRIMARY KEY,
    area            VARCHAR(30)  NOT NULL,
    equipo          VARCHAR(80)  NOT NULL,
    fecha           DATE         NOT NULL,
    niveles         VARCHAR(60)  NOT NULL,
    lote_control    VARCHAR(60)  NOT NULL,
    aceptado        BOOLEAN      NOT NULL,
    observacion     VARCHAR(300),
    registrado_por  VARCHAR(60)  NOT NULL,
    registrado_en   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_control_calidad_fecha ON control_calidad (fecha, area);

-- Historial de la orden (RNFLC-006)
CREATE TABLE evento_orden_lab (
    id          BIGSERIAL PRIMARY KEY,
    orden_id    BIGINT       NOT NULL REFERENCES orden_lab (id),
    accion      VARCHAR(40)  NOT NULL,
    detalle     VARCHAR(500),
    usuario     VARCHAR(60)  NOT NULL,
    fecha       TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_evento_orden ON evento_orden_lab (orden_id);

-- ---------- Catálogo inicial (rangos de adultos de referencia general; los ajusta el laboratorio) ----------

INSERT INTO examen_lab (codigo, nombre, area, tipo_muestra, tat_urgente_min, tat_rutina_min) VALUES
    ('HEMOGRAMA', 'Hemograma completo', 'HEMATOLOGIA', 'Sangre total (tubo lila, EDTA)', 60, 240),
    ('GLUCOSA', 'Glucosa en ayunas', 'QUIMICA', 'Suero (tubo rojo o amarillo)', 60, 240),
    ('CREATININA', 'Creatinina sérica', 'QUIMICA', 'Suero (tubo rojo o amarillo)', 60, 240),
    ('ELECTROLITOS', 'Electrolitos (sodio y potasio)', 'QUIMICA', 'Suero (tubo rojo o amarillo)', 60, 240),
    ('PERFIL_LIPIDICO', 'Perfil lipídico', 'QUIMICA', 'Suero en ayunas de 12 horas', 120, 480),
    ('TROPONINA', 'Troponina I', 'QUIMICA', 'Suero o plasma', 45, 120),
    ('PCR', 'Proteína C reactiva', 'QUIMICA', 'Suero (tubo rojo o amarillo)', 60, 240),
    ('TSH', 'Hormona estimulante de tiroides (TSH)', 'INMUNOLOGIA', 'Suero (tubo rojo o amarillo)', 240, 1440);

INSERT INTO analito (examen_id, codigo, nombre, unidad, decimales, orden, delta_porcentaje)
SELECT e.id, v.codigo, v.nombre, v.unidad, v.decimales, v.orden, v.delta FROM (VALUES
    ('HEMOGRAMA', 'HGB', 'Hemoglobina', 'g/dL', 1, 1, 15.0),
    ('HEMOGRAMA', 'HCT', 'Hematocrito', '%', 1, 2, 15.0),
    ('HEMOGRAMA', 'WBC', 'Leucocitos', '×10³/µL', 1, 3, 50.0),
    ('HEMOGRAMA', 'PLT', 'Plaquetas', '×10³/µL', 0, 4, 40.0),
    ('GLUCOSA', 'GLU', 'Glucosa', 'mg/dL', 0, 1, NULL),
    ('CREATININA', 'CREA', 'Creatinina', 'mg/dL', 2, 1, 30.0),
    ('ELECTROLITOS', 'NA', 'Sodio', 'mmol/L', 0, 1, 5.0),
    ('ELECTROLITOS', 'K', 'Potasio', 'mmol/L', 1, 2, 20.0),
    ('PERFIL_LIPIDICO', 'COL', 'Colesterol total', 'mg/dL', 0, 1, NULL),
    ('PERFIL_LIPIDICO', 'HDL', 'Colesterol HDL', 'mg/dL', 0, 2, NULL),
    ('PERFIL_LIPIDICO', 'LDL', 'Colesterol LDL', 'mg/dL', 0, 3, NULL),
    ('PERFIL_LIPIDICO', 'TG', 'Triglicéridos', 'mg/dL', 0, 4, NULL),
    ('TROPONINA', 'TNI', 'Troponina I', 'ng/mL', 3, 1, NULL),
    ('PCR', 'PCR', 'Proteína C reactiva', 'mg/L', 1, 1, NULL),
    ('TSH', 'TSH', 'TSH', 'µUI/mL', 2, 1, NULL)
) AS v(examen, codigo, nombre, unidad, decimales, orden, delta)
JOIN examen_lab e ON e.codigo = v.examen;

INSERT INTO rango_referencia (analito_id, sexo, edad_min, edad_max, ref_min, ref_max, critico_min, critico_max)
SELECT a.id, v.sexo, v.edad_min, v.edad_max, v.ref_min, v.ref_max, v.cmin, v.cmax FROM (VALUES
    ('HGB', 'MASCULINO', 13, 150, 13.5, 17.5, 7.0, 20.0),
    ('HGB', 'FEMENINO', 13, 150, 12.0, 15.5, 7.0, 20.0),
    ('HGB', NULL, 1, 12, 11.0, 14.5, 7.0, 20.0),
    ('HCT', 'MASCULINO', 13, 150, 41.0, 53.0, 21.0, 60.0),
    ('HCT', 'FEMENINO', 13, 150, 36.0, 46.0, 21.0, 60.0),
    ('HCT', NULL, 1, 12, 33.0, 43.0, 21.0, 60.0),
    ('WBC', NULL, 0, 150, 4.5, 11.0, 2.0, 30.0),
    ('PLT', NULL, 0, 150, 150, 450, 50, 1000),
    ('GLU', NULL, 0, 150, 70, 99, 50, 400),
    ('CREA', 'MASCULINO', 13, 150, 0.70, 1.30, NULL, 5.00),
    ('CREA', 'FEMENINO', 13, 150, 0.60, 1.10, NULL, 5.00),
    ('CREA', NULL, 0, 12, 0.30, 0.70, NULL, 3.00),
    ('NA', NULL, 0, 150, 136, 145, 120, 160),
    ('K', NULL, 0, 150, 3.5, 5.1, 2.8, 6.2),
    ('COL', NULL, 0, 150, NULL, 199, NULL, NULL),
    ('HDL', NULL, 0, 150, 40, NULL, NULL, NULL),
    ('LDL', NULL, 0, 150, NULL, 129, NULL, NULL),
    ('TG', NULL, 0, 150, NULL, 149, NULL, 1000),
    ('TNI', NULL, 0, 150, NULL, 0.040, NULL, 0.400),
    ('PCR', NULL, 0, 150, NULL, 5.0, NULL, NULL),
    ('TSH', NULL, 0, 150, 0.40, 4.00, NULL, NULL)
) AS v(analito, sexo, edad_min, edad_max, ref_min, ref_max, cmin, cmax)
JOIN analito a ON a.codigo = v.analito;
