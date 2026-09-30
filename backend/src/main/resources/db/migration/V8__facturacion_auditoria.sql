-- Fase 8: ciclo de ingresos (RFRC) y auditoría clínica (RFAU)

-- ================= Catálogo de servicios y contratos =================

-- Servicios facturables. clave_origen enlaza el servicio con lo que registran los módulos clínicos
-- (p. ej. LAB:HEMOGRAMA, IMG:RX_TORAX, CONSULTA:URGENCIAS, MED:24) para generar el cargo sin redigitar.
CREATE TABLE servicio_facturable (
    id                    BIGSERIAL PRIMARY KEY,
    codigo                VARCHAR(20)   NOT NULL UNIQUE,
    nombre                VARCHAR(200)  NOT NULL,
    tipo                  VARCHAR(30)   NOT NULL,
    clave_origen          VARCHAR(60)   UNIQUE,
    tarifa_base           NUMERIC(14,2) NOT NULL,
    requiere_autorizacion BOOLEAN       NOT NULL DEFAULT FALSE,
    activo                BOOLEAN       NOT NULL DEFAULT TRUE,
    modificado_en         TIMESTAMPTZ,
    modificado_por        VARCHAR(60)
);

-- RFRC-005 / RFRC-006: contrato por aseguradora con modalidad, factor sobre la tarifa base y reglas
CREATE TABLE contrato (
    id                   BIGSERIAL PRIMARY KEY,
    aseguradora          VARCHAR(120)  NOT NULL,
    nombre               VARCHAR(150)  NOT NULL,
    modalidad            VARCHAR(20)   NOT NULL,
    factor_tarifa        NUMERIC(6,3)  NOT NULL DEFAULT 1,
    dias_pago            INT           NOT NULL,
    tope_cuenta          NUMERIC(14,2),
    vigente_desde        DATE          NOT NULL,
    vigente_hasta        DATE,
    activo               BOOLEAN       NOT NULL DEFAULT TRUE,
    modificado_en        TIMESTAMPTZ,
    modificado_por       VARCHAR(60)
);

CREATE TABLE contrato_regla (
    id            BIGSERIAL PRIMARY KEY,
    contrato_id   BIGINT        NOT NULL REFERENCES contrato (id),
    servicio_id   BIGINT        NOT NULL REFERENCES servicio_facturable (id),
    tipo          VARCHAR(30)   NOT NULL,
    valor         NUMERIC(14,2),
    cantidad_max  INT,
    UNIQUE (contrato_id, servicio_id, tipo)
);

-- Paquete: los servicios incluidos no se cobran por separado; se cobra el precio del paquete
CREATE TABLE paquete (
    id           BIGSERIAL PRIMARY KEY,
    contrato_id  BIGINT        NOT NULL REFERENCES contrato (id),
    codigo       VARCHAR(20)   NOT NULL,
    nombre       VARCHAR(150)  NOT NULL,
    precio       NUMERIC(14,2) NOT NULL,
    UNIQUE (contrato_id, codigo)
);

CREATE TABLE paquete_servicio (
    paquete_id   BIGINT NOT NULL REFERENCES paquete (id),
    servicio_id  BIGINT NOT NULL REFERENCES servicio_facturable (id),
    PRIMARY KEY (paquete_id, servicio_id)
);

-- ================= Cuentas y cargos =================

CREATE SEQUENCE seq_numero_cuenta START 1;

-- RFRC-007: la cuenta acumula los cargos de una atención y recorre su ciclo hasta el cierre
CREATE TABLE cuenta (
    id                    BIGSERIAL PRIMARY KEY,
    numero                VARCHAR(20)  NOT NULL UNIQUE,
    paciente_id           BIGINT       NOT NULL REFERENCES paciente (id),
    episodio_id           BIGINT       REFERENCES episodio (id),
    contrato_id           BIGINT       NOT NULL REFERENCES contrato (id),
    aseguradora           VARCHAR(120) NOT NULL,
    numero_autorizacion   VARCHAR(60),
    urgencia              BOOLEAN      NOT NULL DEFAULT FALSE,
    estado                VARCHAR(20)  NOT NULL,
    abierta_en            TIMESTAMPTZ  NOT NULL,
    enviada_auditoria_en  TIMESTAMPTZ,
    auditada_en           TIMESTAMPTZ,
    auditada_por          VARCHAR(60),
    facturada_en          TIMESTAMPTZ,
    cerrada_en            TIMESTAMPTZ
);
CREATE INDEX ix_cuenta_paciente ON cuenta (paciente_id);
CREATE INDEX ix_cuenta_estado ON cuenta (estado);

