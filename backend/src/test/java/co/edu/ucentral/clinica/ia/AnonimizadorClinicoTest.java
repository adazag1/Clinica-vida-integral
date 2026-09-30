package co.edu.ucentral.clinica.ia;

import co.edu.ucentral.clinica.pacientes.Paciente;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class AnonimizadorClinicoTest {

    @Test
    void quitaNombreDocumentoTelefonoCorreoFechaYDireccion() {
        Paciente p = Mockito.mock(Paciente.class);
        Mockito.when(p.getNombres()).thenReturn("Carolina");
        Mockito.when(p.getApellidos()).thenReturn("Muñoz Bernal");
        String texto = "Paciente CAROLINA munoz, CC 1.020.345.678, tel 300 123 4567, correo caro@mail.com. "
                + "Vive en Calle 45 # 12-30. Ingresó el 12/09/2026 con dolor torácico de 2 horas y PA 150/90.";

        String limpio = AnonimizadorClinico.limpiar(texto, p);

        assertThat(limpio).doesNotContainIgnoringCase("carolina").doesNotContainIgnoringCase("muñoz").doesNotContainIgnoringCase("munoz")
                .doesNotContain("1.020.345.678").doesNotContain("300 123 4567").doesNotContain("caro@mail.com")
                .doesNotContain("12/09/2026").doesNotContain("Calle 45");
        // Los datos clínicos cortos se conservan
        assertThat(limpio).contains("dolor torácico de 2 horas").contains("PA 150/90");
    }

    @Test
    void agrupaLaEdadEnQuinquenios() {
        assertThat(AnonimizadorClinico.grupoEdad(43)).isEqualTo("40-44");
        assertThat(AnonimizadorClinico.grupoEdad(0)).isEqualTo("0-4");
        assertThat(AnonimizadorClinico.grupoEdad(95)).isEqualTo("90+");
        assertThat(AnonimizadorClinico.grupoEdad(null)).isEqualTo("desconocida");
    }
}
