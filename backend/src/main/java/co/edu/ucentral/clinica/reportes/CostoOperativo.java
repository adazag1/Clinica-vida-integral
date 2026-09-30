package co.edu.ucentral.clinica.reportes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** US 5.2: costo operativo de un mes en una categoría. Registrarlo de nuevo reemplaza el valor. */
@Entity
@Table(name = "costo_operativo")
public class CostoOperativo {

    /** Las administrativas son las que la meta del CFO espera reducir un 25 %; la plataforma es el costo nuevo. */
    public enum Categoria {
        PERSONAL_ADMINISTRATIVO("Personal administrativo y de digitación", true),
        ARCHIVO_FISICO("Archivo físico y custodia de historias", true),
        TRANSCRIPCION("Transcripción de resultados e informes", true),
        ESTUDIOS_DUPLICADOS("Estudios repetidos o duplicados", true),
        PAPELERIA("Papelería e impresión", true),
        OTROS_ADMINISTRATIVOS("Otros gastos administrativos", true),
        PLATAFORMA("Plataforma: nube, licencias y soporte", false);

        private final String descripcion;
        private final boolean administrativa;

        Categoria(String descripcion, boolean administrativa) {
            this.descripcion = descripcion;
            this.administrativa = administrativa;
        }

        public String getDescripcion() {
            return descripcion;
        }

        public boolean isAdministrativa() {
            return administrativa;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate mes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Categoria categoria;

    @Column(nullable = false)
    private BigDecimal valor;

    private String nota;

    @Column(name = "registrado_por", nullable = false)
    private String registradoPor;

    @Column(name = "registrado_en", nullable = false)
    private Instant registradoEn;

    protected CostoOperativo() {
    }

    public CostoOperativo(LocalDate mes, Categoria categoria) {
        this.mes = mes.withDayOfMonth(1);
        this.categoria = categoria;
    }

    public void registrar(BigDecimal valor, String nota, String usuario) {
        this.valor = valor;
        this.nota = nota == null || nota.isBlank() ? null : nota.trim();
        this.registradoPor = usuario;
        this.registradoEn = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public LocalDate getMes() {
        return mes;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public String getNota() {
        return nota;
    }

    public String getRegistradoPor() {
        return registradoPor;
    }

    public Instant getRegistradoEn() {
        return registradoEn;
    }
}
