package co.edu.ucentral.clinica.pacientes;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PacienteRepository extends JpaRepository<Paciente, Long>, JpaSpecificationExecutor<Paciente> {

    Optional<Paciente> findByTipoDocumentoAndNumeroDocumento(TipoDocumento tipo, String numero);

    /**
     * Candidatos a duplicado (RFADM-003): comparten documento, fecha de nacimiento, teléfono
     * o el primer apellido. La puntuación final se calcula en {@link DetectorDuplicados}.
     */
    @Query("""
            select p from Paciente p
            where p.numeroDocumento = :documento
               or p.fechaNacimiento = :fechaNacimiento
               or (:telefono <> '' and p.telefono = :telefono)
               or (:apellido <> '' and p.nombreNormalizado like concat('%', :apellido, '%'))
            """)
    List<Paciente> buscarCandidatosDuplicado(String documento, LocalDate fechaNacimiento, String telefono,
                                              String apellido, Pageable limite);

    @Query(value = "select nextval('seq_identificador_temporal')", nativeQuery = true)
    long siguienteIdentificadorTemporal();
}
