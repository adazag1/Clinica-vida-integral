-- Fase 3: agendamiento de citas (RFAGE-002 a RFAGE-015) y notificaciones internas

CREATE TABLE sede (
    id          BIGSERIAL PRIMARY KEY,
    nombre      VARCHAR(100) NOT NULL UNIQUE,
    ciudad      VARCHAR(80)  NOT NULL,
    direccion   VARCHAR(200)
);

CREATE TABLE especialidad (
    id      BIGSERIAL PRIMARY KEY,
    nombre  VARCHAR(100) NOT NULL UNIQUE
);

-- RFAGE-004: duración parametrizada por tipo; RFAGE-013: preparación y área a notificar
CREATE TABLE tipo_cita (
    id                          BIGSERIAL PRIMARY KEY,
    codigo                      VARCHAR(40)  NOT NULL UNIQUE,
    nombre                      VARCHAR(100) NOT NULL,
    duracion_minutos            INT          NOT NULL CHECK (duracion_minutos BETWEEN 5 AND 240),
    requiere_preparacion        BOOLEAN      NOT NULL DEFAULT FALSE,
    rol_area_preparacion        VARCHAR(30),
    instrucciones_preparacion   VARCHAR(500)
);

CREATE TABLE profesional (
    id                BIGSERIAL PRIMARY KEY,
    nombre            VARCHAR(150) NOT NULL,
    especialidad_id   BIGINT       NOT NULL REFERENCES especialidad (id),
    sede_id           BIGINT       NOT NULL REFERENCES sede (id),
    consultorio       VARCHAR(60),
    usuario_id        BIGINT       REFERENCES usuario (id),
    activo            BOOLEAN      NOT NULL DEFAULT TRUE
);

-- RFAGE-015 horarios permitidos; RFAGE-006 franjas reservadas para prioridades
CREATE TABLE horario_profesional (
    id               BIGSERIAL PRIMARY KEY,
    profesional_id   BIGINT      NOT NULL REFERENCES profesional (id),
    dia_semana       INT         NOT NULL CHECK (dia_semana BETWEEN 1 AND 7),
    hora_inicio      TIME        NOT NULL,
    hora_fin         TIME        NOT NULL,
    tipo             VARCHAR(30) NOT NULL,
    CHECK (hora_inicio < hora_fin)
);

CREATE INDEX idx_horario_profesional ON horario_profesional (profesional_id, dia_semana);

-- RFAGE-014: vacaciones, permisos y eventos del médico
CREATE TABLE bloqueo_agenda (
    id               BIGSERIAL PRIMARY KEY,
    profesional_id   BIGINT       NOT NULL REFERENCES profesional (id),
    inicio           TIMESTAMPTZ  NOT NULL,
    fin              TIMESTAMPTZ  NOT NULL,
    motivo           VARCHAR(30)  NOT NULL,
    descripcion      VARCHAR(300),
    creado_en        TIMESTAMPTZ  NOT NULL,
    creado_por       VARCHAR(60),
    modificado_en    TIMESTAMPTZ  NOT NULL,
    modificado_por   VARCHAR(60),
    CHECK (inicio < fin)
);

CREATE INDEX idx_bloqueo_profesional ON bloqueo_agenda (profesional_id, inicio);

CREATE TABLE cita (
    id                      BIGSERIAL PRIMARY KEY,
    paciente_id             BIGINT       NOT NULL REFERENCES paciente (id),
    profesional_id          BIGINT       NOT NULL REFERENCES profesional (id),
    tipo_cita_id            BIGINT       NOT NULL REFERENCES tipo_cita (id),
    inicio                  TIMESTAMPTZ  NOT NULL,
    fin                     TIMESTAMPTZ  NOT NULL,
    estado                  VARCHAR(20)  NOT NULL,
    prioridad               VARCHAR(20)  NOT NULL,
    motivo                  VARCHAR(500),
    -- RFAGE-008: cancelación con motivo estructurado; reprogramación enlazada a la cita anterior
    motivo_cancelacion      VARCHAR(30),
    detalle_cancelacion     VARCHAR(500),
    reprogramada_desde_id   BIGINT       REFERENCES cita (id),
    -- RFAGE-010: enlace para que el paciente confirme o cancele sin iniciar sesión
    token_publico           VARCHAR(64)  NOT NULL UNIQUE,
    confirmada_en           TIMESTAMPTZ,
    recordatorio_enviado_en TIMESTAMPTZ,
    creado_en               TIMESTAMPTZ  NOT NULL,
    creado_por              VARCHAR(60),
    modificado_en           TIMESTAMPTZ  NOT NULL,
    modificado_por          VARCHAR(60),
    CHECK (inicio < fin)
);

