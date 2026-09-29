-- Fase 7: imágenes diagnósticas (RFRAD), patología (RFPAT) y gestión documental (RFGAC)

-- Archivos cargados (imágenes clave, documentos digitalizados). Se guardan en la base de datos para que
-- queden dentro del mismo respaldo; la huella SHA-256 permite comprobar que no se alteraron.
CREATE TABLE archivo (
    id          BIGSERIAL PRIMARY KEY,
    nombre      VARCHAR(200) NOT NULL,
    tipo_mime   VARCHAR(100) NOT NULL,
    tamano      BIGINT       NOT NULL,
    sha256      VARCHAR(64)  NOT NULL,
    contenido   BYTEA,
    subido_por  VARCHAR(60)  NOT NULL,
    subido_en   TIMESTAMPTZ  NOT NULL
);

-- ================= Imágenes diagnósticas =================

-- RFRAD-004 / RFRAD-006: tiempos objetivo y plantilla de informe por tipo de estudio (configurables)
CREATE TABLE tipo_estudio (
    id                        BIGSERIAL PRIMARY KEY,
    codigo                    VARCHAR(30)  NOT NULL UNIQUE,
    nombre                    VARCHAR(150) NOT NULL,
    modalidad                 VARCHAR(10)  NOT NULL,
    region                    VARCHAR(80)  NOT NULL,
    tat_urgente_min           INT          NOT NULL,
    tat_hospitalizado_min     INT          NOT NULL,
    tat_ambulatorio_min       INT          NOT NULL,
    plantilla_hallazgos       TEXT,
    plantilla_impresion       TEXT,
    plantilla_recomendaciones TEXT,
    modificado_en             TIMESTAMPTZ,
    modificado_por            VARCHAR(60)
);

CREATE SEQUENCE seq_numero_estudio START 1;

