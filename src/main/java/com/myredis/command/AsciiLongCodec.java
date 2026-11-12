package com.myredis.command;

import java.nio.charset.StandardCharsets;

/** Encode and decode signed decimal longs as US-ASCII bytes without intermediate strings. */
final class AsciiLongCodec {

    private AsciiLongCodec() {
    }

    static long parseStrict(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new NumberFormatException("empty");
        }
        int index = 0;
        boolean negative = false;
        if (bytes[0] == '+') {
            if (bytes.length == 1) {
                throw new NumberFormatException("sign only");
            }
            index = 1;
        } else if (bytes[0] == '-') {
            if (bytes.length == 1) {
                throw new NumberFormatException("sign only");
            }
            negative = true;
            index = 1;
        }
        long limit = negative ? Long.MIN_VALUE : -Long.MAX_VALUE;
        long multmin = limit / 10;
        long result = 0;
        for (int i = index; i < bytes.length; i++) {
            byte digitByte = bytes[i];
            if (digitByte < '0' || digitByte > '9') {
                throw new NumberFormatException("non-digit");
            }
            int digit = digitByte - '0';
            if (result < multmin) {
                throw new NumberFormatException("overflow");
            }
            result *= 10;
            if (result < limit + digit) {
                throw new NumberFormatException("overflow");
            }
            result -= digit;
        }
        return negative ? result : -result;
    }

    static byte[] encode(long value) {
        if (value == 0L) {
            return new byte[] {'0'};
        }
        if (value == Long.MIN_VALUE) {
            return new byte[] {
                '-', '9', '2', '2', '3', '3', '7', '2', '0', '3', '6', '8', '5', '4', '7', '7', '5', '8', '0', '8'
            };
        }
        boolean negative = value < 0;
        long remainder = negative ? -value : value;
        byte[] digits = new byte[20];
        int count = 0;
        while (remainder != 0) {
            int digit = (int) (remainder % 10);
            remainder /= 10;
            digits[count++] = (byte) ('0' + digit);
        }
        int length = count + (negative ? 1 : 0);
        byte[] encoded = new byte[length];
        int out = 0;
        if (negative) {
            encoded[out++] = '-';
        }
        while (count > 0) {
            encoded[out++] = digits[--count];
        }
        return encoded;
    }

    static String decodeForTests(byte[] bytes) {
        return new String(bytes, StandardCharsets.US_ASCII);
    }
}
