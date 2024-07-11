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

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class HashTreeManager<T> {

    public HashTreeManager(Codec<T> codec) {
        this.codec = codec;
    }

    private final Codec<T> codec;
    List<Bytes> inputsCompleteSubtreeHashes;
    List<Bytes> outputsCompleteSubtreeHashes;
    private final List<Bytes> hashList = new ArrayList<>(); // Correctly defining and initializing hashList

    private enum Alternator {
        LEFT,
        RIGHT
    }

    Alternator currentAlternate = Alternator.LEFT;

    public void processNodes(List<T> elements) {
        for (T element : elements) {
            Bytes elementHash = hash(codec.toBytes(element));
            hashList.add(elementHash);

            if ((elements.indexOf(element) & 1) == 1) {
                Bytes yHash = hashList.remove(hashList.size() - 1);
                Bytes xHash = hashList.remove(hashList.size() - 1);
                Bytes combinedHash = hash(yHash.append(xHash));
                hashList.add(combinedHash);
            }
        }
    }

    public Bytes calculateMerkleRootHash() {
        Bytes merkleRootHash = hashList.get(hashList.size() - 1);
        for (int i = hashList.size() - 2; i >= 0; i--) {
            merkleRootHash = hash(hashList.get(i).append(merkleRootHash));
        }
        return merkleRootHash;
    }

    public String calculateMerkleRootOnLeafNodes(List<String> elements) {
        List<String> hashList = new ArrayList<>();

        // Initial hashing of elements
        for (String element : elements) {
            System.out.println(
                    "Writing element " + element + " to the stream, with Hash: " + sha384(element.toString()));
            hashList.add(sha384(element).toString());

            // Perform the double hashing operation
            int i = hashList.size() - 1;
            while (i % 2 != 0) {
                i = i / 2;
                String x = hashList.remove(i);
                String y = hashList.remove(i);
                System.out.println("Adding: " + i + "," + sha384(x + y));
                hashList.add(i, sha384(x + y).toString());
            }
        }

        // Calculate Merkle root hash
        String merkleRootHash = hashList.get(hashList.size() - 1);
        for (int i = hashList.size() - 2; i >= 0; i--) {
            merkleRootHash = sha384(hashList.get(i) + merkleRootHash).toString();
        }

        System.out.println(merkleRootHash);
        return merkleRootHash;
    }

    public void setHeader(Bytes bytes) {}
    ;

    public void acceptItem(T item) {
        boolean isForInputMerkleTree = isForInput(item);

        List<Bytes> listToOperateOn = isForInputMerkleTree ? inputsCompleteSubtreeHashes : outputsCompleteSubtreeHashes;

        // add the hash for the given block item
        var serializedItem = codec.toBytes(item);
        var hashedItem = hash(serializedItem);
        listToOperateOn.add(hashedItem);

        // if we're inserting a left node, do nothing; if right, compute the parent hash
        if (currentAlternate == Alternator.LEFT) {
            currentAlternate = Alternator.RIGHT;
        } else {
            var yHashToRemove = listToOperateOn.removeLast();
            var xHashToRemove = listToOperateOn.removeLast();
            var concatenated = xHashToRemove.append(yHashToRemove);
            var newParentHash = hash(concatenated);
            inputsCompleteSubtreeHashes.add(newParentHash);

            currentAlternate = Alternator.LEFT;
        }
    }

    public Bytes hash(Bytes bytes) {
        return sha384(String.valueOf(bytes));
    }

    public Bytes sha384(String base) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-384");
            byte[] encodedhash = digest.digest(base.getBytes(StandardCharsets.UTF_8));
            return Bytes.wrap(encodedhash);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public boolean isForInput(T item) {
        return false;
    }

    public void combineTreesAndComputeHash() {
        // Todo: part of "closing" the block, implement in separate ticket
    }

    /* This method calculates the Merkle root hash of a list of elements provided by Output Merkle Tree. */
    public String calculateMerkleRootOnOutputTree(List<OutputMerkleTreeData> elements) {
        List<String> hashList = new ArrayList<>();

        // Initial hashing of elements
        for (OutputMerkleTreeData element : elements) {
            System.out.println(
                    "Writing element " + element + " to the stream, with Hash: " + sha384(element.toString()));
            hashList.add(sha384(element.toString()).toString());

            // Perform the double hashing operation
            int i = hashList.size() - 1;
            while (i % 2 != 0) {
                i = i / 2;
                String x = hashList.remove(i);
                String y = hashList.remove(i);
                System.out.println("Adding: " + i + "," + sha384(x + y));
                hashList.add(i, sha384(x + y).toString());
            }
        }

        // Calculate Merkle root hash
        String merkleRootHash = hashList.get(hashList.size() - 1);
        for (int i = hashList.size() - 2; i >= 0; i--) {
            merkleRootHash = sha384(hashList.get(i) + merkleRootHash).toString();
        }

        System.out.println(merkleRootHash);
        return merkleRootHash;
    }

    private String bytesToHex(byte[] hash) {
        StringBuffer hexString = new StringBuffer();
        for (int i = 0; i < hash.length; i++) {
            String hex = Integer.toHexString(0xff & hash[i]);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
