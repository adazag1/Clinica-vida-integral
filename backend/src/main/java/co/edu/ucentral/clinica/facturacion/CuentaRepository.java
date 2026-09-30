package co.edu.ucentral.clinica.facturacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CuentaRepository extends JpaRepository<Cuenta, Long> {

    List<Cuenta> findByEstadoInOrderByAbiertaEnDesc(Collection<EstadoCuenta> estados);

    List<Cuenta> findByPacienteIdOrderByAbiertaEnDesc(Long pacienteId);

    /** Cuenta abierta de la atención (o la ambulatoria del paciente si no hay atención), bloqueada para agregar cargos. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from Cuenta c where c.pacienteId = :pacienteId and c.estado in :estados
            and ((:episodioId is null and c.episodioId is null) or c.episodioId = :episodioId)
            order by c.abiertaEn desc
            """)
    List<Cuenta> abiertasParaCargos(Long pacienteId, Long episodioId, Collection<EstadoCuenta> estados);

    @Query(value = "select nextval('seq_numero_cuenta')", nativeQuery = true)
    long siguienteNumero();

    Optional<Cuenta> findByNumero(String numero);
}