CREATE TABLE cargo (
    id                     BIGSERIAL PRIMARY KEY,
    cuenta_id              BIGINT        NOT NULL REFERENCES cuenta (id),
    servicio_id            BIGINT        NOT NULL REFERENCES servicio_facturable (id),
    cantidad               INT           NOT NULL,
    valor_unitario         NUMERIC(14,2) NOT NULL,
    valor_total            NUMERIC(14,2) NOT NULL,
    -- Excluido del contrato: lo paga el paciente, no la aseguradora
    a_cargo_paciente       BOOLEAN       NOT NULL DEFAULT FALSE,
    cubierto_capitacion    BOOLEAN       NOT NULL DEFAULT FALSE,
    requiere_autorizacion  BOOLEAN       NOT NULL DEFAULT FALSE,
    -- RFRC-003 / RFAU-007: soporte clínico (orden, consulta firmada, informe, registro de administración)
    tiene_soporte          BOOLEAN       NOT NULL,
    soporte                VARCHAR(300),
    soporte_enlace         VARCHAR(200),
    origen_modulo          VARCHAR(30),
    referencia_origen      VARCHAR(60),
    fecha_servicio         TIMESTAMPTZ   NOT NULL,
    estado                 VARCHAR(20)   NOT NULL,
    factura_id             BIGINT,
    motivo_anulacion       VARCHAR(300),
    anulado_por            VARCHAR(60),
    anulado_en             TIMESTAMPTZ,
    registrado_por         VARCHAR(60)   NOT NULL,
    registrado_en          TIMESTAMPTZ   NOT NULL
);
CREATE INDEX ix_cargo_cuenta ON cargo (cuenta_id);
-- RFRC-011: un mismo acto clínico no genera dos cargos vigentes
CREATE UNIQUE INDEX ux_cargo_origen ON cargo (origen_modulo, referencia_origen) WHERE estado = 'ACTIVO' AND origen_modulo IS NOT NULL;

-- RFRC-014: todo lo que le pasa a la cuenta, desde el primer cargo hasta el cierre
CREATE TABLE evento_cuenta (
    id         BIGSERIAL PRIMARY KEY,
    cuenta_id  BIGINT       NOT NULL REFERENCES cuenta (id),
    accion     VARCHAR(40)  NOT NULL,
    detalle    VARCHAR(500),
    usuario    VARCHAR(60)  NOT NULL,
    fecha      TIMESTAMPTZ  NOT NULL
);

-- ================= Facturas, glosas y pagos =================

CREATE SEQUENCE seq_numero_factura START 1;

CREATE TABLE factura (
    id                 BIGSERIAL PRIMARY KEY,
    numero             VARCHAR(20)   NOT NULL UNIQUE,
    cuenta_id          BIGINT        NOT NULL REFERENCES cuenta (id),
    aseguradora        VARCHAR(120)  NOT NULL,
    fecha_emision      TIMESTAMPTZ   NOT NULL,
    fecha_vencimiento  DATE          NOT NULL,
    subtotal           NUMERIC(14,2) NOT NULL,
    ajustes            NUMERIC(14,2) NOT NULL,
    total              NUMERIC(14,2) NOT NULL,
    valor_paciente     NUMERIC(14,2) NOT NULL,
    saldo              NUMERIC(14,2) NOT NULL,
    -- RFRC-009: CUFE y XML UBL 2.1; estado_dian SIMULADA mientras no haya proveedor tecnológico
    cufe               VARCHAR(96)   NOT NULL,
    xml                TEXT          NOT NULL,
    estado_dian        VARCHAR(20)   NOT NULL,
    emitida_por        VARCHAR(60)   NOT NULL
);

