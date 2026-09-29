package co.edu.ucentral.clinica.comun.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Base para todas las entidades: registra automáticamente quién y cuándo creó/modificó
 * cada registro (RFMT-008 Trazabilidad de Modificaciones).
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class EntidadAuditable {

    @CreatedDate
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @CreatedBy
    @Column(name = "creado_por", updatable = false)
    private String creadoPor;

    @LastModifiedDate
    @Column(name = "modificado_en", nullable = false)
    private Instant modificadoEn;

    @LastModifiedBy
    @Column(name = "modificado_por")
    private String modificadoPor;

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public String getCreadoPor() {
        return creadoPor;
    }

    public Instant getModificadoEn() {
        return modificadoEn;
    }

    public String getModificadoPor() {
        return modificadoPor;
    }
}
