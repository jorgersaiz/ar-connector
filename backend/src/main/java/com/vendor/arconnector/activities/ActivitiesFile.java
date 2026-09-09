package com.vendor.arconnector.activities;

import java.nio.charset.StandardCharsets;

/** Fichero de imputaciones ya generado (CSV con ';', UTF-8). */
public record ActivitiesFile(byte[] content, int dayCount, int rowCount) {

    public String asText() {
        return new String(content, StandardCharsets.UTF_8);
    }
}
