-- Fase 1: usuarios, roles y auditoría (RFxx-001, RNF Seguridad por Rol, RNF Registro de Auditoría)

CREATE TABLE usuario (
    id                  BIGSERIAL PRIMARY KEY,
    username            VARCHAR(60)  NOT NULL UNIQUE,
    email               VARCHAR(120) NOT NULL UNIQUE,
    password_hash       VARCHAR(100) NOT NULL,
    nombre_completo     VARCHAR(150) NOT NULL,
    rol                 VARCHAR(30)  NOT NULL,
    cargo               VARCHAR(100),
    activo              BOOLEAN      NOT NULL DEFAULT TRUE,
    intentos_fallidos   INT          NOT NULL DEFAULT 0,
    bloqueado           BOOLEAN      NOT NULL DEFAULT FALSE,
    ultimo_acceso       TIMESTAMPTZ,
    creado_en           TIMESTAMPTZ  NOT NULL,
    creado_por          VARCHAR(60),
    modificado_en       TIMESTAMPTZ  NOT NULL,
    modificado_por      VARCHAR(60)
);

-- Registro inmutable de accesos y acciones (Ley 1581: quién consultó o modificó qué y cuándo)
CREATE TABLE auditoria (
    id            BIGSERIAL PRIMARY KEY,
    fecha         TIMESTAMPTZ  NOT NULL,
    usuario       VARCHAR(60),
    accion        VARCHAR(40)  NOT NULL,
    recurso       VARCHAR(255) NOT NULL,
    metodo_http   VARCHAR(10),
    estado_http   INT,
    ip            VARCHAR(64),
    detalle       TEXT
);

CREATE INDEX idx_auditoria_fecha   ON auditoria (fecha);
CREATE INDEX idx_auditoria_usuario ON auditoria (usuario);
