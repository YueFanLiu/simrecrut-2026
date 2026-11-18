package fr.isep.simrecrut.common.id;

import java.math.BigInteger;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class PublicIdGenerator {

    private static final char[] ALPHABET =
            "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final BigInteger MASK = BigInteger.valueOf(31);
    private final SecureRandom random = new SecureRandom();

    public String nextId() {
        byte[] value = new byte[16];
        random.nextBytes(value);
        long timestamp = System.currentTimeMillis();

        for (int index = 5; index >= 0; index--) {
            value[index] = (byte) timestamp;
            timestamp >>>= 8;
        }

        BigInteger number = new BigInteger(1, value);
        char[] encoded = new char[26];

        for (int index = encoded.length - 1; index >= 0; index--) {
            encoded[index] = ALPHABET[number.and(MASK).intValue()];
            number = number.shiftRight(5);
        }

        return new String(encoded);
    }
}
