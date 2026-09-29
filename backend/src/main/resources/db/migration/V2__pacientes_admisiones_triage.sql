-- Fase 2: pacientes, admisiones y triage (RFADM-002 a RFADM-013)

CREATE TABLE paciente (
    id                  BIGSERIAL PRIMARY KEY,
    tipo_documento      VARCHAR(20)  NOT NULL,
    numero_documento    VARCHAR(30)  NOT NULL,
    nombres             VARCHAR(100) NOT NULL,
    apellidos           VARCHAR(100) NOT NULL,
    -- Nombre completo en minúscula y sin tildes: búsqueda y detección de duplicados sin extensiones de PostgreSQL
    nombre_normalizado  VARCHAR(210) NOT NULL,
    fecha_nacimiento    DATE,
    sexo                VARCHAR(20),
    telefono            VARCHAR(30),
    email               VARCHAR(120),
    direccion           VARCHAR(200),
    aseguradora         VARCHAR(120),
    regimen             VARCHAR(20),
    creado_en           TIMESTAMPTZ  NOT NULL,
    creado_por          VARCHAR(60),
    modificado_en       TIMESTAMPTZ  NOT NULL,
    modificado_por      VARCHAR(60),
    CONSTRAINT uk_paciente_documento UNIQUE (tipo_documento, numero_documento)
);

CREATE INDEX idx_paciente_numero_documento ON paciente (numero_documento);
CREATE INDEX idx_paciente_nombre           ON paciente (nombre_normalizado);
CREATE INDEX idx_paciente_fecha_nacimiento ON paciente (fecha_nacimiento);
CREATE INDEX idx_paciente_telefono         ON paciente (telefono);

-- RFADM-010: número para pacientes que llegan sin documento (TMP-000001, ...)
CREATE SEQUENCE seq_identificador_temporal START 1;

-- RFADM-011: cada cambio demográfico con usuario, fecha y motivo
CREATE TABLE cambio_demografico (
    id              BIGSERIAL PRIMARY KEY,
    paciente_id     BIGINT       NOT NULL REFERENCES paciente (id),
    campo           VARCHAR(40)  NOT NULL,
    valor_anterior  VARCHAR(255),
    valor_nuevo     VARCHAR(255),
    motivo          VARCHAR(500) NOT NULL,
    usuario         VARCHAR(60)  NOT NULL,
    fecha           TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_cambio_demografico_paciente ON cambio_demografico (paciente_id);

-- RFADM-004: episodio de atención (número de atención que se entrega al paciente)
CREATE SEQUENCE seq_numero_episodio START 1;

CREATE TABLE episodio (
    id                          BIGSERIAL PRIMARY KEY,
    numero                      VARCHAR(20)  NOT NULL UNIQUE,
    paciente_id                 BIGINT       NOT NULL REFERENCES paciente (id),
    tipo_atencion               VARCHAR(30)  NOT NULL,
    motivo_consulta             VARCHAR(1000) NOT NULL,
    estado                      VARCHAR(30)  NOT NULL,
    fecha_ingreso               TIMESTAMPTZ  NOT NULL,
    fecha_listo                 TIMESTAMPTZ,
    fecha_cierre                TIMESTAMPTZ,
    motivo_anulacion            VARCHAR(500),
    -- RFADM-008 / RFADM-009: cobertura verificada o excepción de urgencia
    estado_cobertura            VARCHAR(30)  NOT NULL,
    aseguradora                 VARCHAR(120),
    plan_cobertura              VARCHAR(120),
    numero_autorizacion         VARCHAR(60),
    observacion_cobertura       VARCHAR(500),
    cobertura_verificada_por    VARCHAR(60),
    cobertura_verificada_en     TIMESTAMPTZ,
    creado_en                   TIMESTAMPTZ  NOT NULL,
    creado_por                  VARCHAR(60),
    modificado_en               TIMESTAMPTZ  NOT NULL,
    modificado_por              VARCHAR(60)
);

CREATE INDEX idx_episodio_paciente ON episodio (paciente_id);
CREATE INDEX idx_episodio_estado   ON episodio (estado);

-- RFADM-005 / RFADM-006: un triage por episodio
CREATE TABLE triage (
    id                      BIGSERIAL PRIMARY KEY,
    episodio_id             BIGINT       NOT NULL UNIQUE REFERENCES episodio (id),
    frecuencia_cardiaca     INT          NOT NULL,
    frecuencia_respiratoria INT          NOT NULL,
    presion_sistolica       INT          NOT NULL,
    presion_diastolica      INT          NOT NULL,
    temperatura             NUMERIC(4,1) NOT NULL,
    saturacion_oxigeno      INT          NOT NULL,
    glasgow                 INT          NOT NULL,
    dolor                   INT          NOT NULL,
    sintoma_principal       VARCHAR(500) NOT NULL,
    nivel_sugerido          INT          NOT NULL,
    nivel_asignado          INT          NOT NULL,
    justificacion_ajuste    VARCHAR(500),
    creado_en               TIMESTAMPTZ  NOT NULL,
    creado_por              VARCHAR(60),
    modificado_en           TIMESTAMPTZ  NOT NULL,
    modificado_por          VARCHAR(60)
);

-- RFADM-007: consentimientos con fecha, hora y responsable
CREATE TABLE consentimiento (
    id              BIGSERIAL PRIMARY KEY,
    episodio_id     BIGINT       NOT NULL REFERENCES episodio (id),
    tipo            VARCHAR(40)  NOT NULL,
    otorgado_por    VARCHAR(150) NOT NULL,
    parentesco      VARCHAR(60),
    aceptado        BOOLEAN      NOT NULL,
    observaciones   VARCHAR(500),
    creado_en       TIMESTAMPTZ  NOT NULL,
    creado_por      VARCHAR(60),
    modificado_en   TIMESTAMPTZ  NOT NULL,
    modificado_por  VARCHAR(60),
    CONSTRAINT uk_consentimiento_tipo UNIQUE (episodio_id, tipo)
);
