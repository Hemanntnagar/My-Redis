package com.myredis.command;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AsciiLongCodecTest {

    @Test
    void roundTripsTypicalCounters() {
        for (long value : new long[] {0, 1, 42, -7, 9_223_372_036_854_775L, Long.MIN_VALUE, Long.MAX_VALUE}) {
            byte[] encoded = AsciiLongCodec.encode(value);
            assertEquals(value, AsciiLongCodec.parseStrict(encoded));
        }
    }

    @Test
    void matchesUtf8Digits() {
        byte[] encoded = AsciiLongCodec.encode(12345L);
        assertArrayEquals("12345".getBytes(StandardCharsets.US_ASCII), encoded);
    }
}
