-- Fase 6: enfermería (RFENF-002 a RFENF-010)

-- Órdenes médicas que ejecuta enfermería: medicamentos con horario, dietas y cuidados
CREATE TABLE orden_clinica (
    id                  BIGSERIAL PRIMARY KEY,
    paciente_id         BIGINT       NOT NULL REFERENCES paciente (id),
    consulta_id         BIGINT       REFERENCES consulta (id),
    tipo                VARCHAR(20)  NOT NULL,
    descripcion         VARCHAR(300) NOT NULL,
    medicamento_id      BIGINT       REFERENCES medicamento (id),
    dosis               VARCHAR(60),
    via                 VARCHAR(40),
    -- Cada cuántas horas se ejecuta; null = una sola vez (medicamento) o continua (dieta)
    frecuencia_horas    INT,
    inicio              TIMESTAMPTZ  NOT NULL,
    fin                 TIMESTAMPTZ  NOT NULL,
    -- Medicamento de alto riesgo: exige doble validación al administrar
    alto_riesgo         BOOLEAN      NOT NULL DEFAULT FALSE,
    indicaciones        VARCHAR(500),
    estado              VARCHAR(20)  NOT NULL,
    motivo_suspension   VARCHAR(300),
    suspendida_por      VARCHAR(60),
    suspendida_en       TIMESTAMPTZ,
    medico              VARCHAR(60)  NOT NULL,
    medico_nombre       VARCHAR(150) NOT NULL,
    creado_en           TIMESTAMPTZ  NOT NULL,
    creado_por          VARCHAR(60),
    modificado_en       TIMESTAMPTZ  NOT NULL,
    modificado_por      VARCHAR(60),
    CHECK (inicio < fin),
    CHECK (frecuencia_horas IS NULL OR frecuencia_horas BETWEEN 1 AND 72)
);

CREATE INDEX idx_orden_clinica_paciente ON orden_clinica (paciente_id, estado);

-- RFENF-003 / RFENF-004: cada dosis o cuidado programado y lo que realmente pasó
CREATE TABLE ejecucion_orden (
    id                      BIGSERIAL PRIMARY KEY,
    orden_id                BIGINT       NOT NULL REFERENCES orden_clinica (id),
    programada_para         TIMESTAMPTZ  NOT NULL,
    realizada_en            TIMESTAMPTZ  NOT NULL,
    resultado               VARCHAR(30)  NOT NULL,
    motivo                  VARCHAR(300),
    justificacion_retraso   VARCHAR(300),
    verificaciones          VARCHAR(100),
    doble_validacion_por    VARCHAR(60),
    usuario                 VARCHAR(60)  NOT NULL,
    UNIQUE (orden_id, programada_para)
);

-- RFENF-007: eventos adversos e incidentes de seguridad del paciente
CREATE TABLE evento_adverso (
    id                    BIGSERIAL PRIMARY KEY,
    paciente_id           BIGINT        NOT NULL REFERENCES paciente (id),
    tipo                  VARCHAR(40)   NOT NULL,
    severidad             VARCHAR(20)   NOT NULL,
    ocurrido_en           TIMESTAMPTZ   NOT NULL,
    lugar                 VARCHAR(100),
    descripcion           VARCHAR(2000) NOT NULL,
    acciones_inmediatas   VARCHAR(1000) NOT NULL,
    medico_notificado     VARCHAR(150),
    orden_id              BIGINT        REFERENCES orden_clinica (id),
    creado_en             TIMESTAMPTZ   NOT NULL,
    creado_por            VARCHAR(60),
    modificado_en         TIMESTAMPTZ   NOT NULL,
    modificado_por        VARCHAR(60)
);

CREATE INDEX idx_evento_adverso_paciente ON evento_adverso (paciente_id, ocurrido_en);

CREATE TABLE nota_enfermeria (
    id          BIGSERIAL PRIMARY KEY,
    paciente_id BIGINT        NOT NULL REFERENCES paciente (id),
    texto       VARCHAR(2000) NOT NULL,
    usuario     VARCHAR(60)   NOT NULL,
    fecha       TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_nota_enfermeria_paciente ON nota_enfermeria (paciente_id, fecha);

-- RFENF-008: entrega de turno con el resumen tal como se entregó
CREATE TABLE entrega_turno (
    id            BIGSERIAL PRIMARY KEY,
    paciente_id   BIGINT        NOT NULL REFERENCES paciente (id),
    turno         VARCHAR(20)   NOT NULL,
    resumen       TEXT          NOT NULL,
    nota          VARCHAR(2000),
    entregado_por VARCHAR(60)   NOT NULL,
    fecha         TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_entrega_turno_paciente ON entrega_turno (paciente_id, fecha);
