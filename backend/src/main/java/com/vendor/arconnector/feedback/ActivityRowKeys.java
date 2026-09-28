package com.vendor.arconnector.feedback;

import com.vendor.arconnector.catalog.CsvRow;
import com.vendor.arconnector.catalog.CsvTable;
import com.vendor.arconnector.catalog.SemicolonCsv;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Clave de cada fila de un fichero de imputaciones o de su feedback, para
 * comprobar que un feedback corresponde a un envío: fecha, persona (sin
 * distinguir mayúsculas en el hash), nodo hoja, proyectos y tiempo.
 */
public final class ActivityRowKeys {

    private ActivityRowKeys() {
    }

    public static Set<String> of(byte[] csv) {
        CsvTable table = SemicolonCsv.parse(csv);
        Set<String> keys = new HashSet<>(table.rows().size() * 2);
        for (CsvRow r : table.rows()) {
            keys.add(String.join("|",
                    r.text("date"),
                    r.text("person_id").toUpperCase(Locale.ROOT),
                    r.text("task_level_5_code"),
                    r.text("sda_project_id"),
                    r.text("jira_issue_id"),
                    r.text("non_sda_project"),
                    r.text("time")));
        }
        return keys;
    }
}
