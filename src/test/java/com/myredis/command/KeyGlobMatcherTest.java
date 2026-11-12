package com.myredis.command;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyGlobMatcherTest {

    private static byte[] b(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void starMatchesAllKeys() {
        assertTrue(KeyGlobMatcher.matches(b("anything"), b("*")));
        assertTrue(KeyGlobMatcher.matches(b(""), b("*")));
    }

    @Test
    void questionMarkMatchesSingleCharacter() {
        assertTrue(KeyGlobMatcher.matches(b("hallo"), b("h?llo")));
        assertTrue(KeyGlobMatcher.matches(b("hello"), b("h?llo")));
        assertFalse(KeyGlobMatcher.matches(b("hllo"), b("h?llo")));
        assertFalse(KeyGlobMatcher.matches(b("heallo"), b("h?llo")));
    }

    @Test
    void prefixStarSuffixPattern() {
        assertTrue(KeyGlobMatcher.matches(b("user:1"), b("user:*")));
        assertFalse(KeyGlobMatcher.matches(b("other:1"), b("user:*")));
    }

    @Test
    void bracketExpressionMatchesSingleCharacterFromSet() {
        assertTrue(KeyGlobMatcher.matches(b("hello"), b("h[ae]llo")));
        assertTrue(KeyGlobMatcher.matches(b("hallo"), b("h[ae]llo")));
        assertFalse(KeyGlobMatcher.matches(b("hillo"), b("h[ae]llo")));
    }

    @Test
    void bracketNegationRejectsMatchingCharacter() {
        assertTrue(KeyGlobMatcher.matches(b("hallo"), b("h[^e]llo")));
        assertFalse(KeyGlobMatcher.matches(b("hello"), b("h[^e]llo")));
    }

    @Test
    void bracketRangeMatchesByteRange() {
        assertTrue(KeyGlobMatcher.matches(b("hallo"), b("h[a-c]llo")));
        assertTrue(KeyGlobMatcher.matches(b("hbllo"), b("h[a-c]llo")));
        assertFalse(KeyGlobMatcher.matches(b("hdllo"), b("h[a-c]llo")));
    }

    @Test
    void escapeMatchesLiteralMetacharacter() {
        assertTrue(KeyGlobMatcher.matches(b("foo*bar"), b("foo\\*bar")));
        assertFalse(KeyGlobMatcher.matches(b("fooxbar"), b("foo\\*bar")));
    }
}
