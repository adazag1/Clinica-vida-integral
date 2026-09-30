package co.edu.ucentral.clinica.facturacion;

import co.edu.ucentral.clinica.pacientes.Paciente;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * RFRC-009: arma la factura en XML UBL 2.1 y calcula el CUFE con SHA-384 sobre los campos que
 * define el anexo técnico de la DIAN. Los servicios de salud están excluidos de IVA, así que los
 * impuestos van en cero.
 *
 * <p>Es una representación fiel en estructura pero simplificada: la factura electrónica del sector
 * salud exige además la firma digital con el certificado de la clínica, el resolución de numeración
 * vigente y el anexo de RIPS en JSON (Res. 2275 de 2023). Eso lo completa el proveedor tecnológico.</p>
 */
@Component
public class GeneradorFacturaElectronica {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ssXXX");

    private final String nit;
    private final String razonSocial;
    private final String claveTecnica;
    private final String ambiente;
    private final ZoneId zona;

    public GeneradorFacturaElectronica(@Value("${app.facturacion.nit}") String nit,
                                       @Value("${app.facturacion.razon-social}") String razonSocial,
                                       @Value("${app.facturacion.clave-tecnica}") String claveTecnica,
                                       @Value("${app.facturacion.ambiente}") String ambiente,
                                       @Value("${app.zona-horaria}") String zona) {
        this.nit = nit;
        this.razonSocial = razonSocial;
        this.claveTecnica = claveTecnica;
        this.ambiente = ambiente;
        this.zona = ZoneId.of(zona);
    }

    public record Documento(String cufe, String xml) {
    }

    public Documento generar(Factura f, Paciente paciente) {
        ZonedDateTime emision = f.getFechaEmision().atZone(zona);
        String fecha = FECHA.format(emision);
        String hora = HORA.format(emision);
        String total = dos(f.getTotal());
        String adquirente = f.getAseguradora().replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        // CUFE = SHA-384(NumFac + FecFac + HorFac + ValFac + 01 + ValImp1 + 04 + ValImp2 + 03 + ValImp3 + ValTot + NitOFE + NumAdq + ClTec + TipoAmbiente)
        String semilla = f.getNumero() + fecha + hora + dos(f.getSubtotal()) + "01" + "0.00" + "04" + "0.00" + "03" + "0.00"
                + total + nit + adquirente + claveTecnica + ambiente;
        String cufe = sha384(semilla);

        StringBuilder x = new StringBuilder();
        x.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        x.append("<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\"")
                .append(" xmlns:cac=\"urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2\"")
                .append(" xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\">\n");
        x.append("  <cbc:UBLVersionID>UBL 2.1</cbc:UBLVersionID>\n");
        x.append("  <cbc:CustomizationID>SS-CUFE</cbc:CustomizationID>\n");
        x.append("  <cbc:ProfileExecutionID>").append(ambiente).append("</cbc:ProfileExecutionID>\n");
        x.append("  <cbc:ID>").append(esc(f.getNumero())).append("</cbc:ID>\n");
        x.append("  <cbc:UUID schemeName=\"CUFE-SHA384\">").append(cufe).append("</cbc:UUID>\n");
        x.append("  <cbc:IssueDate>").append(fecha).append("</cbc:IssueDate>\n");
        x.append("  <cbc:IssueTime>").append(hora).append("</cbc:IssueTime>\n");
        x.append("  <cbc:DueDate>").append(f.getFechaVencimiento()).append("</cbc:DueDate>\n");
        x.append("  <cbc:InvoiceTypeCode>01</cbc:InvoiceTypeCode>\n");
        x.append("  <cbc:Note>Documento generado en modo simulado: no transmitido a la DIAN.</cbc:Note>\n");
        x.append("  <cbc:DocumentCurrencyCode>COP</cbc:DocumentCurrencyCode>\n");
        x.append("  <cac:AccountingSupplierParty><cac:Party><cac:PartyTaxScheme><cbc:RegistrationName>")
                .append(esc(razonSocial)).append("</cbc:RegistrationName><cbc:CompanyID schemeName=\"31\">").append(nit)
                .append("</cbc:CompanyID></cac:PartyTaxScheme></cac:Party></cac:AccountingSupplierParty>\n");
        x.append("  <cac:AccountingCustomerParty><cac:Party><cac:PartyTaxScheme><cbc:RegistrationName>")
                .append(esc(f.getAseguradora())).append("</cbc:RegistrationName></cac:PartyTaxScheme></cac:Party></cac:AccountingCustomerParty>\n");
        x.append("  <cac:PaymentMeans><cbc:PaymentMeansCode>ZZZ</cbc:PaymentMeansCode></cac:PaymentMeans>\n");
        x.append("  <cac:AdditionalDocumentReference><cbc:ID>").append(esc(f.getCuenta().getNumero()))
                .append("</cbc:ID><cbc:DocumentType>Cuenta de atención · paciente ").append(esc(paciente.getTipoDocumento().name()))
                .append(" ").append(esc(paciente.getNumeroDocumento())).append("</cbc:DocumentType></cac:AdditionalDocumentReference>\n");
        x.append("  <cac:TaxTotal><cbc:TaxAmount currencyID=\"COP\">0.00</cbc:TaxAmount></cac:TaxTotal>\n");
        x.append("  <cac:LegalMonetaryTotal>\n");
        x.append("    <cbc:LineExtensionAmount currencyID=\"COP\">").append(dos(f.getSubtotal())).append("</cbc:LineExtensionAmount>\n");
        x.append("    <cbc:AllowanceTotalAmount currencyID=\"COP\">").append(dos(f.getAjustes().negate())).append("</cbc:AllowanceTotalAmount>\n");
        x.append("    <cbc:PayableAmount currencyID=\"COP\">").append(total).append("</cbc:PayableAmount>\n");
        x.append("  </cac:LegalMonetaryTotal>\n");
        for (FacturaLinea l : f.getLineas()) {
            x.append("  <cac:InvoiceLine><cbc:ID>").append(l.getOrden()).append("</cbc:ID>")
                    .append("<cbc:InvoicedQuantity unitCode=\"94\">").append(l.getCantidad()).append("</cbc:InvoicedQuantity>")
                    .append("<cbc:LineExtensionAmount currencyID=\"COP\">").append(dos(l.getValorTotal())).append("</cbc:LineExtensionAmount>")
                    .append("<cac:Item><cbc:Description>").append(esc(l.getDescripcion())).append("</cbc:Description>")
                    .append("<cac:StandardItemIdentification><cbc:ID schemeName=\"CUPS\">").append(esc(l.getCodigo()))
                    .append("</cbc:ID></cac:StandardItemIdentification></cac:Item>")
                    .append("<cac:Price><cbc:PriceAmount currencyID=\"COP\">").append(dos(l.getValorUnitario()))
                    .append("</cbc:PriceAmount></cac:Price></cac:InvoiceLine>\n");
        }
        x.append("</Invoice>\n");
        return new Documento(cufe, x.toString());
    }

    private static String dos(BigDecimal v) {
        return v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static String sha384(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-384").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-384 no disponible", e);
        }
    }
}
