package co.edu.ucentral.clinica.pacientes;

import co.edu.ucentral.clinica.comun.excepciones.DatosInvalidosException;
import co.edu.ucentral.clinica.comun.excepciones.RecursoNoEncontradoException;
import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
import co.edu.ucentral.clinica.pacientes.dto.CambioDemograficoDto;
import co.edu.ucentral.clinica.pacientes.dto.PacienteDto;
import co.edu.ucentral.clinica.pacientes.dto.PacienteRequest;
import co.edu.ucentral.clinica.pacientes.dto.PacienteResumenDto;
import co.edu.ucentral.clinica.pacientes.dto.PosibleDuplicadoDto;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PacienteService {

    private final PacienteRepository pacientes;
    private final CambioDemograficoRepository cambios;
    private final DetectorDuplicados detectorDuplicados;
    private final AuditorAware<String> auditor;

    public PacienteService(PacienteRepository pacientes, CambioDemograficoRepository cambios,
                           DetectorDuplicados detectorDuplicados, AuditorAware<String> auditor) {
        this.pacientes = pacientes;
        this.cambios = cambios;
        this.detectorDuplicados = detectorDuplicados;
        this.auditor = auditor;
    }

    /**
     * RFADM-002 Búsqueda por varios criterios. Sin tildes ni mayúsculas, y cada palabra del
     * nombre puede ir en cualquier orden ("perez juan" encuentra a "Juan Pérez").
     *
     * @param texto búsqueda libre (buscador superior): si son dígitos busca por documento o teléfono
     */
    @Transactional(readOnly = true)
    public Page<PacienteResumenDto> buscar(String texto, String documento, String nombre, LocalDate fechaNacimiento,
                                           String telefono, int pagina, int tamano) {
        Specification<Paciente> spec = (root, query, cb) -> {
            List<Predicate> y = new ArrayList<>();
            if (texto != null && !texto.isBlank()) {
                String digitos = Normalizador.telefono(texto);
                if (!digitos.isEmpty() && digitos.length() == texto.replaceAll("[\\s.\\-]", "").length()) {
                    y.add(cb.or(cb.like(root.get("numeroDocumento"), digitos + "%"),
                            cb.like(root.get("telefono"), "%" + digitos + "%")));
                } else {
                    y.add(cb.or(cb.like(root.get("numeroDocumento"), Normalizador.documento(texto) + "%"),
                            palabras(cb, root.get("nombreNormalizado"), texto)));
                }
            }
            if (documento != null && !documento.isBlank()) {
                y.add(cb.like(root.get("numeroDocumento"), Normalizador.documento(documento) + "%"));
            }
            if (nombre != null && !nombre.isBlank()) {
                y.add(palabras(cb, root.get("nombreNormalizado"), nombre));
            }
            if (fechaNacimiento != null) {
                y.add(cb.equal(root.get("fechaNacimiento"), fechaNacimiento));
            }
            if (telefono != null && !Normalizador.telefono(telefono).isEmpty()) {
                y.add(cb.like(root.get("telefono"), "%" + Normalizador.telefono(telefono) + "%"));
            }
            return cb.and(y.toArray(Predicate[]::new));
        };
        PageRequest page = PageRequest.of(pagina, Math.min(tamano, 100), Sort.by("apellidos", "nombres"));
        return pacientes.findAll(spec, page).map(PacienteResumenDto::de);
    }

    /** Todas las palabras deben aparecer en el nombre, en cualquier orden. */
    private static Predicate palabras(CriteriaBuilder cb, Path<String> campo, String texto) {
        String[] tokens = Normalizador.texto(texto).split(" ");
        return cb.and(Arrays.stream(tokens)
                .filter(t -> !t.isEmpty())
                .map(t -> cb.like(campo, "%" + t + "%"))
                .toArray(Predicate[]::new));
    }

    @Transactional(readOnly = true)
    public PacienteDto obtener(Long id) {
        return PacienteDto.de(buscarEntidad(id));
    }

    @Transactional(readOnly = true)
    public Paciente buscarEntidad(Long id) {
        return pacientes.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Paciente", id));
    }

    /** RFADM-003: se consulta mientras admisiones digita, antes de guardar. */
    @Transactional(readOnly = true)
    public List<PosibleDuplicadoDto> posiblesDuplicados(PacienteRequest request, Long excluirId) {
        if (request.tipoDocumento() == null || request.nombres() == null || request.apellidos() == null) {
            return List.of();
        }
        return detectorDuplicados.buscar(limpiar(request, null), excluirId);
    }

    @Transactional
    public PacienteDto crear(PacienteRequest request) {
        DatosDemograficos datos = validar(request, null);

        if (datos.tipoDocumento() == TipoDocumento.TEMPORAL) {
            datos = conNumero(datos, "TMP-%06d".formatted(pacientes.siguienteIdentificadorTemporal()));
        } else {
            DatosDemograficos d = datos;
            pacientes.findByTipoDocumentoAndNumeroDocumento(d.tipoDocumento(), d.numeroDocumento())
                    .ifPresent(p -> {
                        throw new ReglaNegocioException("PACIENTE_EXISTENTE",
                                "Ya existe un paciente con " + d.tipoDocumento().getDescripcion() + " "
                                        + d.numeroDocumento() + ": " + p.getNombreCompleto() + ".");
                    });
        }
        if (!request.confirmarNoDuplicado() && !detectorDuplicados.buscar(datos, null).isEmpty()) {
            throw new ReglaNegocioException("POSIBLE_DUPLICADO",
                    "Existen pacientes parecidos. Revíselos y confirme que se trata de otra persona.");
        }
        return PacienteDto.de(pacientes.save(new Paciente(datos)));
    }

    /**
     * Actualiza los datos y deja en el historial cada campo modificado con usuario, fecha y motivo
     * (RFADM-011). También sirve para reemplazar el identificador temporal por el documento real (RFADM-010).
     */
    @Transactional
    public PacienteDto actualizar(Long id, PacienteRequest request) {
        if (request.motivo() == null || request.motivo().isBlank()) {
            throw new DatosInvalidosException(Map.of("motivo", "Indique el motivo del cambio"));
        }
        Paciente paciente = buscarEntidad(id);
        if (!paciente.isIdentificacionTemporal() && request.tipoDocumento() == TipoDocumento.TEMPORAL) {
            throw new ReglaNegocioException("DOCUMENTO_NO_REVERSIBLE",
                    "Un paciente con documento no puede volver a un identificador temporal.");
        }
        DatosDemograficos datos = validar(request, paciente);

        pacientes.findByTipoDocumentoAndNumeroDocumento(datos.tipoDocumento(), datos.numeroDocumento())
                .filter(otro -> !otro.getId().equals(id))
                .ifPresent(otro -> {
                    throw new ReglaNegocioException("DOCUMENTO_EN_USO",
                            "El documento ya pertenece a otro paciente: " + otro.getNombreCompleto()
                                    + ". Si es la misma persona, solicite la unificación de historias.");
                });

        String usuario = auditor.getCurrentAuditor().orElse("sistema");
        String motivo = request.motivo().trim();
        paciente.aplicar(datos).forEach(c -> cambios.save(new CambioDemografico(id, c, motivo, usuario)));
        return PacienteDto.de(paciente);
    }

    @Transactional(readOnly = true)
    public List<CambioDemograficoDto> historialCambios(Long id) {
        buscarEntidad(id);
        return cambios.findByPacienteIdOrderByFechaDescIdDesc(id).stream().map(CambioDemograficoDto::de).toList();
    }

    /** Reglas que dependen de varios campos. Sin documento (urgencias) solo se exige el nombre. */
    private DatosDemograficos validar(PacienteRequest r, Paciente existente) {
        DatosDemograficos d = limpiar(r, existente);
        Map<String, String> errores = new LinkedHashMap<>();

        if (d.tipoDocumento() != TipoDocumento.TEMPORAL) {
            String numero = d.numeroDocumento();
            if (numero.isEmpty()) {
                errores.put("numeroDocumento", "Ingrese el número de documento");
            } else if (d.tipoDocumento().isSoloNumeros() && !numero.matches("\\d{3,15}")) {
                errores.put("numeroDocumento", "Debe tener solo dígitos (entre 3 y 15)");
            } else if (!numero.matches("[A-Z0-9]{3,20}")) {
                errores.put("numeroDocumento", "Debe tener entre 3 y 20 letras o dígitos");
            }
            if (d.fechaNacimiento() == null) {
                errores.put("fechaNacimiento", "Ingrese la fecha de nacimiento");
            } else if (d.fechaNacimiento().isBefore(LocalDate.now().minusYears(130))) {
                errores.put("fechaNacimiento", "Fecha de nacimiento no válida");
            }
            if (d.sexo() == null) {
                errores.put("sexo", "Seleccione el sexo");
            }
            if (d.regimen() == null) {
                errores.put("regimen", "Seleccione el régimen de afiliación");
            } else if (d.regimen() != Regimen.PARTICULAR && d.aseguradora() == null) {
                errores.put("aseguradora", "Ingrese la aseguradora (EPS)");
            }
        }
        if (!errores.isEmpty()) {
            throw new DatosInvalidosException(errores);
        }
        return d;
    }

    /**
     * Quita espacios sobrantes y deja el documento y el teléfono solo con sus caracteres útiles.
     * Un paciente temporal nuevo queda sin número: se le asigna en crear().
     */
    private DatosDemograficos limpiar(PacienteRequest r, Paciente existente) {
        String numero;
        if (r.tipoDocumento() == TipoDocumento.TEMPORAL) {
            numero = existente != null && existente.isIdentificacionTemporal() ? existente.getNumeroDocumento() : null;
        } else {
            numero = Normalizador.documento(r.numeroDocumento());
        }
        String telefono = Normalizador.telefono(r.telefono());
        return new DatosDemograficos(r.tipoDocumento(), numero, recortar(r.nombres()), recortar(r.apellidos()),
                r.fechaNacimiento(), r.sexo(), telefono.isEmpty() ? null : telefono, vacioANulo(r.email()),
                vacioANulo(r.direccion()), vacioANulo(r.aseguradora()), r.regimen());
    }

    private static DatosDemograficos conNumero(DatosDemograficos d, String numero) {
        return new DatosDemograficos(d.tipoDocumento(), numero, d.nombres(), d.apellidos(), d.fechaNacimiento(),
                d.sexo(), d.telefono(), d.email(), d.direccion(), d.aseguradora(), d.regimen());
    }

    private static String recortar(String s) {
        return s == null ? null : s.trim().replaceAll("\\s+", " ");
    }

    private static String vacioANulo(String s) {
        return s == null || s.isBlank() ? null : recortar(s);
    }
}
