-- Fase 9: motor de IA (Épica 4 · US 4.1 y US 4.2)

-- Reglas clínicas del motor. Se activan o desactivan sin programar; umbrales y referencias en el código,
-- documentados para que el comité médico los valide.
CREATE TABLE regla_ia (
    codigo         VARCHAR(40)  PRIMARY KEY,
    activa         BOOLEAN      NOT NULL DEFAULT TRUE,
    modificado_por VARCHAR(60),
    modificado_en  TIMESTAMPTZ
);

INSERT INTO regla_ia (codigo) VALUES
    ('SEPSIS'), ('IRA'), ('METFORMINA_TFG'), ('POTASIO'), ('SCA'), ('CRISIS_HTA'), ('GLUCOSA'), ('ANEMIA'),
    ('CRITICO_SIN_NOTA');

-- US 4.2: alerta mostrada en la historia clínica. Se conserva aunque se resuelva, para medir precisión.
CREATE TABLE alerta_ia (
    id                BIGSERIAL PRIMARY KEY,
    paciente_id       BIGINT        NOT NULL REFERENCES paciente (id),
    regla             VARCHAR(40)   NOT NULL REFERENCES regla_ia (codigo),
    severidad         VARCHAR(10)   NOT NULL,
    puntaje           NUMERIC(8,2),
    escala            VARCHAR(120),
    titulo            VARCHAR(200)  NOT NULL,
    detalle           VARCHAR(1000) NOT NULL,
    evidencia         TEXT          NOT NULL,
    recomendacion     VARCHAR(600),
    huella            VARCHAR(64)   NOT NULL,
    generada_en       TIMESTAMPTZ   NOT NULL,
    vigente           BOOLEAN       NOT NULL,
    cerrada_en        TIMESTAMPTZ,
    -- Retroalimentación del médico: base de la precisión que valida el Líder Científico
    retroalimentacion VARCHAR(10),
    comentario        VARCHAR(500),
    retro_por         VARCHAR(60),
    retro_en          TIMESTAMPTZ
);
CREATE INDEX ix_alerta_ia_paciente ON alerta_ia (paciente_id, vigente);
CREATE INDEX ix_alerta_ia_regla ON alerta_ia (regla);

-- Cada uso del asistente de lenguaje: quién, cuándo, para qué paciente, modelo, tokens y costo estimado
CREATE TABLE consulta_ia (
    id             BIGSERIAL PRIMARY KEY,
    paciente_id    BIGINT        NOT NULL REFERENCES paciente (id),
    tipo           VARCHAR(20)   NOT NULL,
    usuario        VARCHAR(60)   NOT NULL,
    fecha          TIMESTAMPTZ   NOT NULL,
    modelo         VARCHAR(60),
    tokens_entrada INT,
    tokens_salida  INT,
    costo_usd      NUMERIC(10,6),
    exito          BOOLEAN       NOT NULL,
    error          VARCHAR(300)
);
