package org.example;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@EqualsAndHashCode
@Getter
@Setter
public class MerkleNode {
    private String value;
    private byte[] hash;
    private MerkleNode left;
    private MerkleNode right;
    private MerkleNode parent;
    private boolean visited;
    private int index = -1;

    protected final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();


    public MerkleNode(String value, byte[] hash, int index) {
        this.value = value;
        this.hash = hash;
        this.index = index;
    }

    public MerkleNode(byte[] hash, MerkleNode left, MerkleNode right) {
        this.hash = hash;
        this.left = left;
        this.right = right;
        left.setParent(this);
        right.setParent(this);
    }

    public void update(String value) {
        this.value = value;
        this.updateHashrecur();
    }

    public boolean isLeaf() {
        return left == null && right == null;

    }

    public void updateHashrecur() {
        if (isLeaf()) {
            hash = HashUtil.hash_sha_256(value.getBytes(StandardCharsets.UTF_8));
        } else {
            hash = HashUtil.combineHash(left.getHash(), right.getHash());
        }
        if (parent != null) {
            parent.updateHashrecur();
        }
    }

    public void updateHash() {
        if (isLeaf()) {
            hash = HashUtil.hash_sha_256(value.getBytes(StandardCharsets.UTF_8));
        } else {
            hash = HashUtil.combineHash(left.getHash(), right.getHash());
        }
    }

    private String computeHash(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte hashByte : hashBytes) {
                String hex = Integer.toHexString(0xff & hashByte);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 hash algorithm not found.", e);
        }
    }

    @Override
    public String toString() {
        return "MerkleNode{"
                + "value = " + value + " Hash=0x"
                + HexFormat.of().formatHex(this.hash)
                + '}';
    }
}