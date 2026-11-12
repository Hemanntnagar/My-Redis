package com.myredis.command;

/**
 * Redis-style glob matching for binary-safe keys ({@code *}, {@code ?}, bracket expressions, escapes).
 */
public final class KeyGlobMatcher {

    private KeyGlobMatcher() {
    }

    public static boolean matches(byte[] key, byte[] pattern) {
        return match(key, 0, pattern, 0);
    }

    private static boolean match(byte[] key, int keyIndex, byte[] pattern, int patternIndex) {
        while (patternIndex < pattern.length) {
            byte token = pattern[patternIndex];
            if (token == '\\') {
                patternIndex++;
                if (patternIndex >= pattern.length || keyIndex >= key.length) {
                    return false;
                }
                if (key[keyIndex] != pattern[patternIndex]) {
                    return false;
                }
                keyIndex++;
                patternIndex++;
                continue;
            }
            if (token == '*') {
                patternIndex++;
                if (patternIndex >= pattern.length) {
                    return true;
                }
                while (keyIndex <= key.length) {
                    if (match(key, keyIndex, pattern, patternIndex)) {
                        return true;
                    }
                    keyIndex++;
                }
                return false;
            }
            if (keyIndex >= key.length) {
                return false;
            }
            if (token == '?') {
                keyIndex++;
                patternIndex++;
                continue;
            }
            if (token == '[') {
                patternIndex++;
                boolean negate = patternIndex < pattern.length
                        && (pattern[patternIndex] == '^' || pattern[patternIndex] == '!');
                if (negate) {
                    patternIndex++;
                }
                boolean found = false;
                while (patternIndex < pattern.length && pattern[patternIndex] != ']') {
                    if (patternIndex + 2 < pattern.length && pattern[patternIndex + 1] == '-') {
                        byte start = pattern[patternIndex];
                        byte end = pattern[patternIndex + 2];
                        if (start <= key[keyIndex] && key[keyIndex] <= end) {
                            found = true;
                        }
                        patternIndex += 3;
                    } else {
                        if (pattern[patternIndex] == key[keyIndex]) {
                            found = true;
                        }
                        patternIndex++;
                    }
                }
                if (patternIndex >= pattern.length) {
                    return false;
                }
                patternIndex++;
                if (found == negate) {
                    return false;
                }
                keyIndex++;
                continue;
            }
            if (key[keyIndex] != token) {
                return false;
            }
            keyIndex++;
            patternIndex++;
        }
        return keyIndex >= key.length;
    }
}
