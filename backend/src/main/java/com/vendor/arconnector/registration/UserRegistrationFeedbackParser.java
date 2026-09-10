package com.vendor.arconnector.registration;

import com.vendor.arconnector.catalog.CatalogParseException;
import com.vendor.arconnector.catalog.CatalogRowException;
import com.vendor.arconnector.catalog.CsvRow;
import com.vendor.arconnector.catalog.CsvTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Feedback del registro de usuarios: "el mismo formato que el fichero de
 * registro de usuarios" (uids) más "uid" (anonimizado) y "reason"
 * (APV_UR_xx en los rechazos).
 */
public final class UserRegistrationFeedbackParser {

    public record Row(int lineNumber, String uids, String uid, String reason) {
        public boolean isRejected() {
            return reason != null && !reason.isBlank();
        }
    }

    private UserRegistrationFeedbackParser() {
    }

    public static List<Row> parse(CsvTable table) {
        table.requireColumns("uids", "reason");
        List<Row> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (CsvRow row : table.rows()) {
            try {
                String uids = row.text("uids");
                String uid = row.text("uid");
                if (uids.isEmpty() && uid.isEmpty()) {
                    throw new CatalogRowException(row.lineNumber(), "fila sin uids ni uid");
                }
                rows.add(new Row(row.lineNumber(), uids.toUpperCase(Locale.ROOT), uid.toLowerCase(Locale.ROOT), row.optionalText("reason")));
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
