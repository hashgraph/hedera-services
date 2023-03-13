/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.test;

import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import java.util.Random;

/**
 * Generates {@link Transaction}s using the provided randomization source.
 */
@FunctionalInterface
public interface TransactionGenerator {

    /**
     * Generate an array of transactions.
     *
     * @param random
     * 		source of randomness. May or may not be used depending on the implementation.
     * @return an array of transactions
     */
    ConsensusTransactionImpl[] generate(final Random random);
}
