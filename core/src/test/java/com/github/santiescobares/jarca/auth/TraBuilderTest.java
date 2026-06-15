package com.github.santiescobares.jarca.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TraBuilderTest {

    @Test
    void build_containsServiceName() {
        String tra = TraBuilder.build("wsfe");
        assertTrue(tra.contains("<service>wsfe</service>"), "TRA must contain service element");
    }

    @Test
    void build_containsRequiredElements() {
        String tra = TraBuilder.build("wsfe");
        assertTrue(tra.contains("<uniqueId>"), "TRA must have uniqueId");
        assertTrue(tra.contains("<generationTime>"), "TRA must have generationTime");
        assertTrue(tra.contains("<expirationTime>"), "TRA must have expirationTime");
        assertTrue(tra.contains("loginTicketRequest"), "TRA must be loginTicketRequest");
    }

    @Test
    void build_expiryIsAfterGeneration() {
        String tra = TraBuilder.build("wsfe");
        // Extract the two timestamps by simple string parsing
        String gen = extractBetween(tra, "<generationTime>", "</generationTime>");
        String exp = extractBetween(tra, "<expirationTime>", "</expirationTime>");

        assertNotNull(gen, "generationTime must be present");
        assertNotNull(exp, "expirationTime must be present");

        java.time.OffsetDateTime genTime = java.time.OffsetDateTime.parse(gen);
        java.time.OffsetDateTime expTime = java.time.OffsetDateTime.parse(exp);

        assertTrue(expTime.isAfter(genTime), "expirationTime must be after generationTime");
        // Validity window is 12 h
        long hours = java.time.Duration.between(genTime, expTime).toHours();
        assertEquals(12, hours, "TRA validity must be exactly 12 hours");
    }

    @Test
    void build_uniqueIdIsPositiveLong() {
        String tra = TraBuilder.build("wsfe");
        String id = extractBetween(tra, "<uniqueId>", "</uniqueId>");
        assertNotNull(id);
        assertTrue(Long.parseLong(id) > 0, "uniqueId must be a positive epoch second");
    }

    private static String extractBetween(String s, String open, String close) {
        int start = s.indexOf(open);
        int end = s.indexOf(close);
        if (start < 0 || end < 0) return null;
        return s.substring(start + open.length(), end);
    }
}
