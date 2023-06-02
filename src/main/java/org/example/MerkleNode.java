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

    @Override
    public String toString() {
        return "MerkleNode{"
                + "value = " + value
                + " left hash =" + HexFormat.of().formatHex(left.getHash())
                + " right hash =" + HexFormat.of().formatHex(right.getHash())
                + " hash="
                + HexFormat.of().formatHex(this.hash)
                + '}';
    }
}
