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
| Frontend | `ng serve` (puerto 4200) | Azure Static Web Apps |
| Backend | `mvnw spring-boot:run` (puerto 8080) | Azure App Service (Java 21) |
| Base de datos | PostgreSQL 17 en Docker | Azure Database for PostgreSQL Flexible Server |
| Secretos | `application-local.yml` | Azure Key Vault |
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

## Fases

- [x] **Fase 1** — Base: login JWT, roles, auditoría de accesos, layout del frontend
- [x] **Fase 2** — Pacientes, admisiones y triage (RFADM-002 a RFADM-013)
- [ ] Fase 3 — Agendamiento
- [ ] Fase 4 — Consulta médica / historia clínica
- [ ] Fase 5 — Órdenes, laboratorio y notificaciones
- [ ] Fase 6 — Enfermería
- [ ] Fase 7 — Radiología, patología y gestión documental
- [ ] Fase 8 — Facturación y auditoría clínica
- [ ] Fase 9 — Motor de IA (reglas + Azure OpenAI)
- [ ] Fase 10 — Dashboards CEO/CFO y despliegue final en Azure
