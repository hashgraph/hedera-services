/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.util;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * The HashTreeManager class is responsible for managing streaming elements and calculating the root hash of a Tree.
 * @param <T> The type of elements in the tree
 */
public class HashTreeManager<T> {

    private List<Bytes> hashList = new ArrayList<>(); // The list of hashes in the tree
    private final Codec<T> codec; // The codec used to encode and decode the elements in the tree
    private final MessageDigest digest; // The message digest

    /**
     * Constructor for the HashTreeManager class.
     * @param codec The codec used to encode and decode the elements in the tree
     * @throws NoSuchAlgorithmException If the hashing algorithm is not found
     */
    public HashTreeManager(Codec<T> codec, MessageDigest digest) throws NoSuchAlgorithmException {
        this.codec = codec;
        this.digest = digest;
    }

    /** Add an element to the Tree
     * @param element The element to add to the tree
     */
    public void addElement(T element) {
        Bytes encodedElement = codec.toBytes(element); // Use the codec to encode the element
        Bytes hashedElement = Bytes.wrap(digest.digest(encodedElement.toByteArray()));
        hashList.add(hashedElement);
        rebalanceTree();
    }

    /** Rebalance the binary tree of hashes, where each non-leaf node is the cryptographic hash of its child nodes.
     */
    private void rebalanceTree() {
        while (hashList.size() > 1) {
            if (hashList.size() % 2 != 0) {
                hashList.add(hashList.get(hashList.size() - 1)); // ensures that the number of nodes is always even
            }
            List<Bytes> newHashList = new ArrayList<>();
            // each iteration processes two consecutive nodes (parent and child in the tree structure).
            for (int i = 0; i < hashList.size(); i += 2) {
                Bytes x = hashList.get(i);
                Bytes y = hashList.get(i + 1);
                newHashList.add(
                        hash(x.append(y), digest)); // For each pair of nodes, appends y to x and hashes the result.
            }
            hashList = newHashList;
        }
    }

    /** Get the list of hashes in the tree
     * @return The list of hashes in the tree
     */
    @VisibleForTesting
    public List<Bytes> getHashList() {
        return hashList;
    }
    /** Hash the base bytes using the given message digest
     * @param base The base bytes to hash
     * @param digest The message digest to use
     * @return The hashed bytes
     */
    static Bytes hash(Bytes base, MessageDigest digest) {
        byte[] encodedHash = digest.digest(base.toByteArray());
        return Bytes.wrap(encodedHash);
    }

    /** Get the root hash of the tree
     * @return The root hash of the tree
     */
    Bytes getTreeRoot() {
        if (hashList.isEmpty()) {
            return null;
        }
        rebalanceTree();
        return hashList.get(0); // The last remaining element is the tree root
    }

    /** Get the root hash of the tree as a string
     * @return The root hash of the tree as a string
     */
    String getTreeRootAsString() {
        Bytes treeRoot = getTreeRoot();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < treeRoot.length(); i++) {
            byte b = treeRoot.getByte(i);
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
