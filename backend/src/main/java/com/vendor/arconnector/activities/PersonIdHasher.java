package com.vendor.arconnector.activities;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Anonimización del identificador de persona exigida por el documento:
 * "identificador anonimizado mediante SHA256 del código de usuario de la
 * persona en mayúsculas".
 *
 * Se asume representación hexadecimal en minúsculas del hash. El catálogo
 * "hidden_task_types" trae person_id ya hasheados por Activity Report, así que
 * en cuanto se sincronice se puede verificar que el formato coincide.
 * TODO: verificar hex minúsculas vs mayúsculas contra ese catálogo.
 */
public final class PersonIdHasher {

    private PersonIdHasher() {
    }

    public static String hash(String uid) {
        if (uid == null || uid.isBlank()) {
            throw new IllegalArgumentException("uid vacío");
        }
        String normalized = uid.trim().toUpperCase(Locale.ROOT);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }
}
