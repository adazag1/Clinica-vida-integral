package co.edu.ucentral.clinica.seguridad;

/** Perfiles de usuario identificados en el documento (sección Usuarios Identificados). */
public enum Rol {
    ADMIN("Administrador del sistema"),
    MEDICO("Médico tratante"),
    ENFERMERIA("Enfermería"),
    LABORATORIO("Laboratorio clínico"),
    RADIOLOGIA("Radiología"),
    PATOLOGIA("Patología"),
    ADMISIONES("Admisiones / Triage"),
    AGENDAMIENTO("Agendamiento de citas"),
    FACTURACION("Facturación / Cartera"),
    AUDITORIA("Auditoría clínica / Codificación"),
    ARCHIVO("Gestión documental / Archivo clínico"),
    DIRECTIVO("Directivo (CEO / CFO)"),
    PACIENTE("Paciente");

    private final String descripcion;

    Rol(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
