package co.edu.ucentral.clinica.auditoriaclinica;

import co.edu.ucentral.clinica.historia.Diagnostico;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SugeridorDiagnosticosTest {

    private final SugeridorDiagnosticos sugeridor = new SugeridorDiagnosticos();
    private final List<Diagnostico> catalogo = List.of(
            new Diagnostico("J18.9", "Neumonía, no especificada"),
            new Diagnostico("R50.9", "Fiebre, no especificada"),
            new Diagnostico("R05", "Tos"),
            new Diagnostico("I10", "Hipertensión esencial (primaria)"),
            new Diagnostico("K35.8", "Apendicitis aguda, otras y las no especificadas"));

    @Test
    void encuentraLosDiagnosticosDelTextoConSuEvidencia() {
        List<SugeridorDiagnosticos.Sugerencia> s = sugeridor.sugerir(List.of(
                "Paciente con tos productiva y fiebre de 39 °C hace 5 días",
                "Crépitos basales derechos. Rx: consolidación en lóbulo inferior derecho"), catalogo, Set.of());
        assertThat(s).extracting(SugeridorDiagnosticos.Sugerencia::codigo).contains("J18.9", "R50.9", "R05").doesNotContain("I10", "K35.8");
        assertThat(s.stream().filter(x -> x.codigo().equals("J18.9")).findFirst().orElseThrow().evidencia()).contains("consolidacion");
    }

    @Test
    void loRegistradoPorElMedicoVaPrimero() {
        List<SugeridorDiagnosticos.Sugerencia> s = sugeridor.sugerir(List.of("Control de hipertensión; refiere tos ocasional"),
                catalogo, Set.of("I10"));
        assertThat(s.getFirst().codigo()).isEqualTo("I10");
        assertThat(s.getFirst().registradoPorMedico()).isTrue();
    }

    @Test
    void unaPalabraSueltaNoBastaParaUnDiagnosticoCompuesto() {
        List<Diagnostico> conAngina = List.of(new Diagnostico("I20.9", "Angina de pecho, no especificada"),
                new Diagnostico("R07.4", "Dolor en el pecho, no especificado"));
        assertThat(sugeridor.sugerir(List.of("Dolor en el pecho desde hace 2 horas"), conAngina, Set.of()))
                .extracting(SugeridorDiagnosticos.Sugerencia::codigo).containsExactly("R07.4");
    }

    @Test
    void sinTextoClinicoNoInventaDiagnosticos() {
        assertThat(sugeridor.sugerir(List.of("Control de rutina, paciente asintomático"), catalogo, Set.of())).isEmpty();
    }
}
