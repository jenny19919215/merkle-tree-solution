package org.example;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HashUtil {

    public static byte[] hash_sha_256(byte[] data){
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
           /* byte[] truncatedHash = new byte[16]; // 128-bit output
            System.arraycopy(hash, 0, truncatedHash, 0, truncatedHash.length);*/
            return digest.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 hash algorithm not found.", e);
        }
    }

    public static byte[] combineHash(byte[] left, byte[] right) {
        byte[] result = new byte[left.length+right.length];
        System.arraycopy(left, 0, result, 0, left.length);
        System.arraycopy(right, 0, result, left.length, right.length);
        return hash_sha_256(result);

    }
}
