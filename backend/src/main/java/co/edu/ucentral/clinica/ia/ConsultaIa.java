package co.edu.ucentral.clinica.ia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** Registro de cada uso del asistente de lenguaje: quién, para qué paciente, modelo, tokens y costo. */
@Entity
@Table(name = "consulta_ia")
public class ConsultaIa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    @Column(nullable = false)
    private String tipo;

    @Column(nullable = false)
    private String usuario;

    @Column(nullable = false)
    private Instant fecha;

    private String modelo;

    @Column(name = "tokens_entrada")
    private Integer tokensEntrada;

    @Column(name = "tokens_salida")
    private Integer tokensSalida;

    @Column(name = "costo_usd")
    private BigDecimal costoUsd;

    @Column(nullable = false)
    private boolean exito;

    private String error;

    protected ConsultaIa() {
    }

    public ConsultaIa(Long pacienteId, String tipo, String usuario, String modelo, Integer tokensEntrada,
                      Integer tokensSalida, BigDecimal costoUsd, boolean exito, String error) {
        this.pacienteId = pacienteId;
        this.tipo = tipo;
        this.usuario = usuario;
        this.fecha = Instant.now();
        this.modelo = modelo;
        this.tokensEntrada = tokensEntrada;
        this.tokensSalida = tokensSalida;
        this.costoUsd = costoUsd;
        this.exito = exito;
        this.error = error == null || error.length() <= 300 ? error : error.substring(0, 300);
    }

    public Long getId() {
        return id;
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public String getTipo() {
        return tipo;
    }

    public String getUsuario() {
        return usuario;
    }

    public Instant getFecha() {
        return fecha;
    }

    public String getModelo() {
        return modelo;
    }

    public Integer getTokensEntrada() {
        return tokensEntrada;
    }

    public Integer getTokensSalida() {
        return tokensSalida;
    }

    public BigDecimal getCostoUsd() {
        return costoUsd;
    }

    public boolean isExito() {
        return exito;
    }

    public String getError() {
        return error;
    }
}
