package co.edu.ucentral.clinica.facturacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** RFRC-010: registro de que ya se alertó una factura al cruzar un umbral de antigüedad. */
@Entity
@Table(name = "alerta_cartera")
@IdClass(AlertaCartera.Clave.class)
public class AlertaCartera {

    public static class Clave implements Serializable {
        private Long facturaId;
        private Integer umbralDias;

        public Clave() {
        }

        public Clave(Long facturaId, Integer umbralDias) {
            this.facturaId = facturaId;
            this.umbralDias = umbralDias;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Clave c && Objects.equals(facturaId, c.facturaId) && Objects.equals(umbralDias, c.umbralDias);
        }

        @Override
        public int hashCode() {
            return Objects.hash(facturaId, umbralDias);
        }
    }

    @Id
    @Column(name = "factura_id")
    private Long facturaId;

    @Id
    @Column(name = "umbral_dias")
    private Integer umbralDias;

    @Column(name = "alertada_en", nullable = false)
    private Instant alertadaEn;

    protected AlertaCartera() {
    }

    AlertaCartera(Long facturaId, int umbralDias) {
        this.facturaId = facturaId;
        this.umbralDias = umbralDias;
        this.alertadaEn = Instant.now();
    }
}
