package co.edu.ucentral.clinica.ia.asistente;

/** Modelo de lenguaje externo. Solo recibe texto ya anonimizado. */
public interface AsistenteLenguaje {

    record Respuesta(String texto, String modelo, Integer tokensEntrada, Integer tokensSalida) {
    }

    /** true si está encendido y tiene endpoint, despliegue y llave. */
    boolean disponible();

    boolean habilitado();

    boolean configurado();

    String modelo();

    Respuesta completar(String instrucciones, String contenido);
}
