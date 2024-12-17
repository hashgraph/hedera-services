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

import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.services.bdd.junit.support.translators.inputs.BlockTransactionParts;
import com.hedera.services.bdd.junit.support.translators.inputs.BlockTransactionalUnit;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * Defines a functional interface for translating a block transaction and the remaining state changes from its origin
 * {@link BlockTransactionalUnit} into a {@link SingleTransactionRecord}.
 */
@FunctionalInterface
public interface BlockTransactionPartsTranslator {
    /**
     * Translate the given block transaction parts into a single transaction record. The remaining state changes are
     * provided to allow for the translation of the record to include the state changes that were not part of the
     * transaction.
     * <p>
     * If the translator needs a state change with a particular {@link StateChange#stateId()} to complete its
     * translation it <b>must</b> follow two principles:
     * <ol>
     *     <li>It must use the first occurrence of that state change from the given list.</li>
     *     <li>It must remove that state change from the list before returning the translated record.</li>
     * </ol>
     *
     * @param parts the parts of the transaction
     * @param baseTranslator the base translator
     * @param remainingStateChanges the state changes remaining to be processed
     * @return the translated record
     */
    SingleTransactionRecord translate(
            @NonNull BlockTransactionParts parts,
            @NonNull BaseTranslator baseTranslator,
            @NonNull List<StateChange> remainingStateChanges);
}
