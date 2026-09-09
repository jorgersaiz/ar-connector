package com.vendor.arconnector.activities;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** "person_id: identificador anonimizado mediante SHA256 del código de usuario de la persona en mayúsculas". */
class PersonIdHasherTest {

    @Test
    void esSha256DelUidEnMayusculas() throws Exception {
        String expected = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest("XE12345".getBytes(StandardCharsets.UTF_8)));

        assertThat(PersonIdHasher.hash("xe12345")).isEqualTo(expected);
        assertThat(PersonIdHasher.hash("XE12345")).isEqualTo(expected);
        assertThat(PersonIdHasher.hash("  xe12345 ")).isEqualTo(expected);
    }

    @Test
    void produceHexDe64CaracteresEnMinusculas() {
        String hash = PersonIdHasher.hash("abc");
        assertThat(hash).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void uidsDistintosDanHashesDistintos() {
        assertThat(PersonIdHasher.hash("XE00001")).isNotEqualTo(PersonIdHasher.hash("XE00002"));
    }

    @Test
    void rechazaUidVacio() {
        assertThatThrownBy(() -> PersonIdHasher.hash(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PersonIdHasher.hash(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
