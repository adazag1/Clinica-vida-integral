# Clínica Vida Integral — Plataforma de Diagnóstico Asistido por IA

Proyecto de Ingeniería de Software I (Universidad Central, 2026 — Team 8).

Plataforma que centraliza la historia clínica digital e integra agendamiento, facturación,
laboratorio (LIS), imágenes diagnósticas (PACS) y patología, con un motor de apoyo diagnóstico
(reglas clínicas + Azure OpenAI).

## Arquitectura

**Monolito modular**: un backend Spring Boot cuyos paquetes corresponden uno a uno con los
microservicios del documento de arquitectura. Cada módulo se puede extraer como microservicio
independiente cuando sea necesario (ver riesgo #11 del documento).

```
clinica-vida-integral/
├── backend/     Spring Boot 4 · Java 21 · PostgreSQL · JWT
├── frontend/    Angular 21
└── docker-compose.yml   PostgreSQL local
```

| Componente | Local | Azure |
|---|---|---|
| Frontend | `ng serve` (puerto 4200) | Azure Storage, sitio web estático |
| Backend | `mvnw spring-boot:run` (puerto 8080) | Azure App Service (Linux, Java 21, B1) |
| Base de datos | PostgreSQL 17 en Docker | Azure Database for PostgreSQL Flexible Server 17 (B1ms) |
| Secretos | `application-local.yml` | Configuración de la aplicación en App Service |
| Monitoreo | consola | Application Insights |

## Cómo probar la aplicación (sin instalar nada)

Abra **https://clinicavidaweb.z47.web.core.windows.net** en el navegador. La aplicación publicada usa la base de datos
de Azure: no hace falta Docker ni clonar el repositorio. Pida la contraseña de los usuarios de prueba al responsable del
despliegue (no está en este repositorio). Un recorrido sugerido:

| Usuario | Qué probar |
|---|---|
| `admisiones` | Registrar un paciente, abrir un episodio de urgencias y hacer el triage |
| `agendamiento` | Agendar una cita y ver la disponibilidad de los médicos |
| `medico` | Atender al paciente: historia clínica, diagnóstico, fórmula, órdenes de laboratorio e imágenes, firmar la consulta; ver las **alertas de IA** |
| `enfermeria` | Registrar signos vitales (una presión de 190/120 dispara una alerta crítica) y administrar medicamentos |
| `laboratorio` | Recibir la muestra, registrar y validar resultados (un valor crítico avisa al médico) |
| `radiologia` / `patologia` | Informar un estudio de imágenes o un caso de patología |
| `facturacion` / `auditoria` | Revisar la cuenta que se generó sola, auditarla y facturarla |
| `archivo` | Buscar documentos clínicos y atender solicitudes |
| `directivo` | **Indicadores** (tablero del CEO y reporte del CFO), **Motor de IA** y cartera |

Todos los datos deben ser ficticios.

## Cómo ejecutar en local

Requisitos: JDK 21, Node 22+, Docker Desktop.

```bash
# 1. Base de datos
docker compose up -d

# 2. Backend  (http://localhost:8080/swagger-ui.html)
cd backend
./mvnw spring-boot:run

# 3. Frontend (http://localhost:4200)
cd frontend
npm start
```

### Usuarios de prueba

Con el perfil `local` se crea un usuario ficticio por rol. El nombre de usuario es el rol en
minúscula (`medico`, `enfermeria`, `laboratorio`, `radiologia`, `admisiones`, `agendamiento`,
`facturacion`, `auditoria`, `archivo`, `directivo`, `paciente`, `admin`) y la contraseña es la
definida en `app.seed.password-prueba` de `backend/src/main/resources/application-local.yml`.

> ⚠️ Usar solo datos ficticios. Nunca cargar datos reales de pacientes (Ley 1581 de 2012).

### Pruebas

```bash
cd backend && ./mvnw test      # requiere Docker (Testcontainers levanta PostgreSQL)
cd frontend && npm test
```

## Flujo de admisión (Fase 2)

1. **Pacientes** → buscar por documento, nombre, fecha de nacimiento o teléfono (sin importar tildes).
   Al registrar uno nuevo, el sistema avisa si hay pacientes parecidos (nombre mal escrito, misma
   fecha de nacimiento, mismo teléfono) antes de crear un duplicado. Sin documento se genera un
   identificador temporal `TMP-…`. Todo cambio de datos exige un motivo y queda en el historial.
2. **Admitir paciente** → genera el número de atención `AT-año-######` (urgencias, consulta externa u
   hospitalización). Un paciente no puede tener dos atenciones abiertas.
3. **Admisiones y triage** → lista de espera que se actualiza sola. En cada atención:
   - *Triage* (urgencias): signos vitales con validación de rangos y nivel sugerido I–V; si se asigna
     otro nivel hay que justificarlo. Los umbrales de `CalculadoraTriage` deben validarlos los médicos.
   - *Cobertura*: resultado de la verificación con la aseguradora, o atención sin autorización en urgencias.
   - *Consentimientos* con quién los otorga, fecha, hora y responsable.
4. **Enviar a consulta** solo se habilita cuando no falta ningún requisito; el paciente pasa a la
   columna del médico ordenado por gravedad.

## Agendamiento (Fase 3)

- **Agenda del día** con una columna por profesional, indicadores (citas, confirmadas, lista de espera,
  canceladas, asistencia) y ocupación de cada médico. Clic en un horario libre para agendar.
- **Disponibilidad**: cada profesional tiene franjas semanales; la duración la fija el tipo de cita. La
  última hora de la tarde de los profesionales de prueba está reservada para citas prioritarias y urgentes.
- **Sin sobre-reservas**: al agendar se bloquea la agenda del profesional en la base de datos, así dos
  personas que toman el mismo cupo al mismo tiempo no pueden quedar ambas con él (hay una prueba de esto).
- **Cancelar / reprogramar** con motivo e historial. El cupo liberado pasa automáticamente al primero de la
  **lista de espera** (por prioridad y antigüedad) y agendamiento recibe un aviso para llamarlo.
- **Recordatorios** 24 h antes con un enlace (`/cita/<token>`) donde el paciente confirma o cancela sin cuenta.
  Por correo si se configura `spring.mail.*`; sin proveedor quedan registrados como *simulados*. El SMS
  requiere contratar un proveedor.
- **Preparaciones**: al agendar una ecografía o un procedimiento se avisa al área (radiología, enfermería).
- **Horarios y bloqueos** (vacaciones, permisos) en *Agendamiento → Horarios y bloqueos*.
- **Campana de notificaciones**: también avisa a los médicos cuando un paciente de admisiones queda listo.

Con el perfil `local` se crean 4 profesionales de prueba; el usuario `medico` es el Dr. Juan Pérez y ve su agenda.

## Consulta médica e historia clínica (Fase 4)

- **Pacientes por atender** (usuario `medico`): sus consultas abiertas, los pacientes que admisiones dejó
  listos y sus citas de hoy. Los críticos (triage I-II o signos vitales críticos) van primero y en rojo.
- **Pantalla de consulta** según el mockup: motivo, enfermedad actual, examen físico, diagnósticos CIE-10
  (uno principal; impresión o confirmado), plan, signos vitales con semáforo y fórmula médica.
- **Resumen clínico** al lado: alergias, medicamentos actuales, diagnósticos activos, triage y antecedentes.
- **Alergias**: la fórmula usa un catálogo con principio activo y familia; si el paciente es alérgico
  (p. ej. a la penicilina) la prescripción de un medicamento de esa familia se **bloquea**.
- **Firma**: no se puede finalizar sin motivo, enfermedad actual, examen físico, diagnóstico principal y
  plan. El médico confirma con su contraseña; se guarda una huella SHA-256 del contenido y la consulta
  queda inmutable. "Verificar integridad" detecta si alguien la alteró en la base de datos.
  *Es una firma electrónica; la firma digital con certificado (Ley 527 de 1999) requiere contratar una
  entidad de certificación.*
- **Rangos de signos vitales** en `app.signos-vitales` (`application.yml`): deben validarlos los médicos.
- **Historia clínica** (`/historia/<paciente>`): resumen, antecedentes (se inactivan con motivo, nunca se
  borran), línea de tiempo de consultas y signos registrados. Solo la ven médicos, enfermería y auditoría.

## Laboratorio clínico (Fase 5)

- El **médico ordena** exámenes desde la consulta (urgente, hospitalizado o ambulatorio) y ve el estado de
  cada orden: pendiente de muestra, muestra recibida, en proceso, validada o muestra rechazada.
- El **laboratorio** (usuario `laboratorio`) trabaja una cola con los urgentes primero y alerta de tiempo de
  respuesta (TAT) excedido. La pantalla de la orden sigue el mockup: exámenes, estado de la muestra y resultados.
- Cada resultado se interpreta con el **rango de su sexo y edad** (normal, bajo/alto, crítico) y se compara con
  el resultado previo del paciente (**delta check**). También se revisa la coherencia hemoglobina/hematocrito.
- **No se libera un resultado** sin: control de calidad aceptado hoy en el área, equipo/lote de reactivo/
  calibración, datos completos del paciente y de la muestra, y —si hay un valor crítico— haberlo repetido para
  confirmarlo y registrado a quién se informó. Cambiar un valor ya registrado exige "Repetir" con motivo.
- Al validar, el médico recibe el aviso (y uno especial si hay un **valor crítico**); los resultados aparecen
  con semáforo en su resumen clínico. Las muestras rechazadas llevan motivo estructurado y avisan al médico.
- **Rangos configurables** en *Laboratorio → Rangos de referencia y críticos*, sin programar. Los iniciales son
  de adultos de referencia general: el laboratorio debe ajustarlos a su método y población.

## Enfermería (Fase 6)

- **Pacientes a cargo** (usuario `enfermeria`): quienes tienen órdenes vigentes o están en urgencias u
  hospitalización, con dosis atrasadas, próximas, alergias y signos críticos; lo urgente va primero.
- El **médico ordena** desde la consulta, incluso después de firmarla: medicamento con dosis, vía, frecuencia
  y duración, dieta o cuidado de enfermería. Enfermería recibe el aviso. Si el paciente es alérgico, se bloquea.
- **Hoja del paciente**: todas las órdenes con su horario de las últimas y próximas 12 h (pendiente, atrasada,
  administrada, omitida) y los exámenes de laboratorio pendientes de toma de muestra.
- **Administración segura (cinco correctos)**: la enfermera confirma documento del paciente, medicamento,
  dosis y vía, y el sistema los compara con la orden. La hora la pone el servidor; fuera de ±30 min exige
  justificar el retraso. Omitir o registrar un rechazo exige motivo y avisa al médico.
- **Alto riesgo** (insulinas, heparinas, opioides): exige la doble verificación de otro profesional de
  enfermería o un médico con su usuario y contraseña. Las contraseñas erradas cuentan para bloquear la cuenta.
- Una orden **vencida o suspendida** ya no se puede ejecutar; al suspenderla el médico, enfermería se entera.
- **Eventos adversos** clasificados por tipo y severidad; todos llegan a auditoría y los graves también a los médicos.
- **Notas de enfermería** y **entrega de turno** (mañana 7–13, tarde 13–19, noche 19–7): el resumen se arma solo
  con lo registrado y se guarda tal como estaba al entregar.
- Signos críticos registrados sin consulta abierta avisan a los médicos. El laboratorio avisa a enfermería
  para tomar la muestra y cuando hay que repetirla.

## Imágenes diagnósticas, patología y gestión documental (Fase 7)

**Imágenes diagnósticas** (usuarios `radiologia` y `radiologia2`):
- El médico ordena el estudio desde la consulta (motivo y diagnóstico presuntivo). La **lista de trabajo** pone
  primero los urgentes y los que exceden el tiempo objetivo de su tipo de estudio (configurable).
- Pantalla del mockup: estudios del paciente (actual y previos), **visor** (zoom, desplazamiento, brillo/contraste,
  rotación, espejo, pantalla completa, miniaturas, imágenes clave) e **informe** precargado con la plantilla del estudio.
- Firmar exige contraseña; un **hallazgo crítico** exige registrar a quién se comunicó y cuándo. Una plantilla sin
  completar no se firma. Corregir un informe firmado abre una **versión nueva** con motivo; la firmada se conserva.
- **Segunda lectura** a otro radiólogo con concordancia estructurada; **bloqueo** de estudios con errores de
  identificación (avisa a admisiones y al médico); **métricas** de tiempo de respuesta por tipo de estudio.
- Imágenes JPG, PNG o DICOM (el tipo se valida por el contenido, no por la extensión). *El navegador muestra JPG y
  PNG; los DICOM se ven en el visor del PACS. No hay un PACS conectado: se guarda el StudyInstanceUID y, al configurar
  `PACS_VISOR_URL` (p. ej. OHIF sobre Orthanc), aparece el botón «Abrir en el PACS».*

**Patología** (usuario `patologia`):
- Número de caso único que nunca se reutiliza (`B26-000001`: biopsia, `C…` citología, `Q…` pieza quirúrgica). Los
  **intraoperatorios** tienen cola aparte y avisan de inmediato.
- Recepción con **control de identificación**: el documento del rótulo debe coincidir con el paciente; si no, la
  muestra no entra y queda registrado. Macroscopía y microscopía en **campos estructurados** según plantillas por
  tipo de estudio, personalizables por servicio sin programar.
- Validar exige contraseña y guarda una **versión inmutable** con autor, fecha y huella; una corrección exige motivo.
- **Cadena de custodia** de bloques, láminas y piezas (archivo, préstamo, devolución); el descarte solo se permite
  cumplida la retención.

**Gestión documental** (usuario `archivo`; también médicos, enfermería, auditoría y admisiones según el tipo):
- Registro de documentos digitales (PDF, JPG, PNG) o físicos con metadatos, **versiones** con motivo y huella,
  **búsqueda avanzada**, **ubicación física** (sala, estantería, sección, caja) y ciclo de vida (activo, archivado,
  en custodia, eliminado según la retención). Toda consulta o descarga queda registrada.
- Las consultas firmadas, los resultados de laboratorio y los informes de imágenes y patología se registran solos
  en la historia del paciente. **Solicitudes al archivo** (urgentes primero) y **reportes** de volumen, accesos y tiempos.
- Admisiones solo ve y carga documentos administrativos (consentimientos, identidad, autorizaciones).
- *Retención de 15 años (Resolución 839 de 2017) en `app.documentos.retencion-anos` y `app.patologia.retencion-anos`:
  deben validarla el comité de historias clínicas y el área jurídica. Los archivos se guardan en PostgreSQL (máx.
  10 MB c/u) y entran en su respaldo; para volúmenes grandes conviene moverlos a Azure Blob Storage.*

## Facturación y auditoría clínica (Fase 8)

**Facturación** (usuario `facturacion`):
- **Cargos automáticos**: la consulta firmada, cada examen validado, el informe de imágenes o patología firmado y cada
  dosis administrada generan su cargo, con el documento clínico como soporte. Se generan *después* de confirmar el acto
  clínico: un problema de facturación nunca impide firmar o administrar (si falla, facturación recibe el aviso).
- **Contratos** por aseguradora: por evento (factor sobre la tarifa base) o capitación; reglas por servicio (tarifa
  pactada, exclusión a cargo del paciente, tope de cantidad, autorización obligatoria), paquetes y tope por cuenta.
  Hay contratos de prueba con Nueva EPS, Sanitas (con paquete de dolor torácico), Compensar (cápita) y Particular.
- **Simulación** antes de facturar: exactamente lo que se emitiría, con advertencias y bloqueos. No se factura sin
  soporte clínico, sin autorización cuando se exige (salvo urgencias) ni dos veces el mismo cargo. Los cargos se
  anulan con motivo, nunca se borran.
- Ciclo de la cuenta: abierta → auditoría (o devuelta) → auditada → facturada → glosada → pagada → cerrada, con
  historial completo. **Glosas** con concepto y código del Manual Único de Glosas y plazo de respuesta de 15 días
  hábiles; **pagos**; **cartera** por edades con alertas a los 30, 60 y 90 días de mora.
- *Factura electrónica*: se genera el XML UBL 2.1 y el CUFE (SHA-384), pero queda **SIMULADA**: emitir ante la DIAN
  requiere contratar un proveedor tecnológico, la resolución de numeración, el certificado de firma digital de la
  clínica y el anexo de RIPS del sector salud. La integración queda lista en `ProveedorFacturaElectronica`.

**Auditoría clínica** (usuario `auditoria`):
- **Expediente** completo de la atención en una pantalla: notas médicas, órdenes y resultados, imágenes, patología,
  documentos, consentimientos y cargos, con una **lista de verificación** (soporte de cada cargo, notas firmadas,
  autorización, codificación).
- **Codificación CIE-10** con sugerencias a partir del texto clínico y la evidencia encontrada; el auditor confirma.
  Cambiar un código exige motivo y queda en el historial.
- **Aprobar** o **devolver** con motivo a facturación, al médico o a admisiones; se mide el tiempo de corrección.
  Quien registró cargos manuales en una cuenta no puede aprobarla.
- **Indicadores**: tasa de devolución, tiempos de corrección, historias sin firmar, cargos sin soporte y glosas por
  causa clínica. **Catálogo CIE-10 con versiones**: agregar, renombrar o inactivar un código sube la versión.
- *Las tarifas, contratos y códigos CUPS son valores de prueba: los define el área financiera de la clínica.*

## Motor de IA (Fase 9)

**Alertas clínicas por reglas** (en la consulta y la historia clínica; médico y enfermería):
- 9 reglas basadas en escalas y guías publicadas, cada una con su referencia: riesgo de sepsis (qSOFA / SIRS), lesión
  renal aguda (KDIGO), metformina con TFG baja (CKD-EPI 2021), potasio alterado con fármacos que lo empeoran, troponina
  elevada con o sin dolor torácico, crisis hipertensiva, glucosa alterada con insulina, caída de hemoglobina y hallazgo
  crítico sin nota médica posterior.
- Cada alerta muestra severidad, puntaje y escala, **la evidencia concreta** (valor y hace cuánto) y una recomendación.
  Se reevalúan solas al llegar signos, resultados, informes, dosis o una nota firmada, y al abrir la historia. No se
  duplican; si la regla deja de cumplirse se cierran, pero se conservan. Una alerta crítica avisa al médico.
- El médico califica cada alerta como **útil / no útil** (con motivo). Con eso, la página **Motor de IA** muestra la
  precisión por regla y los comentarios, y el administrador puede activar o desactivar reglas.
- *Los umbrales deben validarlos el comité médico y el Líder Científico. No es un modelo entrenado: son reglas
  explicables; un modelo propio requiere datos reales y validación clínica.*

**Asistente de lenguaje (Azure OpenAI) — apagado**: resumen de la historia y diagnóstico diferencial. Está construido
pero se enciende solo cuando la clínica apruebe el costo y el tratamiento de datos (`IA_LLM_HABILITADO=true` y
`AZURE_OPENAI_ENDPOINT`, `AZURE_OPENAI_DEPLOYMENT`, `AZURE_OPENAI_API_KEY`). Antes de enviar, el texto se anonimiza
(sin nombre, documento, teléfono, correo, dirección ni fechas exactas; edad en grupos de 5 años); el médico puede ver
exactamente qué se enviaría. Cada uso queda registrado con usuario, tokens y costo estimado, y nunca escribe en la historia.

**Dataset anonimizado** (directivo o administrador): CSV con diagnósticos, laboratorio, signos, imágenes y patología;
cada paciente con un seudónimo (HMAC con sal secreta `IA_DATASET_SAL`), sexo, grupo de edad y día relativo. La
descarga queda auditada. *Antes de compartirlo fuera de la clínica debe aprobarlo el oficial de protección de datos
(Ley 1581 de 2012).*

## Indicadores de dirección (Fase 10)

Página **Indicadores** (usuario `directivo`; también el administrador):

- **Diagnóstico (CEO, US 5.1)**: tiempo promedio de diagnóstico de enfermedades críticas en los últimos 30 días frente a
  la línea base y la meta (−30 % en 18 meses), con mediana, percentil 90, evolución mensual, dónde se va el tiempo
  (llegada → médico, laboratorio, imágenes, patología), desglose por diagnóstico y últimos casos. Se renueva cada minuto.
  *Caso* = consulta firmada con un diagnóstico crítico confirmado (prefijos CIE-10 configurables); el tiempo va desde la
  llegada del paciente (admisión) hasta la firma del diagnóstico.
- **Costos, capacidad y ROI (CFO, US 5.2)**: reporte mensual que cruza el costo administrativo (meta −25 % en 24
  meses) con las atenciones por médico (meta +15 %), lo facturado y recaudado, y el porcentaje de cargos generados
  automáticamente. **ROI**: beneficio neto = ahorro frente a la línea base + margen de las atenciones adicionales −
  costo de la plataforma; se acumula contra la inversión (750.000 USD) y se proyecta a 36 meses con el promedio de
  los últimos tres meses. Exportable a Excel (CSV) e imprimible a PDF.
- **Metas y costos**: la dirección financiera registra cada mes los costos por categoría (personal administrativo,
  archivo físico, transcripción, estudios duplicados, papelería, otros y plataforma) y ajusta líneas base y metas.
- *Las líneas base vienen como **valores de prueba** y el tablero lo advierte hasta que se reemplacen: el Product
  Owner documenta los tiempos reales previos (tarea 5.1.2) y finanzas los costos. Los costos no salen de otro sistema:
  se registran a mano cada mes.*

## Despliegue en Azure

| | Dirección |
|---|---|
| Aplicación | https://clinicavidaweb.z47.web.core.windows.net |
| API | https://clinica-vida-integral-api.azurewebsites.net (Swagger en `/swagger-ui.html`) |

Todo está en el grupo de recursos `rg-clinica-vida-integral`, región **Chile Central** (la suscripción
Azure for Students solo permite Spain Central, Chile Central, Canada Central, Belgium Central y North
Central US; por esa restricción el frontend usa Storage y no Static Web Apps).

- `clinica-vida-integral-db`: PostgreSQL 17, base `clinica`. Solo acepta conexiones desde servicios de Azure, con TLS.
- `clinica-vida-integral-api`: backend con el perfil `azure`. Contraseñas y llave JWT en la configuración de
  la aplicación (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `CORS_ORIGINS`, `SEED_HABILITADO`, `SEED_PASSWORD`).
- `clinicavidaweb`: archivos compilados de Angular en el contenedor `$web`.

Los usuarios de prueba en Azure usan una contraseña **distinta** a la local; la tiene el responsable del
despliegue. Nunca se sube a este repositorio.

### Publicar una nueva versión

```bash
# Backend
cd backend && ./mvnw package -DskipTests
az webapp deploy -g rg-clinica-vida-integral -n clinica-vida-integral-api --src-path target/clinica-backend-0.0.1-SNAPSHOT.jar --type jar

# Frontend
cd frontend && npx ng build
az storage blob upload-batch --account-name clinicavidaweb --auth-mode key -d '$web' -s dist/frontend/browser --overwrite
```

### Ahorrar crédito

La base de datos es lo más costoso. Cuando no se use por varios días:

```bash
az postgres flexible-server stop  -g rg-clinica-vida-integral -n clinica-vida-integral-db
az postgres flexible-server start -g rg-clinica-vida-integral -n clinica-vida-integral-db
```

Azure la vuelve a encender sola a los 7 días. Para borrar todo: `az group delete -n rg-clinica-vida-integral`.

## Fases

- [x] **Fase 1** — Base: login JWT, roles, auditoría de accesos, layout del frontend
- [x] **Fase 2** — Pacientes, admisiones y triage (RFADM-002 a RFADM-013)
- [x] **Fase 3** — Agendamiento (RFAGE-002 a RFAGE-015) y notificaciones internas
- [x] **Fase 4** — Consulta médica e historia clínica (RFMT-002, 003, 007 a 012)
- [x] **Fase 5** — Órdenes, laboratorio y notificaciones (RFLC-002 a 015, RFMT-004 a 006)
- [x] **Fase 6** — Enfermería (RFENF-002 a 010)
- [x] **Fase 7** — Imágenes diagnósticas, patología y gestión documental (RFRAD, RFPAT, RFGAC)
- [x] **Fase 8** — Facturación y auditoría clínica (RFRC-002 a 014, RFAU-002 a 009)
- [x] **Fase 9** — Motor de IA: reglas clínicas con retroalimentación, asistente Azure OpenAI (apagado) y dataset anonimizado
- [x] **Fase 10** — Tablero clínico del CEO, reporte financiero y de ROI del CFO, y despliegue final en Azure
