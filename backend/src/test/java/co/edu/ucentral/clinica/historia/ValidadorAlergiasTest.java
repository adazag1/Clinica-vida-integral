package co.edu.ucentral.clinica.historia;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ValidadorAlergiasTest {

    private final ValidadorAlergias validador = new ValidadorAlergias();

    private static Medicamento medicamento(String nombre, String principio, String familia, String sinonimos) {
        Medicamento m = org.springframework.beans.BeanUtils.instantiateClass(Medicamento.class);
        ReflectionTestUtils.setField(m, "nombre", nombre);
        ReflectionTestUtils.setField(m, "principioActivo", principio);
        ReflectionTestUtils.setField(m, "familia", familia);
        ReflectionTestUtils.setField(m, "sinonimos", sinonimos);
        return m;
    }

    private static Antecedente alergia(String sustancia) {
        return new Antecedente(1L, Antecedente.Tipo.ALERGIA, sustancia, null, Antecedente.Severidad.SEVERA);
    }

    private final Medicamento amoxicilina = medicamento("Amoxicilina 500 mg", "amoxicilina", "penicilinas", "betalactamicos");
    private final Medicamento asa = medicamento("Ácido acetilsalicílico 100 mg", "acido acetilsalicilico", "aine",
            "aspirina, asa, salicilatos");
    private final Medicamento bactrim = medicamento("Trimetoprim/sulfametoxazol 160/800 mg", "sulfametoxazol trimetoprim",
            "sulfonamidas", "sulfas, bactrim");
    private final Medicamento azitromicina = medicamento("Azitromicina 500 mg", "azitromicina", "macrolidos", null);

    @Test
    void laAlergiaALaFamiliaBloqueaCadaMedicamentoDeElla() {
        assertThat(validador.conflicto(List.of(alergia("Penicilina")), amoxicilina)).isPresent();
        assertThat(validador.conflicto(List.of(alergia("PENICILINAS")), amoxicilina)).isPresent();
        assertThat(validador.conflicto(List.of(alergia("Betalactámicos")), amoxicilina)).isPresent();
    }

    @Test
    void reconoceNombresComercialesYSinonimos() {
        assertThat(validador.conflicto(List.of(alergia("Aspirina")), asa)).isPresent();
        assertThat(validador.conflicto(List.of(alergia("AINEs")), asa)).isPresent();
        assertThat(validador.conflicto(List.of(alergia("Sulfas")), bactrim)).isPresent();
        assertThat(validador.conflicto(List.of(alergia("ASA")), asa)).isPresent();
    }

    @Test
    void noBloqueaMedicamentosSinRelacion() {
        assertThat(validador.conflicto(List.of(alergia("Penicilina"), alergia("Sulfas")), azitromicina)).isEmpty();
        assertThat(validador.conflicto(List.of(alergia("Mariscos")), amoxicilina)).isEmpty();
    }

    @Test
    void unaAlergiaInactivaNoBloquea() {
        Antecedente descartada = alergia("Penicilina");
        descartada.inactivar("Prueba de provocación negativa");
        assertThat(validador.conflicto(List.of(descartada), amoxicilina)).isEmpty();
    }
}
