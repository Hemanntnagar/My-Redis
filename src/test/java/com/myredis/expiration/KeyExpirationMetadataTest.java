package com.myredis.expiration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyExpirationMetadataTest {

    @Test
    void remainingSecondsUsesRedisCeiling() {
        KeyExpirationMetadata metadata = new KeyExpirationMetadata(10_500L);
        assertEquals(2L, metadata.remainingSecondsAt(9_000L));
        assertEquals(1L, metadata.remainingSecondsAt(10_000L));
        assertEquals(0L, metadata.remainingSecondsAt(10_500L));
    }

    @Test
    void isExpiredAtDeadline() {
        KeyExpirationMetadata metadata = new KeyExpirationMetadata(1_000L);
        assertFalse(metadata.isExpiredAt(999L));
        assertTrue(metadata.isExpiredAt(1_000L));
    }
}