CREATE TABLE estudio_imagen (
    id                     BIGSERIAL PRIMARY KEY,
    numero                 VARCHAR(20)  NOT NULL UNIQUE,
    paciente_id            BIGINT       NOT NULL REFERENCES paciente (id),
    consulta_id            BIGINT       REFERENCES consulta (id),
    tipo_estudio_id        BIGINT       NOT NULL REFERENCES tipo_estudio (id),
    prioridad              VARCHAR(20)  NOT NULL,
    motivo                 VARCHAR(500) NOT NULL,
    diagnostico_presuntivo VARCHAR(300),
    medico                 VARCHAR(60)  NOT NULL,
    medico_nombre          VARCHAR(150) NOT NULL,
    estado                 VARCHAR(30)  NOT NULL,
    ordenado_en            TIMESTAMPTZ  NOT NULL,
    adquirido_en           TIMESTAMPTZ,
    adquirido_por          VARCHAR(60),
    asignado_a             VARCHAR(60),
    -- RFRAD-009: identificador DICOM del estudio en el PACS
    study_instance_uid     VARCHAR(128),
    -- RFRAD-011: bloqueo por inconsistencia de identificación
    bloqueo_motivo         VARCHAR(300),
    bloqueado_por          VARCHAR(60),
    bloqueado_en           TIMESTAMPTZ,
    estado_antes_bloqueo   VARCHAR(30),
    informado_en           TIMESTAMPTZ,
    informado_por          VARCHAR(60),
    -- RFRAD-008: hallazgo crítico y a quién se notificó
    critico                BOOLEAN      NOT NULL DEFAULT FALSE,
    critico_receptor       VARCHAR(150),
    critico_medio          VARCHAR(40),
    critico_notificado_en  TIMESTAMPTZ,
    tat_alertado           BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE INDEX ix_estudio_paciente ON estudio_imagen (paciente_id);
CREATE INDEX ix_estudio_estado ON estudio_imagen (estado);

CREATE TABLE imagen_estudio (
    id          BIGSERIAL PRIMARY KEY,
    estudio_id  BIGINT       NOT NULL REFERENCES estudio_imagen (id),
    archivo_id  BIGINT       NOT NULL REFERENCES archivo (id),
    orden       INT          NOT NULL,
    descripcion VARCHAR(200),
    -- RFRAD-010: imagen clave adjunta al informe
    clave       BOOLEAN      NOT NULL DEFAULT FALSE
);

-- RNFRAD-002: cada versión del informe se conserva; una versión firmada no cambia nunca
CREATE TABLE informe_imagen (
    id               BIGSERIAL PRIMARY KEY,
    estudio_id       BIGINT       NOT NULL REFERENCES estudio_imagen (id),
    version          INT          NOT NULL,
    hallazgos        TEXT,
    impresion        TEXT,
    recomendaciones  TEXT,
    estado           VARCHAR(20)  NOT NULL,
    motivo_cambio    VARCHAR(300),
    autor            VARCHAR(60)  NOT NULL,
    autor_nombre     VARCHAR(150) NOT NULL,
    creado_en        TIMESTAMPTZ  NOT NULL,
    modificado_en    TIMESTAMPTZ  NOT NULL,
    firmado_en       TIMESTAMPTZ,
    firma_hash       VARCHAR(64),
    UNIQUE (estudio_id, version)
);

-- RFRAD-007: doble lectura
CREATE TABLE segunda_opinion (
    id             BIGSERIAL PRIMARY KEY,
    estudio_id     BIGINT       NOT NULL REFERENCES estudio_imagen (id),
    solicitada_por VARCHAR(60)  NOT NULL,
    asignada_a     VARCHAR(60)  NOT NULL,
    pregunta       VARCHAR(500) NOT NULL,
    solicitada_en  TIMESTAMPTZ  NOT NULL,
    concordancia   VARCHAR(30),
    comentario     VARCHAR(2000),
    respondida_en  TIMESTAMPTZ
);

CREATE TABLE evento_estudio (
    id         BIGSERIAL PRIMARY KEY,
    estudio_id BIGINT       NOT NULL REFERENCES estudio_imagen (id),
    accion     VARCHAR(40)  NOT NULL,
    detalle    VARCHAR(500),
    usuario    VARCHAR(60)  NOT NULL,
    fecha      TIMESTAMPTZ  NOT NULL
);

INSERT INTO tipo_estudio (codigo, nombre, modalidad, region, tat_urgente_min, tat_hospitalizado_min, tat_ambulatorio_min,
                          plantilla_hallazgos, plantilla_impresion, plantilla_recomendaciones) VALUES
    ('RX_TORAX', 'Radiografía de tórax PA y lateral', 'RX', 'Tórax', 60, 360, 2880,
     E'Campos pulmonares: \nSilueta cardiaca: \nMediastino e hilios: \nSenos costofrénicos: \nEstructuras óseas y tejidos blandos: ',
     'Estudio radiográfico de tórax sin hallazgos patológicos agudos.', NULL),
    ('RX_ABDOMEN', 'Radiografía de abdomen simple', 'RX', 'Abdomen', 60, 360, 2880,
     E'Patrón de gas intestinal: \nNiveles hidroaéreos: \nCalcificaciones: \nEstructuras óseas: ', NULL, NULL),
    ('RX_EXTREMIDAD', 'Radiografía de extremidad', 'RX', 'Extremidades', 60, 360, 2880,
     E'Alineación: \nIntegridad cortical / trazos de fractura: \nEspacios articulares: \nTejidos blandos: ', NULL, NULL),
    ('RX_COLUMNA', 'Radiografía de columna', 'RX', 'Columna', 120, 480, 2880,
     E'Alineación y curvaturas: \nCuerpos vertebrales: \nEspacios intervertebrales: \nElementos posteriores: ', NULL, NULL),
    ('TC_CRANEO', 'Tomografía de cráneo simple', 'TC', 'Cabeza', 30, 240, 2880,
     E'Parénquima cerebral: \nSistema ventricular: \nEspacios subaracnoideos: \nLínea media: \nFosa posterior: \nCalota y base de cráneo: ',
     NULL, NULL),
    ('TC_TORAX', 'Tomografía de tórax contrastada', 'TC', 'Tórax', 60, 360, 4320,
     E'Parénquima pulmonar: \nVía aérea: \nMediastino y ganglios: \nPleura: \nEstructuras vasculares: ', NULL, NULL),
    ('TC_ABDOMEN', 'Tomografía de abdomen y pelvis contrastada', 'TC', 'Abdomen', 60, 360, 4320,
     E'Hígado y vía biliar: \nPáncreas: \nBazo: \nRiñones y vías urinarias: \nAsas intestinales: \nApéndice: \nLíquido libre: ',
     NULL, NULL),
    ('RM_CEREBRO', 'Resonancia magnética de cerebro', 'RM', 'Cabeza', 120, 720, 7200,
     E'Parénquima cerebral: \nSustancia blanca: \nSistema ventricular: \nSecuencia de difusión: \nRealce tras contraste: ', NULL, NULL),
    ('ECO_ABDOMEN', 'Ecografía abdominal total', 'ECO', 'Abdomen', 60, 360, 2880,
     E'Hígado: \nVesícula y vía biliar: \nPáncreas: \nBazo: \nRiñones: \nLíquido libre: ', NULL, NULL),
    ('ECO_OBSTETRICA', 'Ecografía obstétrica', 'ECO', 'Pelvis', 60, 360, 2880,
     E'Número de fetos: \nFrecuencia cardiaca fetal: \nBiometría: \nPlacenta: \nLíquido amniótico: \nEdad gestacional estimada: ',
     NULL, NULL),
    ('MAMOGRAFIA', 'Mamografía bilateral', 'MG', 'Mama', 240, 1440, 7200,
     E'Composición mamaria (ACR): \nMasas: \nCalcificaciones: \nDistorsión de la arquitectura: \nGanglios axilares: ',
     'BI-RADS: ', NULL);

-- ================= Patología =================

-- RFPAT-005: plantilla por tipo de estudio (y opcionalmente por servicio). Los campos se definen en JSON:
-- [{"clave","etiqueta","seccion":"MACRO|MICRO","tipo":"texto|numero|opcion","opciones":[...],"requerido":bool,"unidad"}]
CREATE TABLE plantilla_patologia (
    id             BIGSERIAL PRIMARY KEY,
    tipo           VARCHAR(30)  NOT NULL,
    servicio       VARCHAR(80),
    nombre         VARCHAR(120) NOT NULL,
    campos         TEXT         NOT NULL,
    modificado_en  TIMESTAMPTZ,
    modificado_por VARCHAR(60)
);

-- RFPAT-002: numeración única global; la secuencia nunca devuelve un número ya usado
CREATE SEQUENCE seq_numero_caso_patologia START 1;

CREATE TABLE caso_patologia (
    id                         BIGSERIAL PRIMARY KEY,
    numero                     VARCHAR(20)  NOT NULL UNIQUE,
    paciente_id                BIGINT       NOT NULL REFERENCES paciente (id),
    consulta_id                BIGINT       REFERENCES consulta (id),
    tipo                       VARCHAR(30)  NOT NULL,
    prioridad                  VARCHAR(20)  NOT NULL,
    sitio_anatomico            VARCHAR(150) NOT NULL,
    datos_clinicos             VARCHAR(1000),
    numero_muestras            INT          NOT NULL,
    medico                     VARCHAR(60)  NOT NULL,
    medico_nombre              VARCHAR(150) NOT NULL,
    estado                     VARCHAR(30)  NOT NULL,
    solicitado_en              TIMESTAMPTZ  NOT NULL,
    recibido_en                TIMESTAMPTZ,
    recibido_por               VARCHAR(60),
    plantilla_id               BIGINT       REFERENCES plantilla_patologia (id),
    macroscopia                TEXT,
    microscopia                TEXT,
    diagnostico                VARCHAR(2000),
    macroscopia_por            VARCHAR(60),
    macroscopia_en             TIMESTAMPTZ,
    validado_en                TIMESTAMPTZ,
    validado_por               VARCHAR(60),
    -- RFPAT-008: fecha hasta la que se conserva el material y el informe
    retencion_hasta            DATE
);
CREATE INDEX ix_caso_pat_paciente ON caso_patologia (paciente_id);
CREATE INDEX ix_caso_pat_estado ON caso_patologia (estado);

-- RFPAT-006 / RFPAT-007: cada validación es una versión inmutable con autor, motivo y huella
CREATE TABLE informe_patologia (
    id            BIGSERIAL PRIMARY KEY,
    caso_id       BIGINT        NOT NULL REFERENCES caso_patologia (id),
    version       INT           NOT NULL,
    macroscopia   TEXT          NOT NULL,
    microscopia   TEXT          NOT NULL,
    diagnostico   VARCHAR(2000) NOT NULL,
    motivo_cambio VARCHAR(300),
    autor         VARCHAR(60)   NOT NULL,
    autor_nombre  VARCHAR(150)  NOT NULL,
    validado_en   TIMESTAMPTZ   NOT NULL,
    firma_hash    VARCHAR(64)   NOT NULL,
    UNIQUE (caso_id, version)
);

-- RFPAT-008: cadena de custodia de bloques, láminas y piezas
CREATE TABLE custodia_patologia (
    id           BIGSERIAL PRIMARY KEY,
    caso_id      BIGINT       NOT NULL REFERENCES caso_patologia (id),
    material     VARCHAR(20)  NOT NULL,
    accion       VARCHAR(20)  NOT NULL,
    cantidad     INT          NOT NULL,
    ubicacion    VARCHAR(150),
    destinatario VARCHAR(150),
    motivo       VARCHAR(300),
    usuario      VARCHAR(60)  NOT NULL,
    fecha        TIMESTAMPTZ  NOT NULL
);

CREATE TABLE evento_caso_patologia (
    id      BIGSERIAL PRIMARY KEY,
    caso_id BIGINT       NOT NULL REFERENCES caso_patologia (id),
    accion  VARCHAR(40)  NOT NULL,
    detalle VARCHAR(500),
    usuario VARCHAR(60)  NOT NULL,
    fecha   TIMESTAMPTZ  NOT NULL
);

INSERT INTO plantilla_patologia (tipo, nombre, campos) VALUES
    ('BIOPSIA', 'Biopsia', '[
      {"clave":"fragmentos","etiqueta":"Número de fragmentos","seccion":"MACRO","tipo":"numero","requerido":true},
      {"clave":"medidas","etiqueta":"Medidas (mm)","seccion":"MACRO","tipo":"texto","requerido":true},
      {"clave":"color_consistencia","etiqueta":"Color y consistencia","seccion":"MACRO","tipo":"texto","requerido":false},
      {"clave":"cassettes","etiqueta":"Cassettes incluidos","seccion":"MACRO","tipo":"numero","requerido":true},
      {"clave":"descripcion_micro","etiqueta":"Descripción microscópica","seccion":"MICRO","tipo":"texto","requerido":true},
      {"clave":"displasia","etiqueta":"Displasia","seccion":"MICRO","tipo":"opcion","opciones":["No","Bajo grado","Alto grado","No evaluable"],"requerido":true},
      {"clave":"malignidad","etiqueta":"Malignidad","seccion":"MICRO","tipo":"opcion","opciones":["Negativo","Sospechoso","Positivo"],"requerido":true}
    ]'),
    ('CITOLOGIA', 'Citología', '[
      {"clave":"laminas","etiqueta":"Láminas recibidas","seccion":"MACRO","tipo":"numero","requerido":true},
      {"clave":"volumen","etiqueta":"Volumen (mL, líquidos)","seccion":"MACRO","tipo":"numero","requerido":false},
      {"clave":"calidad","etiqueta":"Calidad de la muestra","seccion":"MACRO","tipo":"opcion","opciones":["Satisfactoria","Insatisfactoria"],"requerido":true},
      {"clave":"descripcion_micro","etiqueta":"Descripción citológica","seccion":"MICRO","tipo":"texto","requerido":true},
      {"clave":"categoria","etiqueta":"Categoría","seccion":"MICRO","tipo":"opcion","opciones":["Negativo para malignidad","Atipia de significado indeterminado","Sospechoso de malignidad","Positivo para malignidad"],"requerido":true}
    ]'),
    ('PIEZA_QUIRURGICA', 'Pieza quirúrgica', '[
      {"clave":"pieza","etiqueta":"Pieza recibida","seccion":"MACRO","tipo":"texto","requerido":true},
      {"clave":"peso","etiqueta":"Peso","seccion":"MACRO","tipo":"numero","unidad":"g","requerido":true},
      {"clave":"medidas","etiqueta":"Medidas (mm)","seccion":"MACRO","tipo":"texto","requerido":true},
      {"clave":"lesion","etiqueta":"Descripción de la lesión","seccion":"MACRO","tipo":"texto","requerido":true},
      {"clave":"cassettes","etiqueta":"Cassettes incluidos","seccion":"MACRO","tipo":"numero","requerido":true},
      {"clave":"descripcion_micro","etiqueta":"Descripción microscópica","seccion":"MICRO","tipo":"texto","requerido":true},
      {"clave":"tipo_histologico","etiqueta":"Tipo histológico","seccion":"MICRO","tipo":"texto","requerido":true},
      {"clave":"grado","etiqueta":"Grado histológico","seccion":"MICRO","tipo":"opcion","opciones":["No aplica","G1","G2","G3"],"requerido":true},
      {"clave":"margenes","etiqueta":"Márgenes","seccion":"MICRO","tipo":"opcion","opciones":["Libres","Comprometidos","No evaluables"],"requerido":true},
      {"clave":"margen_mm","etiqueta":"Distancia al margen más cercano","seccion":"MICRO","tipo":"numero","unidad":"mm","requerido":false},
      {"clave":"invasion_lv","etiqueta":"Invasión linfovascular","seccion":"MICRO","tipo":"opcion","opciones":["No identificada","Presente","No evaluable"],"requerido":true},
      {"clave":"ganglios","etiqueta":"Ganglios (positivos / examinados)","seccion":"MICRO","tipo":"texto","requerido":false}
    ]');

-- ================= Gestión documental =================

CREATE TABLE documento (
    id                 BIGSERIAL PRIMARY KEY,
    paciente_id        BIGINT       NOT NULL REFERENCES paciente (id),
    episodio_id        BIGINT       REFERENCES episodio (id),
    tipo               VARCHAR(40)  NOT NULL,
    titulo             VARCHAR(200) NOT NULL,
    descripcion        VARCHAR(1000),
    fecha_documento    DATE         NOT NULL,
    servicio           VARCHAR(80)  NOT NULL,
    profesional        VARCHAR(150),
    soporte            VARCHAR(20)  NOT NULL,
    estado             VARCHAR(20)  NOT NULL,
    origen             VARCHAR(20)  NOT NULL,
    -- RFGAC-009: documento generado por otro módulo (p. ej. INFORME_RADIOLOGIA 15)
    modulo_origen      VARCHAR(30),
    referencia_origen  VARCHAR(60),
    enlace             VARCHAR(200),
    version_actual     INT          NOT NULL DEFAULT 0,
    archivo_id         BIGINT       REFERENCES archivo (id),
    -- RFGAC-010: ubicación física
    sala               VARCHAR(60),
    estanteria         VARCHAR(60),
    seccion            VARCHAR(60),
    caja               VARCHAR(60),
    retencion_hasta    DATE         NOT NULL,
    motivo_estado      VARCHAR(300),
    creado_en          TIMESTAMPTZ  NOT NULL,
    creado_por         VARCHAR(60),
    modificado_en      TIMESTAMPTZ  NOT NULL,
    modificado_por     VARCHAR(60),
    UNIQUE (modulo_origen, referencia_origen)
);
CREATE INDEX ix_documento_paciente ON documento (paciente_id);
CREATE INDEX ix_documento_tipo_fecha ON documento (tipo, fecha_documento);

CREATE TABLE documento_version (
    id           BIGSERIAL PRIMARY KEY,
    documento_id BIGINT       NOT NULL REFERENCES documento (id),
    version      INT          NOT NULL,
    archivo_id   BIGINT       NOT NULL REFERENCES archivo (id),
    motivo       VARCHAR(300),
    usuario      VARCHAR(60)  NOT NULL,
    fecha        TIMESTAMPTZ  NOT NULL,
    UNIQUE (documento_id, version)
);

-- RNFGAC-006 / RNFGAC-010: toda acción sobre un documento, incluida la consulta
CREATE TABLE documento_evento (
    id           BIGSERIAL PRIMARY KEY,
    documento_id BIGINT       NOT NULL REFERENCES documento (id),
    accion       VARCHAR(30)  NOT NULL,
    detalle      VARCHAR(500),
    usuario      VARCHAR(60)  NOT NULL,
    fecha        TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_documento_evento_fecha ON documento_evento (fecha);

-- RFGAC-008: solicitudes de documentos al archivo clínico
CREATE TABLE solicitud_archivo (
    id                 BIGSERIAL PRIMARY KEY,
    paciente_id        BIGINT       NOT NULL REFERENCES paciente (id),
    documento_id       BIGINT       REFERENCES documento (id),
    solicitante        VARCHAR(60)  NOT NULL,
    solicitante_nombre VARCHAR(150) NOT NULL,
    motivo             VARCHAR(500) NOT NULL,
    urgente            BOOLEAN      NOT NULL,
    estado             VARCHAR(20)  NOT NULL,
    solicitada_en      TIMESTAMPTZ  NOT NULL,
    atendida_por       VARCHAR(60),
    entregada_en       TIMESTAMPTZ,
    devuelta_en        TIMESTAMPTZ,
    respuesta          VARCHAR(500)
);
