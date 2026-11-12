package com.myredis.storage;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class ByteArrayKeyTest {

    @Test
    void probeMatchesOwnedKeyWithoutCopyingLookupBytes() {
        byte[] lookup = "alpha".getBytes(StandardCharsets.UTF_8);
        ByteArrayKey stored = ByteArrayKey.owned("alpha".getBytes(StandardCharsets.UTF_8));
        ByteArrayKey probe = ByteArrayKey.probe(lookup);

        assertEquals(stored, probe);
        assertEquals(stored.hashCode(), probe.hashCode());
        assertNotSame(lookup, stored.bytes());
    }
}
