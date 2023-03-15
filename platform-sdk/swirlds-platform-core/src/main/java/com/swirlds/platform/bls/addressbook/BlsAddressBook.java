/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.platform.bls.addressbook;

import com.swirlds.common.system.NodeId;
import com.swirlds.platform.bls.crypto.BlsPublicKey;
import com.swirlds.platform.bls.crypto.PublicKeyShares;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Class representing an address book. Will be conceptually merged into the existing platform address book
 */
public class BlsAddressBook {

    /** Mapping from nodeId to a data object describing the node */
    private final Map<NodeId, BlsNodeData> nodeDataMap;

    /** A sorted set of nodeIds */
    private final SortedSet<NodeId> sortedNodeIds;

    /** Total number of shares belonging to all nodes in the address book */
    private int totalShares;

    /** The {@link PublicKeyShares} object, representing the public keys of all existing shares */
    private PublicKeyShares publicKeyShares;

    /** True if nodes have been added or removed since calling {@link #recomputeTransientData()} */
    private boolean dirty;

    /** Constructor */
    public BlsAddressBook() {
        this.nodeDataMap = new HashMap<>();
        this.totalShares = 0;
        this.sortedNodeIds = new TreeSet<>();
        this.publicKeyShares = new PublicKeyShares();
        this.dirty = true;
    }

    /**
     * Copy constructor
     *
     * @param otherAddressBook the address book being copied
     */
    public BlsAddressBook(final BlsAddressBook otherAddressBook) {
        final Map<NodeId, BlsNodeData> copiedNodeDataMap = new HashMap<>();

        for (final Map.Entry<NodeId, BlsNodeData> nodeDataEntry : otherAddressBook.nodeDataMap.entrySet()) {
            final NodeId nodeId = nodeDataEntry.getKey();
            final BlsNodeData nodeData = nodeDataEntry.getValue();

            copiedNodeDataMap.put(nodeId, new BlsNodeData(nodeData));
        }

        this.nodeDataMap = copiedNodeDataMap;
        this.sortedNodeIds = new TreeSet<>(otherAddressBook.sortedNodeIds);
        this.totalShares = otherAddressBook.getTotalShares();
        this.publicKeyShares = new PublicKeyShares(otherAddressBook.publicKeyShares);
        this.dirty = otherAddressBook.dirty;
    }

    /**
     * Gets the sorted list of nodeIds in the address book
     *
     * @return a sorted list of node ids
     */
    public SortedSet<NodeId> getSortedNodeIds() {
        return Collections.unmodifiableSortedSet(sortedNodeIds);
    }

    /**
     * Gets the number of shares belonging to a node. A node's share count is equal to its stake
     *
     * @param nodeId which node's share count is being requested
     * @return the corresponding share count
     */
    public int getNodeShareCount(final NodeId nodeId) {
        if (!containsNode(nodeId)) {
            throw new IllegalArgumentException(
                    String.format("Cannot get share count for node %s that isn't in the address book", nodeId));
        }

        return nodeDataMap.get(nodeId).getShareCount();
    }

    /**
     * Gets the total number of shares held by all nodes in the address book
     *
     * @return the value of {@link #totalShares}
     */
    public int getTotalShares() {
        checkDirty("getTotalShares");

        return totalShares;
    }

    /**
     * Gets the public IBE key associated with a node id
     *
     * @param nodeId which node's public key is being requested
     * @return the corresponding IBE public key
     */
    public BlsPublicKey getIbePublicKey(final NodeId nodeId) {
        if (!containsNode(nodeId)) {
            throw new IllegalArgumentException("Cannot get public key for node that isn't in the address book");
        }

        return nodeDataMap.get(nodeId).getIbePublicKey();
    }

    /**
     * Recomputes values that are derived from the existing composition of the address book
     *
     * <p>Sets {@link #dirty} to false after doing calculations
     */
    public void recomputeTransientData() {
        totalShares = 0;

        int shareId = 1;
        for (final NodeId nodeId : sortedNodeIds) {
            int nodeShareCount = getNodeShareCount(nodeId);
            totalShares = Math.addExact(totalShares, nodeShareCount);

            final List<Integer> nodeShareIds = new ArrayList<>();

            for (int shareCount = 0; shareCount < nodeShareCount; ++shareCount) {
                nodeShareIds.add(shareId);
                ++shareId;
            }

            final BlsNodeData nodeData = nodeDataMap.get(nodeId);
            nodeData.setShareIds(nodeShareIds);
        }

        dirty = false;
    }

    /**
     * Create a new entry in the address book
     *
     * @param nodeId       node id of the new node
     * @param stake        consensus stake belonging to the new node
     * @param ibePublicKey IBE public key of the new node
     */
    public void addNode(final NodeId nodeId, final long stake, final BlsPublicKey ibePublicKey) {
        nodeDataMap.put(nodeId, new BlsNodeData(stake, ibePublicKey));
        sortedNodeIds.add(nodeId);

        dirty = true;
    }

