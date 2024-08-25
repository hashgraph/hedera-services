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

package com.hedera.services.bdd.junit.support.translators;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.services.bdd.junit.support.translators.inputs.BlockTransactionalUnit;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Defines a translator for a {@link BlockTransactionalUnit} into a list of {@link SingleTransactionRecord}s.
 */
public class BlockTransactionalUnitTranslator {
    private static final Logger log = LogManager.getLogger(BlockTransactionalUnitTranslator.class);

    /**
     * The translators used to translate the block transaction parts for a logical HAPI transaction.
     */
    private final Map<HederaFunctionality, BlockTransactionPartsTranslator> translators;

    public BlockTransactionalUnitTranslator(
            @NonNull final Map<HederaFunctionality, BlockTransactionPartsTranslator> translators) {
        this.translators = requireNonNull(translators);
    }

    /**
     * Translates the given {@link BlockTransactionalUnit} into a list of {@link SingleTransactionRecord}s.
     * @param unit the unit to translate
     * @return the translated records
     */
    public List<SingleTransactionRecord> translate(@NonNull final BlockTransactionalUnit unit) {
        requireNonNull(unit);
        final List<StateChange> remainingStateChanges = new LinkedList<>(unit.stateChanges());
        final List<SingleTransactionRecord> translatedRecords = new ArrayList<>();
        for (final var blockTransactionParts : unit.blockTransactionParts()) {
            final var translator = translators.get(blockTransactionParts.functionality());
            if (translator == null) {
                log.warn("No translator found for functionality {}, skipping", blockTransactionParts.functionality());
            } else {
                translatedRecords.add(translator.translate(blockTransactionParts, remainingStateChanges));
            }
        }
        return translatedRecords;
    }
}