CREATE TABLE factura_linea (
    id              BIGSERIAL PRIMARY KEY,
    factura_id      BIGINT        NOT NULL REFERENCES factura (id),
    orden           INT           NOT NULL,
    codigo          VARCHAR(20)   NOT NULL,
    descripcion     VARCHAR(250)  NOT NULL,
    cantidad        INT           NOT NULL,
    valor_unitario  NUMERIC(14,2) NOT NULL,
    valor_total     NUMERIC(14,2) NOT NULL,
    nota            VARCHAR(200)
);

-- RFRC-008: glosa con concepto del Manual Único de Glosas (Res. 3047 de 2008, anexo 6) y su respuesta
CREATE TABLE glosa (
    id               BIGSERIAL PRIMARY KEY,
    factura_id       BIGINT        NOT NULL REFERENCES factura (id),
    cargo_id         BIGINT        REFERENCES cargo (id),
    concepto         VARCHAR(20)   NOT NULL,
    codigo           VARCHAR(10)   NOT NULL,
    descripcion      VARCHAR(500)  NOT NULL,
    valor            NUMERIC(14,2) NOT NULL,
    estado           VARCHAR(20)   NOT NULL,
    valor_aceptado   NUMERIC(14,2),
    respuesta        VARCHAR(1000),
    recibida_en      DATE          NOT NULL,
    limite_respuesta DATE          NOT NULL,
    respondida_en    TIMESTAMPTZ,
    respondida_por   VARCHAR(60),
    registrada_por   VARCHAR(60)   NOT NULL
);

CREATE TABLE pago (
    id             BIGSERIAL PRIMARY KEY,
    factura_id     BIGINT        NOT NULL REFERENCES factura (id),
    valor          NUMERIC(14,2) NOT NULL,
    fecha          DATE          NOT NULL,
    medio          VARCHAR(30)   NOT NULL,
    referencia     VARCHAR(60),
    registrado_por VARCHAR(60)   NOT NULL,
    registrado_en  TIMESTAMPTZ   NOT NULL
);

-- RFRC-010: cada umbral de antigüedad se alerta una sola vez por factura
CREATE TABLE alerta_cartera (
    factura_id  BIGINT      NOT NULL REFERENCES factura (id),
    umbral_dias INT         NOT NULL,
    alertada_en TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (factura_id, umbral_dias)
);

-- ================= Auditoría clínica =================

-- RFAU-009: codificación de la cuenta; cada cambio queda en codificacion_cambio
CREATE TABLE codificacion (
    id                   BIGSERIAL PRIMARY KEY,
    cuenta_id            BIGINT        NOT NULL UNIQUE REFERENCES cuenta (id),
    diagnostico_principal VARCHAR(10),
    diagnosticos_relacionados VARCHAR(200),
    modificado_por       VARCHAR(60),
    modificado_en        TIMESTAMPTZ
);

CREATE TABLE codificacion_cambio (
    id        BIGSERIAL PRIMARY KEY,
    cuenta_id BIGINT       NOT NULL REFERENCES cuenta (id),
    campo     VARCHAR(40)  NOT NULL,
    anterior  VARCHAR(200),
    nuevo     VARCHAR(200),
    motivo    VARCHAR(300),
    usuario   VARCHAR(60)  NOT NULL,
    fecha     TIMESTAMPTZ  NOT NULL
);

-- RFAU-004: devolución de la cuenta con motivo y tiempo hasta la corrección
CREATE TABLE devolucion (
    id            BIGSERIAL PRIMARY KEY,
    cuenta_id     BIGINT        NOT NULL REFERENCES cuenta (id),
    motivo        VARCHAR(30)   NOT NULL,
    detalle       VARCHAR(1000) NOT NULL,
    dirigida_a    VARCHAR(20)   NOT NULL,
    devuelta_por  VARCHAR(60)   NOT NULL,
    devuelta_en   TIMESTAMPTZ   NOT NULL,
    corregida_en  TIMESTAMPTZ,
    corregida_por VARCHAR(60),
    respuesta     VARCHAR(1000)
);

