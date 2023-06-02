package org.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExampleTest {
    private static final Logger logger = LogManager.getLogger(ExampleTest.class);
    public static void main(String[] args) throws InterruptedException {
        ExecutorService executors = Executors.newFixedThreadPool(3);

        //Build a merkle tree
        List<String> data = List.of("A", "B", "C", "D", "E");
        logger.info("1. Build a merkle tree by data {}", data);
        MerkleTree tree = new MerkleTree(data);

        byte[] originalHash = tree.getRoot().getHash();
        logger.info("original root is {}  \n\n", tree.getRoot());

        // Generate and verify Merkle proof
        logger.info("2. Generate merkle proofs for leave 'C'. ");
        String targetData = "C";
        List<MerkleProof> merklePath = tree.getMerkleProof(targetData);


        if (!merklePath.isEmpty()) {
            logger.info(String.format("Merkle proof for %s : %s", targetData, merklePath));
            logger.info("3. Test if merkle proofs for leave 'C' is valid in this merkle tree.");
            boolean isValid = tree.verifyMerkleProof(targetData, merklePath);
            logger.info("Is Merkle proof valid? {}  \n\n", isValid);
        } else {
            System.out.println(targetData + " not found in the Merkle tree. \n\n");
        }

        logger.info("4. Update leaf 'C' to 'c' ");
        MerkleNode leaf = tree.findLeaf(targetData);
        String newVal = "c";
        leaf.setValue(newVal);
        tree.updateLeaves(List.of(leaf),null);
        List<MerkleProof> proofs = tree.getMerkleProof(newVal);
        logger.info("4.1 Merkle proof for {} : {}", newVal, proofs);
        boolean isValid = tree.verifyMerkleProof(newVal, proofs);
        logger.info("Is Merkle proof valid? {} \n\n", isValid);


        logger.info("5. Update leaves concurrently.");

        String targetDataA = "A";
        MerkleNode leafA = tree.findLeaf(targetDataA);
        leafA.setValue("a");

        String targetDataD = "D";
        MerkleNode leafD = tree.findLeaf(targetDataD);
        leafD.setValue("d");

        String targetDataE = "E";
        MerkleNode leafE = tree.findLeaf(targetDataE);
        leafE.setValue("e");

        tree.updateLeaves(List.of(leafA,leafD,leafE),executors);

        // Update a leaf and verify again

        List<MerkleProof> updatedMerklePath = tree.getMerkleProof("a");
        logger.info("Is Merkle proof valid for update leaf \"A\" to \"a\"? {}  \n\n", tree.verifyMerkleProof("a", updatedMerklePath));


    }
}
