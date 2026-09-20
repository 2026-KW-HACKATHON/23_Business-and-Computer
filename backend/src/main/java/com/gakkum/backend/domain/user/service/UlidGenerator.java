package com.gakkum.backend.domain.user.service;

import java.math.BigInteger;
import java.security.SecureRandom;

final class UlidGenerator {

    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final BigInteger MASK = BigInteger.valueOf(31);

    private UlidGenerator() {
    }

    static String generate() {
        byte[] bytes = new byte[16];
        long timestamp = System.currentTimeMillis();
        for (int i = 5; i >= 0; i--) {
            bytes[i] = (byte) timestamp;
            timestamp >>>= 8;
        }

        byte[] random = new byte[10];
        RANDOM.nextBytes(random);
        System.arraycopy(random, 0, bytes, 6, random.length);

        BigInteger value = new BigInteger(1, bytes);
        char[] ulid = new char[26];
        for (int i = ulid.length - 1; i >= 0; i--) {
            ulid[i] = ALPHABET[value.and(MASK).intValue()];
            value = value.shiftRight(5);
        }
        return new String(ulid);
    }
}