-- RFAU-005: catálogos con versión; cada alta, cambio o inactivación sube la versión
ALTER TABLE diagnostico ADD COLUMN activo BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE catalogo_version (
    catalogo     VARCHAR(20) PRIMARY KEY,
    version      INT         NOT NULL,
    actualizado_en TIMESTAMPTZ NOT NULL,
    actualizado_por VARCHAR(60) NOT NULL
);

CREATE TABLE catalogo_cambio (
    id        BIGSERIAL PRIMARY KEY,
    catalogo  VARCHAR(20)  NOT NULL,
    version   INT          NOT NULL,
    codigo    VARCHAR(20)  NOT NULL,
    accion    VARCHAR(20)  NOT NULL,
    detalle   VARCHAR(500),
    usuario   VARCHAR(60)  NOT NULL,
    fecha     TIMESTAMPTZ  NOT NULL
);

INSERT INTO catalogo_version (catalogo, version, actualizado_en, actualizado_por) VALUES
    ('DIAGNOSTICOS', 1, now(), 'sistema'),
    ('SERVICIOS', 1, now(), 'sistema');

-- ================= Datos iniciales (valores de referencia: los fija la clínica) =================

-- Códigos CUPS de referencia; las tarifas son valores de prueba, no un tarifario real
INSERT INTO servicio_facturable (codigo, nombre, tipo, clave_origen, tarifa_base, requiere_autorizacion) VALUES
    ('890201', 'Consulta de primera vez por medicina general', 'CONSULTA', 'CONSULTA:GENERAL', 45000, FALSE),
    ('890701', 'Consulta de urgencias por medicina general', 'CONSULTA', 'CONSULTA:URGENCIAS', 78000, FALSE),
    ('902210', 'Hemograma completo', 'LABORATORIO', 'LAB:HEMOGRAMA', 18500, FALSE),
    ('903841', 'Glucosa en suero', 'LABORATORIO', 'LAB:GLUCOSA', 7200, FALSE),
    ('903895', 'Creatinina en suero', 'LABORATORIO', 'LAB:CREATININA', 8400, FALSE),
    ('903864', 'Sodio y potasio en suero', 'LABORATORIO', 'LAB:ELECTROLITOS', 21000, FALSE),
    ('903868', 'Perfil lipídico', 'LABORATORIO', 'LAB:PERFIL_LIPIDICO', 32000, FALSE),
    ('903437', 'Troponina I cuantitativa', 'LABORATORIO', 'LAB:TROPONINA', 64000, FALSE),
    ('906913', 'Proteína C reactiva cuantitativa', 'LABORATORIO', 'LAB:PCR', 19500, FALSE),
    ('904902', 'Hormona estimulante de tiroides (TSH)', 'LABORATORIO', 'LAB:TSH', 36000, FALSE),
    ('871121', 'Radiografía de tórax PA y lateral', 'IMAGEN', 'IMG:RX_TORAX', 52000, FALSE),
    ('871030', 'Radiografía de abdomen simple', 'IMAGEN', 'IMG:RX_ABDOMEN', 48000, FALSE),
    ('873420', 'Radiografía de extremidad', 'IMAGEN', 'IMG:RX_EXTREMIDAD', 45000, FALSE),
    ('871010', 'Radiografía de columna', 'IMAGEN', 'IMG:RX_COLUMNA', 58000, FALSE),
    ('879111', 'Tomografía de cráneo simple', 'IMAGEN', 'IMG:TC_CRANEO', 245000, TRUE),
    ('879301', 'Tomografía de tórax contrastada', 'IMAGEN', 'IMG:TC_TORAX', 390000, TRUE),
    ('879420', 'Tomografía de abdomen y pelvis contrastada', 'IMAGEN', 'IMG:TC_ABDOMEN', 480000, TRUE),
    ('883101', 'Resonancia magnética de cerebro', 'IMAGEN', 'IMG:RM_CEREBRO', 820000, TRUE),
    ('881302', 'Ecografía de abdomen total', 'IMAGEN', 'IMG:ECO_ABDOMEN', 95000, FALSE),
    ('881431', 'Ecografía obstétrica', 'IMAGEN', 'IMG:ECO_OBSTETRICA', 85000, FALSE),
    ('876802', 'Mamografía bilateral', 'IMAGEN', 'IMG:MAMOGRAFIA', 120000, FALSE),
    ('898101', 'Estudio de coloración básica en biopsia', 'PATOLOGIA', 'PAT:BIOPSIA', 145000, FALSE),
    ('898001', 'Estudio de citología', 'PATOLOGIA', 'PAT:CITOLOGIA', 68000, FALSE),
    ('898201', 'Estudio de espécimen quirúrgico', 'PATOLOGIA', 'PAT:PIEZA_QUIRURGICA', 210000, TRUE),
    ('S11101', 'Estancia en observación de urgencias (día)', 'ESTANCIA', NULL, 180000, FALSE),
    ('I00001', 'Kit de venopunción', 'INSUMO', NULL, 6500, FALSE),
    ('I00002', 'Equipo de macrogoteo', 'INSUMO', NULL, 4800, FALSE);

