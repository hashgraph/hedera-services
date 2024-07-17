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

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.block.stream.BlockHeader;
import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.EventMetadata;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.block.stream.output.TransactionOutput;
import com.hedera.hapi.block.stream.output.TransactionResult;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.merkle.MerkleNode;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

public class HashTreeManagerTest {

    private final BlockItem blockItem = BlockItem.newBuilder().build();
    private final Block block = Block.newBuilder().items().build();
    private final Map<BlockHeader, MerkleNode> inputRootsByBlock = new HashMap<>();
    private final Map<BlockHeader, MerkleNode> outputRootsByBlock = new HashMap<>();
    private final String SHA_384_ROOT_HASH =
            "1333c0960fbb9cac040834be1597b45571dae1bc14b3f78281c20dd147826f017ce4f4105d71ecf394bb41db424a637a";

    @Test
    public void testHashTreeManager() throws NoSuchAlgorithmException {
        HashTreeManager<String> tree = new HashTreeManager<>(new SimpleStringCodec());
        List<String> elements = Arrays.asList("Event1", "Transaction1", "Transaction2", "Event2", "Transaction3");
        tree.addElements(elements);

        // Calculate the root hash
        Bytes rootHash = tree.getTreeRoot();
        assertThat(rootHash).isNotNull();
        assertThat(rootHash.toString()).isEqualTo(SHA_384_ROOT_HASH);
    }

    @Test
    public void testIncrementalConstruction() throws Exception {
        HashTreeManager<String> tree = new HashTreeManager<>(new SimpleStringCodec());

        tree.addElement("leaf1");
        // A Merkle tree with a single leaf does have a root hash.
        assertThat(tree.getTreeRoot()).isNotNull();

        tree.addElement("leaf2");
        String rootHash = tree.getTreeRootAsString();
        assertThat(rootHash)
                .isEqualTo(tree.getTreeRootAsString()); // Root hash should remain consistent after balancing

        tree.addElement("leaf3");
        assertThat(tree.getTreeRoot()).isNotNull(); // A tree with three leaves still computes a root hash

        tree.addElement("leaf4");
        String newRootHash = tree.getTreeRootAsString();
        // Root hash should change as new leaves are added
        assertThat(rootHash).as("Checking that hashes are not equal").isNotEqualTo(newRootHash);
    }

    @Test
    public void testHashBlockItem() throws NoSuchAlgorithmException {
        final String expectedHash =
                "38b060a751ac96384cd9327eb1b1e36a21fdb71114be07434c0cc7bf63f6e1da274edebfe76f65fbd51ad2f14898b95b";
        var rootHash = testAcceptBlockItem(blockItem);
        assertThat(rootHash).isNotNull();
        assertThat(rootHash.toString()).isEqualTo(expectedHash);
    }

    private String testAcceptBlockItem(@NonNull final BlockItem blockItem) throws NoSuchAlgorithmException {
        // TODO: Not fully implemented yet
        MerkleNode outputRoot = null;
        TransactionOutput transactionOutput = null;
        TransactionResult transactionResult = null;
        StateChanges stateChanges = null;
        HashTreeManager<String> tree = new HashTreeManager<>(new SimpleStringCodec());
        tree.addElement(String.valueOf(blockItem));

        if (blockItem.hasHeader()) {
            // A header item signals the end of the previous block so we compute the block hash here
            // May need to call `state.getRootHash()` on the latest `HederaState` here for State Merkle Tree

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

        var rootHash = tree.getTreeRootAsString();
        return rootHash;
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
