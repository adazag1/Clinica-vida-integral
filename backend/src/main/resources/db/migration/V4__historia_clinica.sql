-- Fase 4: consulta médica e historia clínica (RFMT-002, 003, 007 a 012)

-- Catálogo CIE-10 (subconjunto de los diagnósticos más frecuentes en consulta externa y urgencias)
CREATE TABLE diagnostico (
    id      BIGSERIAL PRIMARY KEY,
    codigo  VARCHAR(10)  NOT NULL UNIQUE,
    nombre  VARCHAR(200) NOT NULL
);

-- RFMT-003: la fórmula usa este catálogo para poder cruzar el medicamento con las alergias
CREATE TABLE medicamento (
    id                BIGSERIAL PRIMARY KEY,
    nombre            VARCHAR(150) NOT NULL UNIQUE,
    principio_activo  VARCHAR(100) NOT NULL,
    familia           VARCHAR(100) NOT NULL,
    -- Otros nombres con que se registran las alergias (p. ej. "aspirina" para ácido acetilsalicílico)
    sinonimos         VARCHAR(200),
    presentacion      VARCHAR(100) NOT NULL,
    via               VARCHAR(40)  NOT NULL
);

-- Antecedentes del paciente: alergias, medicamentos actuales, enfermedades crónicas, cirugías...
CREATE TABLE antecedente (
    id                BIGSERIAL PRIMARY KEY,
    paciente_id       BIGINT       NOT NULL REFERENCES paciente (id),
    tipo              VARCHAR(30)  NOT NULL,
    descripcion       VARCHAR(300) NOT NULL,
    detalle           VARCHAR(500),
    severidad         VARCHAR(20),
    activo            BOOLEAN      NOT NULL DEFAULT TRUE,
    motivo_inactivacion VARCHAR(300),
    creado_en         TIMESTAMPTZ  NOT NULL,
    creado_por        VARCHAR(60),
    modificado_en     TIMESTAMPTZ  NOT NULL,
    modificado_por    VARCHAR(60)
);

CREATE INDEX idx_antecedente_paciente ON antecedente (paciente_id, tipo);

