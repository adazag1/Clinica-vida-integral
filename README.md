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
- [ ] Fase 4 — Consulta médica / historia clínica
- [ ] Fase 5 — Órdenes, laboratorio y notificaciones
- [ ] Fase 6 — Enfermería
- [ ] Fase 7 — Radiología, patología y gestión documental
- [ ] Fase 8 — Facturación y auditoría clínica
- [ ] Fase 9 — Motor de IA (reglas + Azure OpenAI)
- [ ] Fase 10 — Dashboards CEO/CFO y despliegue final en Azure
