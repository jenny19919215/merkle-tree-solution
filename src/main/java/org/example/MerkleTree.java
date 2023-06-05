package org.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.security.InvalidParameterException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

public class MerkleTree {

    private static final Function<String, byte[]> stringToByte = a -> a.getBytes(StandardCharsets.UTF_8);
    private static final Logger logger = LogManager.getLogger(MerkleTree.class);
    private final MerkleNode root;
    private final List<MerkleNode> leaves = new ArrayList<>();
    private List<MerkleNode> conflictNodes = new ArrayList<>();

    protected final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public MerkleTree(List<String> data) {
        if (data == null || data.isEmpty()) throw new InvalidParameterException("Data list not expected to be empty!");
        this.root = buildTree(data);
        logger.info("new merkle tree root hash is {}", HexFormat.of().formatHex(root.getHash()));
    }

    private MerkleNode buildTree(List<String> data) {
        logger.info("build merkle tree by data: {}", data);
        for (int i = 0; i < data.size(); i++) {
            String value = data.get(i);
            MerkleNode leaf = new MerkleNode(value, HashUtil.hash_sha_256(stringToByte.apply(value)), i);
            leaves.add(leaf);
        }
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
        try {
            lock.readLock().lock();
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
                try {
                    sibling.lock.readLock().lock();
                    proofs.add(new MerkleProof(sibling.getHash(), direction));
                } finally {
                    sibling.lock.readLock().unlock();
                }
                currentNode = parent;
            }
            logger.info("Merkle Proofs of data are : {}", proofs);
            return proofs;

        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean verifyMerkleProof(String data, List<MerkleProof> proofList) {
        try {
            lock.readLock().lock();
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
        } finally {
            lock.readLock().unlock();
        }

    }

    public MerkleNode findLeaf(String data) {
        return this.leaves.stream()
                .filter(leaf -> Objects.equals(leaf.getValue(), data))
                .findFirst()
                .orElse(null);
    }

    public void updateLeaves(List<MerkleNode> modifiedLeaves, ExecutorService executors) {
        try {
            lock.writeLock().lock();
            logger.info("start to update leaves {}", modifiedLeaves);
            ArrayList<MerkleNode> leaves = new ArrayList<>();
            for (MerkleNode leaf : modifiedLeaves) {
                if (this.leaves.contains(leaf)) {
                    leaves.add(leaf);
                }
            }

            if (leaves.isEmpty()) {
                logger.info("no leaves to update exist in current merkle tree");
                return;
            }
            //update leaves by synchro
            if (executors == null) {
                for (MerkleNode leaf : leaves) {
                    updateNode(leaf);
                }
                return;
            }

            leaves.sort(Comparator.comparingInt(MerkleNode::getIndex));
            this.conflictNodes = findConflictNodes(leaves);

            for (int i = 0; i < leaves.size(); i++) {
                final int index = i;
                CompletableFuture.runAsync(() -> {
                    logger.info("final i = {}", index);
                    updateNode(leaves.get(index));
                }, executors);
                // Process the batch of data
            }

            executors.shutdown();
            while (!executors.isTerminated()) {
            }
            resetAfterConcurrentTreeUpdate();
        } finally {
            lock.writeLock().unlock();
        }
    }


    private void resetAfterConcurrentTreeUpdate() {
        conflictNodes.clear();
        resetVisitedFlag(this.root);

    }

    private void resetVisitedFlag(MerkleNode node) {
        if (node == null) return;

        node.setVisited(false);
        resetVisitedFlag(node.getLeft());
        resetVisitedFlag(node.getRight());
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

    private void updateNode(MerkleNode node) {
        //update node hash
        logger.debug("update node {}", node);
        node.updateHash();
        //update ancestor tree
        while (node.getParent() != null) {
            node = node.getParent();
            logger.debug("1 parent hash {}", HexFormat.of().formatHex(node.getHash()));
            if (conflictNodes.contains(node)) {
                logger.debug("2 node is conflict nodes {}", HexFormat.of().formatHex(node.getHash()));
                try {
                    node.lock.writeLock().lock();
                    logger.debug("get lockkkkkkkkkkkkkkkkkk");
                    if (!node.isVisited()) {
                        logger.info("2.5 set visited {}", HexFormat.of().formatHex(node.getHash()));
                        node.setVisited(true);
                        break;
                    } else {
                        logger.debug("3 node visited = true {}", HexFormat.of().formatHex(node.getHash()));
                        node.updateHash();
                    }
                } finally {
                    node.lock.writeLock().unlock();
                    logger.debug("release lockkkkkkkkkkkkkkkkkkk");
                }
            } else {
                node.updateHash();
                logger.debug("4 udpate parent hash {}", HexFormat.of().formatHex(node.getHash()));
            }
        }
    }

    public MerkleNode getRoot() {
        return root;
    }
}
