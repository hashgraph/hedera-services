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

package com.hedera.services.bdd.junit.support.validators.block;

import com.hedera.hapi.block.stream.Block;
import com.hedera.services.bdd.junit.support.BlockStreamValidator;
import com.hedera.services.bdd.spec.HapiSpec;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

public class BlockContentsValidator implements BlockStreamValidator {
    private static final Logger logger = LogManager.getLogger(BlockContentsValidator.class);

    public static final Factory FACTORY = new Factory() {
        @NonNull
        @Override
        public BlockStreamValidator create(@NonNull final HapiSpec spec) {
            return new BlockContentsValidator();
        }
    };

    @Override
    public void validateBlocks(@NonNull final List<Block> blocks) {
        final var firstBlock = blocks.get(0);
        validate(firstBlock, true);

        final var restBlocks = blocks.subList(1, blocks.size());
        for (final var block : restBlocks) {
            validate(block, false);
        }
    }

    private static void validate(Block block, boolean isFirst) {
        final var blockItems = block.items();

        // A block SHALL start with a `header`.
        if (!blockItems.getFirst().hasBlockHeader()) {
            Assertions.fail("Block does not start with a block header");
        }

        // A block SHALL end with a `state_proof`.
        if (!blockItems.getLast().hasBlockProof()) {
            Assertions.fail("Block does not end with a block proof");
        }

        // For the first block the `block_header` might be followed by `state_changes`, due to state migration.
        if (isFirst) {
            if (!blockItems.get(1).hasStateChanges()) {
                Assertions.fail("First block header not followed by state changes");
            }
            return;
        }

        // In general, a `block_header` SHALL be followed by an `event_header`.
        if (!blockItems.get(1).hasEventHeader()) {
            Assertions.fail("Block header not followed by an event header");
        }

        if (blockItems.size() == 5) { // block without a user transaction
            // A block with no user transactions contains a `block_header`, `event_header`,
            // 2 `state_changes` and `state_proof`.
            if (!blockItems.get(2).hasStateChanges() || !blockItems.get(3).hasStateChanges()) {
                Assertions.fail("Block with no user should contain at least 2 state changes");
            }

            return;
        }

        for (int i = 0; i < blockItems.size(); i++) {
            //  An `event_header` SHALL be followed by one or more `event_transaction` items.
            if (blockItems.get(i).hasEventHeader() && !blockItems.get(i + 1).hasEventTransaction()) {
                Assertions.fail("Event header not followed by an event transaction");
            }

            // An `event_transaction` SHALL be followed by a `transaction_result`.
            if (blockItems.get(i).hasEventTransaction()
                    && !blockItems.get(i + 1).hasTransactionResult()) {
                Assertions.fail("Event transaction not followed by a transaction result");
            }
        }
    }
}
