package org.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.security.InvalidParameterException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

public class MerkleTree {

    private static final Function<String, byte[]> stringToByte = (a) -> a.getBytes(StandardCharsets.UTF_8);
    private static final Logger logger = LogManager.getLogger(MerkleTree.class);

    private final MerkleNode root;

    private List<MerkleNode> leaves;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private List<MerkleNode> conflictNodes = new ArrayList<>();

    public MerkleTree(List<String> data) {
        if (data == null || data.isEmpty()) throw new InvalidParameterException("Data list not expected to be empty!");
        this.root = buildTree(data);
        logger.info("new merkle tree root hash is {}", HexFormat.of().formatHex(root.getHash()));
    }

    private MerkleNode buildTree(List<String> data) {
        logger.info("build merkle tree by data: {}", data.toString());
        List<MerkleNode> leaves = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            String value = data.get(i);
            MerkleNode leaf = new MerkleNode(value, HashUtil.hash_sha_256(stringToByte.apply(value)), i);
            leaves.add(leaf);
        }
        this.leaves = leaves;
        if (leaves.size() == 1) {
            return leaves.get(0);
        }
        List<MerkleNode> nodes = leaves;
        while (nodes.size() > 1) {
            List<MerkleNode> nextLevel = new ArrayList<>();
            for (int i = 0; i < nodes.size(); i += 2) {
                MerkleNode left = nodes.get(i);
                MerkleNode right = (i + 1 < nodes.size()) ? nodes.get(i + 1) : left;
                MerkleNode parent = new MerkleNode(HashUtil.combineHash(left.getHash(), right.getHash()), left, right);
                nextLevel.add(parent);
            }
            nodes = nextLevel;
        }
        logger.info("build merkle tree finish.");
        return nodes.get(0); // root node
    }


    public List<MerkleProof> getMerkleProof(String targetData) {
        logger.info("get Merkle Proofs of data: {}", targetData);
        List<MerkleProof> proofs = new ArrayList<>();
        MerkleNode leaf = findLeaf(targetData);
        if (leaf == null) {
            return proofs; // Data not found in the tree
        }

        MerkleNode currentNode = leaf;
        while (currentNode.getParent() != null) {
            MerkleNode parent = currentNode.getParent();
            MerkleProof.Direction direction = (currentNode == parent.getLeft()) ? MerkleProof.Direction.RIGHT : MerkleProof.Direction.LEFT;
            MerkleNode sibling = direction == MerkleProof.Direction.RIGHT ? parent.getRight() : parent.getLeft();
            proofs.add(new MerkleProof(sibling.getHash(), direction));
            currentNode = parent;
        }
        logger.info("Merkle Proofs of data are : {}", proofs);
        return proofs;
    }

    public boolean verifyMerkleProof(String data, List<MerkleProof> proofList) {
        logger.info("verify Merkle Proof of data: {} with proofs {}", data, proofList);
        MerkleNode leaf = findLeaf(data);
        if (leaf == null) {
            logger.warn("data {} not exist in merkle tree {}", data, this.root);
            return false; // Data not found in the tree
        }
        byte[] hash = leaf.getHash();
        for (MerkleProof proof : proofList) {
            MerkleNode parent = (leaf.getParent() != null) ? leaf.getParent() : null;
            if (parent == null) return false;

            if (proof.direction == MerkleProof.Direction.LEFT) {
                hash = HashUtil.combineHash(proof.getHash(), hash);
            } else {
                hash = HashUtil.combineHash(hash, proof.getHash());
            }
        }
        return Arrays.equals(root.getHash(), hash); // Merkle path is valid

    }

    public void updateSingleLeaf(String data, String newData) {
        MerkleNode leaf = findLeaf(data);
        if (leaf == null) {
            logger.warn("update leaf failed, {} not found in the merkle tree", data);
            return; // Data not found in the tree
        }
        leaf.update(newData);
        // updateParents(leaf.getParent());
        // updateNodeConcurrent(leaf);

    }
    public List<MerkleNode> getLeaves() {
        return leaves;
    }

    private MerkleNode findLeaf(String data) {
        return this.leaves.stream()
                .filter((leaf) -> Objects.equals(leaf.getValue(), data))
                .findFirst()
                .orElse(null);


        // return findLeaf(root, data);
    }

    private MerkleNode findLeaf(MerkleNode node, String data) {
        if (node.isLeaf()) {
            if (node.getValue().equals(data)) {
                return node;
            }
            return null;
        }
        MerkleNode leftResult = findLeaf(node.getLeft(), data);
        if (leftResult != null) {
            return leftResult;
        }
        return findLeaf(node.getRight(), data);
    }

    private void updateParents(MerkleNode node) {
        if (node == null) {
            return;
        }
        node.updateHashrecur();
        updateParents(node.getParent());
    }
    /*public void updateTree(List<Pa> ) {
        this.conflictNodes = findConflictNodes(leaves);
        for(MerkleNode leaf: leaves){
            updateNodeConcurrent(leaf);
        }
    }*/

    public void updateLeaves(List<MerkleNode> modifiedLeaves) {
        modifiedLeaves.sort(Comparator.comparingInt(MerkleNode::getIndex));
        this.conflictNodes = findConflictNodes(modifiedLeaves);
        Thread thread = new Thread(() -> {

            //    modifiedLeaves.get(0).updateHash();
            updateTreeByNodeConcurrent1(modifiedLeaves.get(0));

            System.out.println("Thread 1  is running");
        });

        Thread thread2 = new Thread(() -> {

            //  modifiedLeaves.get(1).updateHash();
            updateTreeByNodeConcurrent1(modifiedLeaves.get(1));

            System.out.println("Thread 2  is running");
        });

        thread.start();
        thread2.start();
       /* for(MerkleNode leaf: modifiedLeaves){
            System.out.println("enter for 1");
            leaf.updateHash();
            updateNodeConcurrent1(leaf);
        }*/

        conflictNodes.clear();
        System.out.println("Leaf A D updated to  a d, and root hash updated to " + HexFormat.of().formatHex(getRoot().getHash()));
        //setallnode isvisited to false
    }

    private List<MerkleNode> findConflictNodes(List<MerkleNode> leaves) {
        List<MerkleNode> conflictNodes = new ArrayList<>();
        if (leaves == null || leaves.size() <= 1) {
            return conflictNodes;
        }

        MerkleNode node = leaves.get(0);
        for (int j = 1; j < leaves.size(); j++) {
            node = lowestCommonAncestor(root, node, leaves.get(j));
            conflictNodes.add(node);
        }
        return conflictNodes;
    }

    private MerkleNode lowestCommonAncestor(MerkleNode root, MerkleNode p, MerkleNode q) {
        if (root == null || root == p || root == q) return root;
        MerkleNode left = lowestCommonAncestor(root.getLeft(), p, q);
        MerkleNode right = lowestCommonAncestor(root.getRight(), p, q);
        if (left == null) return right;
        if (right == null) return left;
        return root;
    }

    private void updateNodeConcurrent(MerkleNode node) {
        if (node == null) {
            return;
        }
        MerkleNode parent = node.getParent();
        if (conflictNodes.contains(node)) {
            node.lock.writeLock().lock();
            if (!node.isVisited()) {
                node.setVisited(true);
                return;
            }
            node.updateHashrecur();
            node.lock.writeLock().unlock();
        } else {
            node.updateHashrecur();
        }
    }

    private void updateTreeByNodeConcurrent1(MerkleNode node) {
        //TODO
        node.updateHash();
        if (node.getParent() != null) {
            MerkleNode parent = node.getParent();
            logger.info("1 parent hash {}", HexFormat.of().formatHex(parent.getHash()));
            if (conflictNodes.contains(parent)) {
                logger.info("2 calcul parent hash {}", HexFormat.of().formatHex(parent.getHash()));
                try {
                    parent.lock.writeLock().lock();
                    if (!parent.isVisited()) {
                        logger.info("2.5 calcul parent hash {}", HexFormat.of().formatHex(parent.getHash()));
                        parent.setVisited(true);
                        Thread.currentThread().interrupt();
                    } else {
                        logger.info("3 calcul parent hash {}", HexFormat.of().formatHex(parent.getHash()));
                        parent.updateHash();
                        updateTreeByNodeConcurrent1(parent);
                    }
                } finally {
                    parent.lock.writeLock().unlock();
                }

            } else {
                parent.updateHash();
                updateTreeByNodeConcurrent1(parent);
                logger.info("4 calcul parent hash {}", HexFormat.of().formatHex(parent.getHash()));
            }
        }
        logger.info("exit while");
    }

    public void verifyTree() {
    }

    public MerkleNode getRoot() {
        return root;
    }


    public static void main(String[] args) throws InterruptedException {
        // Test harness
        List<String> data = List.of("A", "B", "C", "D", "E");
        MerkleTree tree = new MerkleTree(data);
        byte[] originalHash = tree.getRoot().getHash();

        Map<String, String> map = new ConcurrentHashMap<>();
        map.put("a", "aval");

        logger.info("root hash is {}", tree.getRoot());

        // Generate and verify Merkle proof
        String targetData = "C";
        List<MerkleProof> merklePath = tree.getMerkleProof(targetData);
        logger.info(String.format("Merkle proof for %s : %s", targetData, merklePath));

        List<MerkleProof> merklePath2 = tree.getMerkleProof("D");
        logger.info(String.format("Merkle proof  for D : %s", merklePath2));

        if (merklePath != null) {
            logger.info(String.format("Merkle proof for %s : %s", targetData, merklePath));
            // System.out.println("Merkle proof for " + targetData + ": " + merklePath);
            boolean isValid = tree.verifyMerkleProof(targetData, merklePath);

            System.out.println("Is Merkle proof valid? " + isValid);
        } else {
            System.out.println(targetData + " not found in the Merkle tree.");
        }

        // Update a leaf and verify again
        String newData = "d";
        MerkleNode nodeA = tree.getLeaves().get(0);
        nodeA.setValue("a");
        MerkleNode nodeD = tree.getLeaves().get(3);
        nodeD.setValue("d");
        // tree.updateLeaf("A", "a");
        //tree.updateLeaf("D", "d");
        List<MerkleNode> list = new ArrayList<>();
        list.add(nodeA);
        list.add(nodeD);
        Thread thread2 = new Thread(() -> {

            //  modifiedLeaves.get(1).updateHash();
            tree.updateLeaves(list);


        });
        thread2.start();

        Thread.sleep(10000);
        byte[] updatedHash = tree.getRoot().getHash();
        System.out.println("Leaf A D updated to  a d, and root hash updated to " + HexFormat.of().formatHex(tree.getRoot().getHash()));
        System.out.println("is tree always the same? " + Arrays.equals(originalHash, updatedHash));

        List<MerkleProof> updatedMerklePath = tree.getMerkleProof(newData);
        if (updatedMerklePath != null) {
            System.out.println("Updated Merkle proof for " + newData + ": " + updatedMerklePath);
            boolean isValid = tree.verifyMerkleProof(newData, updatedMerklePath);
            System.out.println("Is updated Merkle proof valid? " + isValid);
        } else {
            System.out.println(newData + " not found in the Merkle tree.");
        }
        List<MerkleProof> merklePath3 = tree.getMerkleProof("d");
        logger.info(String.format(" new Merkle proof  for d : %s", merklePath3));
        logger.info("new root hash is {}", tree.getRoot());
    }
}
