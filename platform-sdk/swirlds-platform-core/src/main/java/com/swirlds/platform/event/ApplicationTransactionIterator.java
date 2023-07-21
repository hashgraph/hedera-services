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

package com.swirlds.platform.event;

import com.swirlds.common.system.transaction.Transaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * An iterator that walks over application transactions in an array of transactions that also contain system
 * transactions.
 */
public class ApplicationTransactionIterator implements Iterator<Transaction> { // TODO test this

    private final Transaction[] transactions;
    private int nextIndex = 0;

    public ApplicationTransactionIterator(@NonNull final Transaction[] transactions) {
        this.transactions = Objects.requireNonNull(transactions);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasNext() {
        while (nextIndex < transactions.length && !transactions[nextIndex].isSystem()) {
            nextIndex++;
        }

        return nextIndex < transactions.length && transactions[nextIndex].isSystem();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Transaction next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        return transactions[nextIndex++];
    }
}
