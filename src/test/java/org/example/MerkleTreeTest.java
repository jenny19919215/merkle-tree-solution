package org.example;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MerkleTreeTest {
    private static MerkleTree merkleTree;
    private static List<String> data;

    @BeforeEach
    public void setUp() {
        data = List.of("A", "B", "C", "D", "E");
        merkleTree = new MerkleTree(data);
    }

    @Test
    public void test_null_input_for_tree_throw_exception() {
        assertThrows(InvalidParameterException.class, () -> new MerkleTree(null));
    }

    @Test
    public void testRootHashAreSame() {
        MerkleTree merkleTree1 = new MerkleTree(data);
        assertArrayEquals(merkleTree.getRoot().getHash(),merkleTree1.getRoot().getHash());
    }

    @Test
    public void test_hash_for_case_sensitive() {
        List<String> lowercase_data = data.stream().map(String::toLowerCase).toList();
        MerkleTree merkleTree1 = new MerkleTree(lowercase_data);
        assertFalse(Arrays.equals(merkleTree.getRoot().getHash(), merkleTree1.getRoot().getHash()));
    }

    @Test
    public void testRootHashAsExpected() {
        MerkleTree merkleTree1 = new MerkleTree(data);
        assertEquals("cc7461bf32d59f9249796e6e7691f304a3221825732284b9c049b1dd2c689b56", HexFormat.of().formatHex(merkleTree1.getRoot().getHash()));
    }

    @Test
    public void test_get_proof_leaf_not_exist() {
        String targetData = "c";
        List<MerkleProof> merklePath = merkleTree.getMerkleProof(targetData);
        assertTrue(merklePath.isEmpty());
    }

    @Test
    public void test_get_proof_leaf_exist() {
        String targetData = "C";
        List<MerkleProof> merklePath = merkleTree.getMerkleProof(targetData);
        assertEquals(3, merklePath.size());

        assertEquals("3f39d5c348e5b79d06e842c114e6cc571583bbf44e4b0ebfda1a01ec05745d43", HexFormat.of().formatHex(merklePath.get(0).getHash()));
        assertEquals(MerkleProof.Direction.RIGHT, merklePath.get(0).getDirection());

        assertEquals("63956f0ce48edc48a0d528cb0b5d58e4d625afb14d63ca1bb9950eb657d61f40", HexFormat.of().formatHex(merklePath.get(1).getHash()));
        assertEquals(MerkleProof.Direction.LEFT, merklePath.get(1).getDirection());

        assertEquals("d48e3e0653332ff79423214119dfc2d81992f4b9778520be3a424b334b846c9d", HexFormat.of().formatHex(merklePath.get(2).getHash()));
        assertEquals(MerkleProof.Direction.RIGHT, merklePath.get(2).getDirection());

    }

    @Test
    public void verify_with_incorrect_proof_list_failed() {
        String targetData = "C";
        List<MerkleProof> merklePath = merkleTree.getMerkleProof(targetData);
        merklePath.remove(0);
        assertFalse(merkleTree.verifyMerkleProof(targetData,merklePath));
    }

    @Test
    public void verify_proof_of_data_not_exist_failed() {
        String targetData = "c";
        List<MerkleProof> merklePath = merkleTree.getMerkleProof(targetData);
        assertFalse(merkleTree.verifyMerkleProof(targetData,merklePath));
    }

    @Test
    public void verify_proof_of_data_successful() {
        String targetData = "C";
        List<MerkleProof> merklePath = merkleTree.getMerkleProof(targetData);
        assertTrue(merkleTree.verifyMerkleProof(targetData,merklePath));
    }

    @Test
    public void update_single_leaf_with_new_value_not_found() throws InterruptedException {
        byte[] rootHash = merkleTree.getRoot().getHash();
        MerkleNode leaf = new MerkleNode("a", HashUtil.hash_sha_256("a".getBytes(StandardCharsets.UTF_8)),-1);
        merkleTree.updateLeaves(List.of(leaf));
        assertArrayEquals(rootHash,merkleTree.getRoot().getHash());
    }

    @Test
    public void update_single_leaf_with_new_value_successful() {
        String targetData = "C";
        MerkleNode leaf = merkleTree.findLeaf("C");
       // leaf.setValue("F");
        merkleTree.updateSingleLeaf("C","F");
      //  merkleTree.updateLeaf1(leaf);
        assertEquals("2f48bc499259fcbdf8119716a116652f0ac5388a2a63b57478113c78fac8c576", HexFormat.of().formatHex(merkleTree.getRoot().getHash()));
    }


    @Test
    public void update_leaves() throws InterruptedException {
        String targetData = "A";
        MerkleNode leaf = merkleTree.findLeaf(targetData);
        leaf.setValue("a");

        String targetData1 = "D";
        MerkleNode leaf1 = merkleTree.findLeaf(targetData1);
        leaf1.setValue("d");

        merkleTree.updateLeaves(List.of(leaf,leaf1));

       /* merkleTree.updateSingleLeaf("A","a");
        merkleTree.updateSingleLeaf("D","d");*/

        assertEquals("619c2cc9c54ad5efdab2306a12be2209e0ff0710c907dc275bdb6ddf55035daf",HexFormat.of().formatHex(merkleTree.getRoot().getHash()));
        // assertArrayEquals("", merkleTree.getRoot().getHash());
    }

    @Test
    public void update_leaves2() throws InterruptedException {
        String targetData = "A";
        MerkleNode leaf = merkleTree.findLeaf(targetData);
        leaf.setValue("a");

        String targetData1 = "D";
        MerkleNode leaf1 = merkleTree.findLeaf(targetData1);
        leaf1.setValue("d");

        String targetData2 = "E";
        MerkleNode leaf2 = merkleTree.findLeaf(targetData2);
        leaf2.setValue("e");

        /*merkleTree.updateSingleLeaf("A","a");
        merkleTree.updateSingleLeaf("D","d");
        merkleTree.updateSingleLeaf("E","e");*/

        merkleTree.updateLeaves(List.of(leaf,leaf1,leaf2));
        //  merkleTree.resetAfterTreeUpdate();
        assertEquals("68812a5ef301d5712a0fc4fc923e194418eb17bcfa2cb54272b2be8df915f7d0",HexFormat.of().formatHex(merkleTree.getRoot().getHash()));
        // assertArrayEquals("", merkleTree.getRoot().getHash());
    }

    @Test
    public void testMerkleProof() {
        String targetData = "C";
        List<MerkleProof> merklePath = merkleTree.getMerkleProof(targetData);
        assertNotNull(merklePath);
        assertTrue(merkleTree.verifyMerkleProof(targetData, merklePath));

        String newData = "F";
        merkleTree.updateSingleLeaf(targetData, newData);
        List<MerkleProof> updatedMerklePath = merkleTree.getMerkleProof(newData);
        assertNotNull(updatedMerklePath);
        assertTrue(merkleTree.verifyMerkleProof(newData, updatedMerklePath));
    }



}