-- RFMT-010: registros de signos vitales (cualquier campo puede faltar salvo la fecha)
CREATE TABLE signos_vitales (
    id                      BIGSERIAL PRIMARY KEY,
    paciente_id             BIGINT       NOT NULL REFERENCES paciente (id),
    consulta_id             BIGINT,
    presion_sistolica       INT,
    presion_diastolica      INT,
    frecuencia_cardiaca     INT,
    frecuencia_respiratoria INT,
    temperatura             NUMERIC(4,1),
    saturacion_oxigeno      INT,
    peso                    NUMERIC(5,1),
    talla                   INT,
    registrado_por          VARCHAR(60)  NOT NULL,
    fecha                   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_signos_paciente ON signos_vitales (paciente_id, fecha);

-- Atención del médico (evolución). Firmada, queda inmutable (RFMT-008 / RFMT-011).
CREATE TABLE consulta (
    id                BIGSERIAL PRIMARY KEY,
    paciente_id       BIGINT        NOT NULL REFERENCES paciente (id),
    episodio_id       BIGINT        REFERENCES episodio (id),
    cita_id           BIGINT        REFERENCES cita (id),
    medico            VARCHAR(60)   NOT NULL,
    medico_nombre     VARCHAR(150)  NOT NULL,
    estado            VARCHAR(20)   NOT NULL,
    motivo_consulta   VARCHAR(1000),
    enfermedad_actual VARCHAR(4000),
    examen_fisico     VARCHAR(4000),
    plan              VARCHAR(4000),
    inicio            TIMESTAMPTZ   NOT NULL,
    firmada_en        TIMESTAMPTZ,
    firma_hash        VARCHAR(64),
    creado_en         TIMESTAMPTZ   NOT NULL,
    creado_por        VARCHAR(60),
    modificado_en     TIMESTAMPTZ   NOT NULL,
    modificado_por    VARCHAR(60)
);

CREATE INDEX idx_consulta_paciente ON consulta (paciente_id, inicio);
CREATE INDEX idx_consulta_medico   ON consulta (medico, estado);

ALTER TABLE signos_vitales ADD CONSTRAINT fk_signos_consulta FOREIGN KEY (consulta_id) REFERENCES consulta (id);

CREATE TABLE consulta_diagnostico (
    id              BIGSERIAL PRIMARY KEY,
    consulta_id     BIGINT      NOT NULL REFERENCES consulta (id),
    diagnostico_id  BIGINT      NOT NULL REFERENCES diagnostico (id),
    principal       BOOLEAN     NOT NULL,
    confirmado      BOOLEAN     NOT NULL,
    UNIQUE (consulta_id, diagnostico_id)
);

CREATE TABLE prescripcion (
    id              BIGSERIAL PRIMARY KEY,
    consulta_id     BIGINT       NOT NULL REFERENCES consulta (id),
    medicamento_id  BIGINT       NOT NULL REFERENCES medicamento (id),
    dosis           VARCHAR(60)  NOT NULL,
    frecuencia      VARCHAR(60)  NOT NULL,
    duracion_dias   INT          NOT NULL CHECK (duracion_dias BETWEEN 1 AND 365),
    indicaciones    VARCHAR(500),
    creado_en       TIMESTAMPTZ  NOT NULL,
    creado_por      VARCHAR(60)
);

INSERT INTO diagnostico (codigo, nombre) VALUES
    ('A09', 'Diarrea y gastroenteritis de presunto origen infeccioso'),
    ('B34.9', 'Infección viral, no especificada'),
    ('D50.9', 'Anemia por deficiencia de hierro, sin otra especificación'),
    ('E03.9', 'Hipotiroidismo, no especificado'),
    ('E11.9', 'Diabetes mellitus tipo 2 sin complicaciones'),
    ('E66.9', 'Obesidad, no especificada'),
    ('E78.5', 'Hiperlipidemia, no especificada'),
    ('F32.9', 'Episodio depresivo, no especificado'),
    ('F41.1', 'Trastorno de ansiedad generalizada'),
    ('G43.9', 'Migraña, no especificada'),
    ('H10.9', 'Conjuntivitis, no especificada'),
    ('I10', 'Hipertensión esencial (primaria)'),
    ('I20.9', 'Angina de pecho, no especificada'),
    ('I21.9', 'Infarto agudo del miocardio, sin otra especificación'),
    ('I50.9', 'Insuficiencia cardíaca, no especificada'),
    ('J00', 'Rinofaringitis aguda (resfriado común)'),
    ('J02.9', 'Faringitis aguda, no especificada'),
    ('J03.9', 'Amigdalitis aguda, no especificada'),
    ('J06.9', 'Infección aguda de las vías respiratorias superiores, no especificada'),
    ('J18.9', 'Neumonía, no especificada'),
    ('J20.9', 'Bronquitis aguda, no especificada'),
    ('J30.4', 'Rinitis alérgica, no especificada'),
    ('J44.9', 'Enfermedad pulmonar obstructiva crónica, no especificada'),
    ('J45.9', 'Asma, no especificada'),
    ('K21.9', 'Enfermedad del reflujo gastroesofágico sin esofagitis'),
    ('K29.7', 'Gastritis, no especificada'),
    ('K35.8', 'Apendicitis aguda, otras y las no especificadas'),
    ('K59.0', 'Constipación'),
    ('L03.9', 'Celulitis de sitio no especificado'),
    ('M54.5', 'Lumbago no especificado'),
    ('M79.1', 'Mialgia'),
    ('N18.9', 'Enfermedad renal crónica, no especificada'),
    ('N39.0', 'Infección de vías urinarias, sitio no especificado'),
    ('R05', 'Tos'),
    ('R07.4', 'Dolor en el pecho, no especificado'),
    ('R10.4', 'Otros dolores abdominales y los no especificados'),
    ('R11', 'Náusea y vómito'),
    ('R50.9', 'Fiebre, no especificada'),
    ('R51', 'Cefalea'),
    ('S93.4', 'Esguince y torcedura del tobillo'),
    ('T78.4', 'Alergia no especificada'),
    ('Z00.0', 'Examen médico general');

INSERT INTO medicamento (nombre, principio_activo, familia, sinonimos, presentacion, via) VALUES
    ('Amoxicilina 500 mg', 'amoxicilina', 'penicilinas', 'betalactamicos', 'Cápsula', 'Oral'),
    ('Ampicilina 1 g', 'ampicilina', 'penicilinas', 'betalactamicos', 'Polvo para inyección', 'Intravenosa'),
    ('Penicilina G benzatínica 2.400.000 UI', 'penicilina g benzatinica', 'penicilinas', 'betalactamicos, benzetacil', 'Polvo para inyección', 'Intramuscular'),
    ('Cefalexina 500 mg', 'cefalexina', 'cefalosporinas', 'betalactamicos', 'Cápsula', 'Oral'),
    ('Ceftriaxona 1 g', 'ceftriaxona', 'cefalosporinas', 'betalactamicos', 'Polvo para inyección', 'Intravenosa'),
    ('Azitromicina 500 mg', 'azitromicina', 'macrolidos', NULL, 'Tableta', 'Oral'),
    ('Claritromicina 500 mg', 'claritromicina', 'macrolidos', NULL, 'Tableta', 'Oral'),
    ('Ciprofloxacino 500 mg', 'ciprofloxacino', 'quinolonas', 'fluoroquinolonas', 'Tableta', 'Oral'),
    ('Trimetoprim/sulfametoxazol 160/800 mg', 'sulfametoxazol trimetoprim', 'sulfonamidas', 'sulfas, bactrim', 'Tableta', 'Oral'),
    ('Nitrofurantoína 100 mg', 'nitrofurantoina', 'nitrofuranos', NULL, 'Cápsula', 'Oral'),
    ('Acetaminofén 500 mg', 'acetaminofen', 'analgesicos no opioides', 'paracetamol', 'Tableta', 'Oral'),
    ('Ibuprofeno 400 mg', 'ibuprofeno', 'aine', 'antiinflamatorios no esteroideos', 'Tableta', 'Oral'),
    ('Naproxeno 250 mg', 'naproxeno', 'aine', 'antiinflamatorios no esteroideos', 'Tableta', 'Oral'),
    ('Diclofenaco 75 mg/3 mL', 'diclofenaco', 'aine', 'antiinflamatorios no esteroideos', 'Solución inyectable', 'Intramuscular'),
    ('Ácido acetilsalicílico 100 mg', 'acido acetilsalicilico', 'aine', 'aspirina, asa, salicilatos', 'Tableta', 'Oral'),
    ('Dipirona 1 g/2 mL', 'dipirona', 'pirazolonas', 'metamizol', 'Solución inyectable', 'Intravenosa'),
    ('Tramadol 50 mg', 'tramadol', 'opioides', NULL, 'Cápsula', 'Oral'),
    ('Morfina 10 mg/mL', 'morfina', 'opioides', NULL, 'Solución inyectable', 'Intravenosa'),
    ('Losartán 50 mg', 'losartan', 'antagonistas del receptor de angiotensina', 'ara ii', 'Tableta', 'Oral'),
    ('Enalapril 20 mg', 'enalapril', 'ieca', 'inhibidores de la enzima convertidora', 'Tableta', 'Oral'),
    ('Amlodipino 5 mg', 'amlodipino', 'calcioantagonistas', NULL, 'Tableta', 'Oral'),
    ('Hidroclorotiazida 25 mg', 'hidroclorotiazida', 'diureticos tiazidicos', NULL, 'Tableta', 'Oral'),
    ('Metformina 850 mg', 'metformina', 'biguanidas', NULL, 'Tableta', 'Oral'),
    ('Insulina glargina 100 UI/mL', 'insulina glargina', 'insulinas', NULL, 'Solución inyectable', 'Subcutánea'),
    ('Atorvastatina 20 mg', 'atorvastatina', 'estatinas', NULL, 'Tableta', 'Oral'),
    ('Omeprazol 20 mg', 'omeprazol', 'inhibidores de bomba de protones', NULL, 'Cápsula', 'Oral'),
    ('Loratadina 10 mg', 'loratadina', 'antihistaminicos', NULL, 'Tableta', 'Oral'),
    ('Salbutamol 100 mcg/dosis', 'salbutamol', 'agonistas beta 2', NULL, 'Inhalador', 'Inhalada'),
    ('Prednisolona 5 mg', 'prednisolona', 'corticoides', 'corticosteroides', 'Tableta', 'Oral'),
    ('Metoclopramida 10 mg', 'metoclopramida', 'procineticos', NULL, 'Tableta', 'Oral'),
    ('Enoxaparina 40 mg', 'enoxaparina', 'heparinas', NULL, 'Jeringa prellenada', 'Subcutánea'),
    ('Levotiroxina 50 mcg', 'levotiroxina', 'hormonas tiroideas', NULL, 'Tableta', 'Oral'),
    ('Sertralina 50 mg', 'sertralina', 'isrs', 'inhibidores de la recaptacion de serotonina', 'Tableta', 'Oral');
