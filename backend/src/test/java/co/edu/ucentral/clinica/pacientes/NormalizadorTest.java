package co.edu.ucentral.clinica.pacientes;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizadorTest {

    @Test
    void quitaTildesMayusculasYEspacios() {
        assertThat(Normalizador.texto("  José   PÉREZ  Muñoz ")).isEqualTo("jose perez munoz");
    }

    @Test
    void documentoYTelefonoSoloConCaracteresUtiles() {
        assertThat(Normalizador.documento("1.020-345 678")).isEqualTo("1020345678");
        assertThat(Normalizador.documento("ab-12345")).isEqualTo("AB12345");
        assertThat(Normalizador.telefono("(+57) 300 123-4567")).isEqualTo("573001234567");
    }

    @Test
    void nombreConErrorDeDigitacionEsMuyParecido() {
        double s = Normalizador.similitud(Normalizador.texto("Jhon Peres Gomez"), Normalizador.texto("John Pérez Gómez"));
        assertThat(s).isGreaterThanOrEqualTo(0.85);
    }

    @Test
    void elOrdenDeLasPalabrasNoImporta() {
        assertThat(Normalizador.similitud("perez gomez john", "john perez gomez")).isEqualTo(1.0);
    }

    @Test
    void nombresDistintosNoSeParecen() {
        assertThat(Normalizador.similitud("maria lopez", "carlos ruiz")).isLessThan(0.7);
    }
}