CREATE INDEX idx_cita_profesional_inicio ON cita (profesional_id, inicio);
CREATE INDEX idx_cita_paciente           ON cita (paciente_id, inicio);
CREATE INDEX idx_cita_estado_inicio      ON cita (estado, inicio);

-- RFAGE-008 / RFAGE-012: historial completo de cada cita
CREATE TABLE evento_cita (
    id        BIGSERIAL PRIMARY KEY,
    cita_id   BIGINT       NOT NULL REFERENCES cita (id),
    accion    VARCHAR(30)  NOT NULL,
    detalle   VARCHAR(500),
    usuario   VARCHAR(60)  NOT NULL,
    fecha     TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_evento_cita ON evento_cita (cita_id);

-- RFAGE-007: lista de espera con prioridad
CREATE TABLE lista_espera (
    id                BIGSERIAL PRIMARY KEY,
    paciente_id       BIGINT       NOT NULL REFERENCES paciente (id),
    especialidad_id   BIGINT       NOT NULL REFERENCES especialidad (id),
    profesional_id    BIGINT       REFERENCES profesional (id),
    tipo_cita_id      BIGINT       NOT NULL REFERENCES tipo_cita (id),
    prioridad         VARCHAR(20)  NOT NULL,
    motivo            VARCHAR(500),
    estado            VARCHAR(20)  NOT NULL,
    cita_id           BIGINT       REFERENCES cita (id),
    creado_en         TIMESTAMPTZ  NOT NULL,
    creado_por        VARCHAR(60),
    modificado_en     TIMESTAMPTZ  NOT NULL,
    modificado_por    VARCHAR(60)
);

CREATE INDEX idx_lista_espera_estado ON lista_espera (estado, especialidad_id);

-- RFAGE-009: cada recordatorio enviado (o intentado) queda registrado
CREATE TABLE recordatorio (
    id         BIGSERIAL PRIMARY KEY,
    cita_id    BIGINT       NOT NULL REFERENCES cita (id),
    canal      VARCHAR(20)  NOT NULL,
    destino    VARCHAR(150),
    resultado  VARCHAR(20)  NOT NULL,
    detalle    VARCHAR(500),
    fecha      TIMESTAMPTZ  NOT NULL
);

-- Avisos internos (campana): a un rol completo o a un usuario puntual
CREATE TABLE notificacion (
    id               BIGSERIAL PRIMARY KEY,
    rol_destino      VARCHAR(30),
    usuario_destino  VARCHAR(60),
    titulo           VARCHAR(150) NOT NULL,
    mensaje          VARCHAR(500) NOT NULL,
    enlace           VARCHAR(200),
    fecha            TIMESTAMPTZ  NOT NULL,
    leida_en         TIMESTAMPTZ,
    CHECK (rol_destino IS NOT NULL OR usuario_destino IS NOT NULL)
);

CREATE INDEX idx_notificacion_rol     ON notificacion (rol_destino, fecha);
CREATE INDEX idx_notificacion_usuario ON notificacion (usuario_destino, fecha);

-- Parametrización institucional inicial (editable luego desde configuración)
INSERT INTO sede (nombre, ciudad, direccion) VALUES
    ('Sede Principal', 'Bogotá', 'Calle 75 # 15-20'),
    ('Sede Norte', 'Bogotá', 'Autopista Norte # 120-45');

INSERT INTO especialidad (nombre) VALUES
    ('Medicina General'), ('Medicina Interna'), ('Pediatría'), ('Cardiología'), ('Ginecología');

INSERT INTO tipo_cita (codigo, nombre, duracion_minutos, requiere_preparacion, rol_area_preparacion, instrucciones_preparacion) VALUES
    ('PRIMERA_VEZ', 'Primera vez', 30, FALSE, NULL, NULL),
    ('CONTROL', 'Control', 20, FALSE, NULL, NULL),
    ('SEGUIMIENTO_CRONICO', 'Seguimiento de paciente crónico', 30, FALSE, NULL, NULL),
    ('PRIORITARIA', 'Consulta prioritaria', 20, FALSE, NULL, NULL),
    ('ECOGRAFIA', 'Ecografía', 40, TRUE, 'RADIOLOGIA',
     'Ayuno de 8 horas. Para ecografía pélvica, vejiga llena (1 litro de agua 1 hora antes).'),
    ('PROCEDIMIENTO_MENOR', 'Procedimiento menor', 60, TRUE, 'ENFERMERIA',
     'Traer acompañante. No suspender medicamentos sin indicación médica.');
