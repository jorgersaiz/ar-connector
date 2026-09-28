package com.vendor.arconnector.registration;

import com.vendor.arconnector.catalog.CatalogParseException;
import com.vendor.arconnector.catalog.CatalogRowException;
import com.vendor.arconnector.catalog.CsvRow;
import com.vendor.arconnector.catalog.CsvTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Feedback del registro de usuarios. El fichero real tiene dos columnas:
 *
 * <pre>
 *   uid;reason
 *   E7713342BE08...;APV_UR_01
 * </pre>
 *
 * donde "uid" es el SHA256 del uid en mayúsculas, escrito en hexadecimal en
 * mayúsculas, y "reason" viene vacío si la persona quedó registrada. El uid en
 * claro NO se devuelve (el documento sugería una columna "uids" que no existe;
 * se sigue aceptando por si apareciera).
 */
public final class UserRegistrationFeedbackParser {

    /**
     * @param uids uid en claro en mayúsculas, o "" si el fichero no lo trae
     * @param uid  hash del uid en mayúsculas, o "" si el fichero no lo trae
     */
    public record Row(int lineNumber, String uids, String uid, String reason) {
        public boolean isRejected() {
            return reason != null && !reason.isBlank();
        }
    }

    private UserRegistrationFeedbackParser() {
    }

    public static List<Row> parse(CsvTable table) {
        table.requireColumns("reason");
        if (!table.hasColumn("uid") && !table.hasColumn("uids")) {
            throw new CatalogParseException(List.of("faltan columnas en la cabecera: uid (cabecera recibida: "
                    + String.join(";", table.headers()) + ")"));
        }
        List<Row> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (CsvRow row : table.rows()) {
            try {
                String uids = row.text("uids");
                String uid = row.text("uid");
                if (uids.isEmpty() && uid.isEmpty()) {
                    throw new CatalogRowException(row.lineNumber(), "fila sin identificador de usuario");
                }
                rows.add(new Row(row.lineNumber(), uids.toUpperCase(Locale.ROOT), uid.toUpperCase(Locale.ROOT), row.optionalText("reason")));
            } catch (CatalogRowException e) {
                errors.add(e.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            throw new CatalogParseException(errors);
        }
        return rows;
    }
}
