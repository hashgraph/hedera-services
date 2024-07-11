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

package com.hedera.node.app.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.block.stream.BlockHeader;
import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.EventMetadata;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.block.stream.output.TransactionOutput;
import com.hedera.hapi.block.stream.output.TransactionResult;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.node.app.util.HashTreeManager;
import com.hedera.node.app.util.OutputMerkleTreeData;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.base.utility.Pair;
import com.swirlds.common.merkle.MerkleNode;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

class HashTreeManagerTest {

    private final HashTreeManager<String> hashTreeManager = new HashTreeManager<>(new SimpleStringCodec());
    private final String SHA_384_ROOT_HASH =
            "360526b48cb86c82e7af61c6a880afcfce4c738d02c4109338a44fe2994f1a8f3f9ee832693ad0f723be3b2777b97fcb";

    private final Map<BlockHeader, MerkleNode> inputRootsByBlock = new HashMap<>();
    private final Map<BlockHeader, MerkleNode> outputRootsByBlock = new HashMap<>();
    private final Set<Pair<MerkleNode, Bytes>> completedSubtreeHashes = new HashSet<>();

    @Test
    public void testHashTreeManager() {
        // Example elements to be processed
        List<String> elements = Arrays.asList("Event1", "Transaction1", "Transaction2", "Event2", "Transaction3");

        // Process the elements
        hashTreeManager.processNodes(elements);

        // Calculate and print the Merkle root hash
        Bytes merkleRootHash = hashTreeManager.calculateMerkleRootHash();
        System.out.println("Merkle Root Hash: " + merkleRootHash.toString());
        assertThat(merkleRootHash).isNotNull();
        assertThat(merkleRootHash.toString())
                .isEqualTo(
                        "49d156f8dda7219898d98d48a5bdc8df453c993890c7f1dbe2628361ff6fbb19553ad6258d0431691d20bd8b2cb0ab07");
    }

    @Test
    void testCalculateMerkleRoot() throws Exception {
        List<String> elements = Arrays.asList("0", "1", "2", "3", "4", "5", "6", "7");
        String rootHash = hashTreeManager.calculateMerkleRootOnLeafNodes(elements);
        assertThat(rootHash).isNotNull();
        assertThat(rootHash).isEqualTo(SHA_384_ROOT_HASH);
    }

    @Test
    void testCollisionResistance() {
        boolean foundCollision = false;
        // A collision occurs when two different inputs produce the same hash output.
        for (long i = 0L; i < 1000L; i++) {
            for (long j = 1000L; j < 2000L; j++) {
                String hash1 = hashTreeManager.sha384("input" + i).toString();
                String hash2 = hashTreeManager.sha384("input" + j).toString();
                if (hash1.equals(hash2)) {
                    foundCollision = true;
                    break;
                }
            }
            if (foundCollision) break;
        }
        assertThat(foundCollision).isFalse();
    }

    @Test
    void validateHashingWorksAsExpected() {
        boolean foundCollision = false;
        // Ensure the same inputs to the hash algorithm produce the same hash output.
        for (long i = 0L; i < 10000L; i++) {
            for (long j = 0L; j < 10000L; j++) {
                String hash1 = hashTreeManager.sha384("input" + i).toString();
                String hash2 = hashTreeManager.sha384("input" + j).toString();
                if (hash1.equals(hash2)) {
                    foundCollision = true;
                    break;
                }
            }
            if (foundCollision) break;
        }
        assertThat(foundCollision).isTrue().withFailMessage("A collision was found.");
    }

    BlockItem blockItem = BlockItem.newBuilder().build();
    Block block = Block.newBuilder().items().build();

    @Test
    public void testHashBlock() {
        var rootHash = testAcceptBlockItem(blockItem);
        assertThat(rootHash).isNotNull();
    }

    public Bytes testAcceptBlockItem(@NonNull final BlockItem blockItem) {
        MerkleNode outputRoot = null;
        TransactionOutput transactionOutput = null;
        TransactionResult transactionResult = null;
        StateChanges stateChanges = null;

        if (blockItem.hasHeader()) {
            // A header item signals the end of the previous block so we compute the block hash here
            // State Merkle Tree - may need to call `state.getRootHash()` on the latest `HederaState` here

            // 1. Previous Block Hash Merkle Tree
            Bytes previousBlockHash = block.items().getFirst().header().previousBlockProofHash();

        } else if (blockItem.hasTransaction()) {
            // 2. Input Merkle Tree
            MerkleNode inputRoot = inputRootsByBlock.get(block.items().getFirst());
            EventMetadata eventMetadata = block.items().get(1).startEventOrThrow();
            Transaction transaction = block.items().get(1).transactionOrThrow();

        } else if (blockItem.hasTransactionOutput() || blockItem.hasStateChanges()) {
            // 3. Output Merkle Tree
            outputRoot = outputRootsByBlock.get(block.items().getFirst());
            transactionOutput = block.items().get(1).transactionOutputOrThrow();
            transactionResult = block.items().get(1).transactionResultOrThrow();
            stateChanges = block.items().get(1).stateChangesOrThrow();

            // 4. State Merkle Tree
            // todo: manipulate a node from outputRootsByBlock
        }
        OutputMerkleTreeData outputMerkleTreeData =
                new OutputMerkleTreeData(outputRoot, transactionOutput, transactionResult, stateChanges);
        List<OutputMerkleTreeData> outputMerkleTreeDataList = new ArrayList<>();
        outputMerkleTreeDataList.add(outputMerkleTreeData);

        var rootHash = hashTreeManager.calculateMerkleRootOnOutputTree(outputMerkleTreeDataList);
        return Bytes.wrap(rootHash);
    }
}

class SimpleStringCodec implements Codec<String> {

    @NotNull
    @Override
    public String parse(@NotNull ReadableSequentialData readableSequentialData, boolean b, int i)
            throws ParseException {
        return null;
    }

    @Override
    public void write(@NotNull String s, @NotNull WritableSequentialData writableSequentialData) throws IOException {}

    @Override
    public int measure(@NotNull ReadableSequentialData readableSequentialData) throws ParseException {
        return 0;
    }

    @Override
    public int measureRecord(String s) {
        return 0;
    }

    @Override
    public boolean fastEquals(@NotNull String s, @NotNull ReadableSequentialData readableSequentialData)
            throws ParseException {
        return false;
    }
}