-- Medicamentos: una dosis administrada por enfermería genera un cargo (valores de prueba por familia)
INSERT INTO servicio_facturable (codigo, nombre, tipo, clave_origen, tarifa_base, requiere_autorizacion)
SELECT 'M' || lpad(id::text, 5, '0'), nombre, 'MEDICAMENTO', 'MED:' || id,
       CASE familia
           WHEN 'insulinas' THEN 38000 WHEN 'heparinas' THEN 29000 WHEN 'opioides' THEN 12000
           WHEN 'cefalosporinas' THEN 16500 WHEN 'penicilinas' THEN 9800 ELSE 2500 END,
       FALSE
FROM medicamento;

INSERT INTO contrato (aseguradora, nombre, modalidad, factor_tarifa, dias_pago, tope_cuenta, vigente_desde) VALUES
    ('Particular', 'Tarifa particular', 'EVENTO', 1.000, 0, NULL, DATE '2026-01-01'),
    ('Nueva EPS', 'Nueva EPS — evento 2026', 'EVENTO', 0.900, 60, NULL, DATE '2026-01-01'),
    ('Sanitas', 'Sanitas — evento con paquetes 2026', 'EVENTO', 0.950, 45, 5000000, DATE '2026-01-01'),
    ('Compensar', 'Compensar — cápita primer nivel 2026', 'CAPITACION', 1.000, 30, NULL, DATE '2026-01-01');

-- Sanitas: la TC de cráneo a tarifa pactada, el perfil lipídico excluido y máximo 2 hemogramas por cuenta
INSERT INTO contrato_regla (contrato_id, servicio_id, tipo, valor, cantidad_max)
SELECT c.id, s.id, r.tipo, r.valor, r.cantidad
FROM contrato c
JOIN (VALUES ('879111', 'TARIFA_PACTADA', 210000::numeric, NULL::int),
             ('903868', 'EXCLUSION', NULL, NULL),
             ('902210', 'TOPE_CANTIDAD', NULL, 2)) AS r(codigo, tipo, valor, cantidad) ON TRUE
JOIN servicio_facturable s ON s.codigo = r.codigo
WHERE c.aseguradora = 'Sanitas';

-- Sanitas: paquete de dolor torácico en urgencias
INSERT INTO paquete (contrato_id, codigo, nombre, precio)
SELECT id, 'PQ-DT', 'Paquete dolor torácico en urgencias (consulta, troponina, electrolitos, Rx tórax)', 190000
FROM contrato WHERE aseguradora = 'Sanitas';

INSERT INTO paquete_servicio (paquete_id, servicio_id)
SELECT p.id, s.id FROM paquete p JOIN servicio_facturable s ON s.codigo IN ('890701', '903437', '903864', '871121')
WHERE p.codigo = 'PQ-DT';
