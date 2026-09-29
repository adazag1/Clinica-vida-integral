package co.edu.ucentral.clinica.pacientes;

import co.edu.ucentral.clinica.comun.modelo.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "paciente")
public class Paciente extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false)
    private TipoDocumento tipoDocumento;

    @Column(name = "numero_documento", nullable = false)
    private String numeroDocumento;

    @Column(nullable = false)
    private String nombres;

    @Column(nullable = false)
    private String apellidos;

    @Column(name = "nombre_normalizado", nullable = false)
    private String nombreNormalizado;

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @Enumerated(EnumType.STRING)
    private Sexo sexo;

    private String telefono;

    private String email;

    private String direccion;

    private String aseguradora;

    @Enumerated(EnumType.STRING)
    private Regimen regimen;

    protected Paciente() {
    }

    public Paciente(DatosDemograficos datos) {
        aplicar(datos);
    }

    /** Un cambio en un campo demográfico, para dejarlo en el historial (RFADM-011). */
    public record Cambio(String campo, String valorAnterior, String valorNuevo) {
    }

    /** Aplica los datos y devuelve solo los campos que realmente cambiaron. */
    public List<Cambio> aplicar(DatosDemograficos d) {
        List<Cambio> cambios = new ArrayList<>();
        comparar(cambios, "tipoDocumento", tipoDocumento, d.tipoDocumento());
        comparar(cambios, "numeroDocumento", numeroDocumento, d.numeroDocumento());
        comparar(cambios, "nombres", nombres, d.nombres());
        comparar(cambios, "apellidos", apellidos, d.apellidos());
        comparar(cambios, "fechaNacimiento", fechaNacimiento, d.fechaNacimiento());
        comparar(cambios, "sexo", sexo, d.sexo());
        comparar(cambios, "telefono", telefono, d.telefono());
        comparar(cambios, "email", email, d.email());
        comparar(cambios, "direccion", direccion, d.direccion());
        comparar(cambios, "aseguradora", aseguradora, d.aseguradora());
        comparar(cambios, "regimen", regimen, d.regimen());

        tipoDocumento = d.tipoDocumento();
        numeroDocumento = d.numeroDocumento();
        nombres = d.nombres();
        apellidos = d.apellidos();
        nombreNormalizado = Normalizador.texto(d.nombres() + " " + d.apellidos());
        fechaNacimiento = d.fechaNacimiento();
        sexo = d.sexo();
        telefono = d.telefono();
        email = d.email();
        direccion = d.direccion();
        aseguradora = d.aseguradora();
        regimen = d.regimen();
        return cambios;
    }

    private static void comparar(List<Cambio> cambios, String campo, Object anterior, Object nuevo) {
        if (!Objects.equals(anterior, nuevo)) {
            cambios.add(new Cambio(campo, anterior == null ? null : anterior.toString(),
                    nuevo == null ? null : nuevo.toString()));
        }
    }

    public boolean isIdentificacionTemporal() {
        return tipoDocumento == TipoDocumento.TEMPORAL;
    }

    public String getNombreCompleto() {
        return nombres + " " + apellidos;
    }

    public Integer getEdad() {
        return fechaNacimiento == null ? null : Period.between(fechaNacimiento, LocalDate.now()).getYears();
    }

    public Long getId() {
        return id;
    }

    public TipoDocumento getTipoDocumento() {
        return tipoDocumento;
    }

    public String getNumeroDocumento() {
        return numeroDocumento;
    }

    public String getNombres() {
        return nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    public String getNombreNormalizado() {
        return nombreNormalizado;
    }

    public LocalDate getFechaNacimiento() {
        return fechaNacimiento;
    }

    public Sexo getSexo() {
        return sexo;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getEmail() {
        return email;
    }

    public String getDireccion() {
        return direccion;
    }

    public String getAseguradora() {
        return aseguradora;
    }

    public Regimen getRegimen() {
        return regimen;
    }
}
