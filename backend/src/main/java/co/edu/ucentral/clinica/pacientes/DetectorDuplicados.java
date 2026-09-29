package co.edu.ucentral.clinica.pacientes;

import co.edu.ucentral.clinica.pacientes.dto.PacienteResumenDto;
import co.edu.ucentral.clinica.pacientes.dto.PosibleDuplicadoDto;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * RFADM-003: detecta pacientes que probablemente son la misma persona, aunque el nombre esté
 * mal escrito ("Jhon Peres" vs "John Pérez"), que es la causa principal de historias duplicadas
 * según admisiones.
 *
 * <p>Puntaje: mismo documento 60, nombre muy parecido 30 (parecido 15), misma fecha de
 * nacimiento 20, mismo teléfono 15. Desde {@value #UMBRAL} se considera posible duplicado:
 * p. ej. nombre parecido + misma fecha de nacimiento. Un nombre común solo, o un teléfono
 * compartido por una familia, no alcanzan.</p>
 */
@Component
public class DetectorDuplicados {

    static final int UMBRAL = 35;
    private static final int MAX_CANDIDATOS = 200;
    private static final int MAX_RESULTADOS = 10;

    private final PacienteRepository pacientes;

    public DetectorDuplicados(PacienteRepository pacientes) {
        this.pacientes = pacientes;
    }

    /** @param excluirId el propio paciente cuando se está editando (null al crear) */
    public List<PosibleDuplicadoDto> buscar(DatosDemograficos d, Long excluirId) {
        String nombre = Normalizador.texto(d.nombres() + " " + d.apellidos());
        String documento = d.tipoDocumento() == TipoDocumento.TEMPORAL ? "" : Normalizador.documento(d.numeroDocumento());
        String telefono = Normalizador.telefono(d.telefono());
        String[] apellidos = Normalizador.texto(d.apellidos()).split(" ");
        String primerApellido = apellidos[0].length() >= 3 ? apellidos[0] : "";

        List<Paciente> candidatos = pacientes.buscarCandidatosDuplicado(documento, d.fechaNacimiento(), telefono,
                primerApellido, PageRequest.of(0, MAX_CANDIDATOS));

        List<PosibleDuplicadoDto> resultado = new ArrayList<>();
        for (Paciente p : candidatos) {
            if (p.getId().equals(excluirId)) {
                continue;
            }
            int puntaje = 0;
            List<String> coincidencias = new ArrayList<>();

            if (!documento.isEmpty() && documento.equals(p.getNumeroDocumento())) {
                puntaje += 60;
                coincidencias.add("Mismo número de documento");
            }
            double similitud = Normalizador.similitud(nombre, p.getNombreNormalizado());
            if (similitud >= 0.85) {
                puntaje += 30;
                coincidencias.add(similitud == 1.0 ? "Mismo nombre" : "Nombre muy parecido");
            } else if (similitud >= 0.7) {
                puntaje += 15;
                coincidencias.add("Nombre parecido");
            }
            if (d.fechaNacimiento() != null && d.fechaNacimiento().equals(p.getFechaNacimiento())) {
                puntaje += 20;
                coincidencias.add("Misma fecha de nacimiento");
            }
            if (!telefono.isEmpty() && Objects.equals(telefono, p.getTelefono())) {
                puntaje += 15;
                coincidencias.add("Mismo teléfono");
            }

            if (puntaje >= UMBRAL) {
                resultado.add(new PosibleDuplicadoDto(PacienteResumenDto.de(p), Math.min(puntaje, 100), coincidencias));
            }
        }
        resultado.sort(Comparator.comparingInt(PosibleDuplicadoDto::puntaje).reversed());
        return resultado.size() > MAX_RESULTADOS ? resultado.subList(0, MAX_RESULTADOS) : resultado;
    }
}
