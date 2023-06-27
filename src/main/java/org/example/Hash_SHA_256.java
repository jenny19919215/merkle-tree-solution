package org.example;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Hash_SHA_256 implements HashFunction {

    public Hash_SHA_256() {
    }

    public byte[] hash(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
           /* byte[] truncatedHash = new byte[16]; // 128-bit output
            System.arraycopy(hash, 0, truncatedHash, 0, truncatedHash.length);*/
            return digest.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 hash algorithm not found.", e);
        }
    }
}
