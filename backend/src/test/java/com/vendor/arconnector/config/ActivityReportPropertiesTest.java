package com.vendor.arconnector.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityReportPropertiesTest {

    private static ActivityReportProperties props(String baseUrl, ActivityReportProperties.ClientMode mode, String gps, String geos) {
        return new ActivityReportProperties(baseUrl, "", gps, geos, mode, null, null, null, false, null, null);
    }

    @Test
    void reconoceElEntornoPorLaUrlDelGateway() {
        assertThat(props("https://dev-bbva-gateway.appspot.com/timereport/api/vendors", null, "", "").environment()).isEqualTo("DEV");
        assertThat(props("https://au-bbva-gateway.appspot.com/timereport/api/vendors", null, "", "").environment()).isEqualTo("AU");
        assertThat(props("https://bbva-gateway.appspot.com/timereport/api/vendors", null, "", "").environment()).isEqualTo("PRO");
        assertThat(props("https://gapis.bbva.com/timereport/api/vendors", null, "", "").environment()).isEqualTo("PRO");
        assertThat(props("http://host.docker.internal:9911/timereport/api/vendors", null, "", "").environment()).isEqualTo("OTRO");
        assertThat(props("https://bbva-gateway.appspot.com/timereport/api/vendors", ActivityReportProperties.ClientMode.FILESYSTEM, "", "").environment()).isEqualTo("LOCAL");
    }

    @Test
    void losCodigosGpsYGeografiasVanSeparadosPorComas() {
        ActivityReportProperties p = props("", null, " 0000417143 , 0000999999,", "ES, MX ");
        assertThat(p.gpsCodeList()).containsExactly("0000417143", "0000999999");
        assertThat(p.geographyList()).containsExactly("ES", "MX");
        assertThat(props("", null, null, "").gpsCodeList()).isEmpty();
    }
}