    /**
     * Removes a node from the address book
     *
     * @param nodeId the id of the node to remove
     */
    public void removeNode(final NodeId nodeId) {
        nodeDataMap.remove(nodeId);
        sortedNodeIds.remove(nodeId);

        dirty = true;
    }

    /**
     * Sets the {@link PublicKeyShares} of the address book
     *
     * @param publicKeyShares the new public key shares
     */
    public void setPublicKeyShares(final PublicKeyShares publicKeyShares) {
        this.publicKeyShares = publicKeyShares;
    }

    /**
     * Gets the {@link PublicKeyShares} of the address book
     *
     * @return the public key shares
     */
    public PublicKeyShares getPublicKeyShares() {
        return publicKeyShares;
    }

    /**
     * Checks if a node is in this address book
     *
     * @param nodeId the id of the node to check
     * @return true if the node is in this address book, otherwise false
     */
    public boolean containsNode(final NodeId nodeId) {
        return nodeDataMap.containsKey(nodeId);
    }

    /**
     * Gets a list of share ids that belong to a single node
     *
     * @param nodeId the id of the node to get share ids for
     * @return a list of share ids that belong to nodeId
     */
    public List<Integer> getNodeShareIds(final NodeId nodeId) {
        checkDirty("getNodeShareIds");

        if (!containsNode(nodeId)) {
            throw new IllegalArgumentException("Cannot get share ids for node that isn't in the address book");
        }

        return nodeDataMap.get(nodeId).getShareIds();
    }

    /**
     * Gets the combined share count of a set of nodes
     *
     * @param nodeSet the set of nodes to get the combined share count of
     * @return the combined share count of the set of nodes
     */
    public int getCombinedShares(final Set<NodeId> nodeSet) {
        int combinedShares = 0;

        for (final NodeId nodeId : nodeSet) {
            if (containsNode(nodeId)) {
                // we don't have to worry about overflow, since we check elsewhere that even the
                // total share count won't
                // exceed integer bounds
                combinedShares += getNodeShareCount(nodeId);
            }
        }

        return combinedShares;
    }

    /**
     * Gets the number of shares owned by nodes that haven't been disqualified
     *
     * @param maliciousNodes the set of malicious nodes
     * @param offlineNodes   the set of offline nodes
     * @return the combined share count of nodes in neither malicious nor offline sets
     */
    public int getNonDisqualifiedShareCount(final Set<NodeId> maliciousNodes, final Set<NodeId> offlineNodes) {
        return getTotalShares() - getCombinedShares(maliciousNodes) - getCombinedShares(offlineNodes);
    }

    /**
     * Gets the minimum share count which satisfies a given threshold
     *
     * @param threshold the threshold the returned share count satisfies
     * @return the number of shares which satisfy the given threshold
     */
    public int getSharesSatisfyingThreshold(final Threshold threshold) {
        if (sortedNodeIds.isEmpty()) {
            throw new IllegalStateException("Cannot determine threshold if address book contains no nodes");
        }

        // don't enforce a threshold
        if (threshold == null) {
            return 0;
        }

        // cannot overflow, since the value returned by getMinSatisfyingValue will always be <= the
        // whole value, which in this case is an int
        return (int) threshold.getMinValueMeetingThreshold(getTotalShares());
    }

    /**
     * Sets the IBE public key of a node. Serves only to allow genesis nodes to add their public keys after CRS
     * generation, and before initial key generation
     *
     * @param nodeId       the id of the node to set the public key for
     * @param ibePublicKey the node's new public key
     */
    public void setIbePublicKey(final NodeId nodeId, final BlsPublicKey ibePublicKey) {
        if (!nodeDataMap.containsKey(nodeId)) {
            throw new IllegalArgumentException("Cannot set IBE public key for node that doesn't exist in address book");
        }

        if (nodeDataMap.get(nodeId).getIbePublicKey() != null) {
            throw new IllegalStateException("Cannot overwrite existing IBE public key");
        }

        nodeDataMap.get(nodeId).setIbePublicKey(ibePublicKey);
    }

    /**
     * Sets a new stake value for a node
     *
     * @param nodeId   the id of the node to set the stake of
     * @param newStake the new consensus stake value for the node
     */
    public void setNodeStake(final NodeId nodeId, final long newStake) {
        if (!nodeDataMap.containsKey(nodeId)) {
            throw new IllegalArgumentException("Cannot set stake for node that doesn't exist in address book");
        }

        nodeDataMap.get(nodeId).setStake(newStake);

        dirty = true;
    }

    /**
     * Checks if the address book is dirty before performing an operation that requires a clean address book
     *
     * @param attemptedOperation the operation that is being attempted, for logging's sake in case address book is
     *                           dirty
     */
    private void checkDirty(final String attemptedOperation) {
        if (!dirty) {
            return;
        }

        throw new IllegalStateException("Cannot " + attemptedOperation + ", since address book is dirty");
    }
}
