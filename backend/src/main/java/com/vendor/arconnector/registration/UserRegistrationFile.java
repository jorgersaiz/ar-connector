package com.vendor.arconnector.registration;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Fichero "REGISTRO DE USUARIOS" (Vendor -> Activity Report): una sola
 * columna "uids" con el identificador SIN anonimizar, sin duplicados.
 */
public final class UserRegistrationFile {

    public static final String HEADER = "uids";

    private UserRegistrationFile() {
    }

    /** Devuelve el contenido y el nº de uids incluidos (ya sin duplicados ni vacíos). */
    public record Built(byte[] content, int count) {
    }

    public static Built build(Collection<String> uids) {
        Set<String> unique = new LinkedHashSet<>();
        for (String uid : uids) {
            if (uid != null && !uid.isBlank()) {
                unique.add(uid.trim().toUpperCase(Locale.ROOT));
            }
        }
        StringBuilder sb = new StringBuilder(HEADER).append('\n');
        unique.forEach(u -> sb.append(u).append('\n'));
        return new Built(sb.toString().getBytes(StandardCharsets.UTF_8), unique.size());
    }
}
