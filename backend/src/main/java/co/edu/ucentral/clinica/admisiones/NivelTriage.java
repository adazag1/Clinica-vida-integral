package co.edu.ucentral.clinica.admisiones;

/** Niveles de triage de la Resolución 5596 de 2015 (Colombia). */
public final class NivelTriage {

    private NivelTriage() {
    }

    public static String descripcion(int nivel) {
        return switch (nivel) {
            case 1 -> "Triage I – Atención inmediata";
            case 2 -> "Triage II – Muy urgente (máx. 30 min)";
            case 3 -> "Triage III – Urgente";
            case 4 -> "Triage IV – Prioritario";
            case 5 -> "Triage V – No urgente";
            default -> throw new IllegalArgumentException("Nivel de triage inválido: " + nivel);
        };
    }
}
