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

package com.hedera.services.bdd.junit.support.translators.impl;

import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CALL;
import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CREATE;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.services.bdd.junit.support.translators.BaseTranslator;
import com.hedera.services.bdd.junit.support.translators.inputs.BlockTransactionParts;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Provides utility methods for translators.
 */
public class TranslatorUtils {
    private static final Logger log = LogManager.getLogger(TranslatorUtils.class);

    private TranslatorUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Attempts to add an expected synthetic contract call result to a transaction record.
     * @param baseTranslator The base translator.
     * @param parts The transaction parts.
     * @param recordBuilder The transaction record builder.
     */
    public static void addSyntheticResultIfExpected(
            @NonNull final BaseTranslator baseTranslator,
            @NonNull final BlockTransactionParts parts,
            @NonNull final TransactionRecord.Builder recordBuilder) {
        requireNonNull(baseTranslator);
        requireNonNull(parts);
        requireNonNull(recordBuilder);
        if (baseTranslator.isFollowingChild(parts)
                && !parts.transactionIdOrThrow().scheduled()
                && !isInternalContractOp(parts)) {
            try {
                final var result = baseTranslator.nextSyntheticCallResult();
                recordBuilder.contractCallResult(result);
            } catch (Exception e) {
                log.error("Failed to add synthetic call result to transaction record for {}", parts.body(), e);
            }
        }
    }

    /**
     * Attempts to add an expected synthetic contract call result to a transaction record.
     * @param baseTranslator The base translator.
     * @param parts The transaction parts.
     * @param recordBuilder The transaction record builder.
     */
    public static void addScheduleRefIfExpected(
            @NonNull final BaseTranslator baseTranslator,
            @NonNull final BlockTransactionParts parts,
            @NonNull final TransactionRecord.Builder recordBuilder) {
        requireNonNull(baseTranslator);
        requireNonNull(parts);
        requireNonNull(recordBuilder);
        if (parts.transactionIdOrThrow().scheduled()) {
            try {
                recordBuilder.scheduleRef(baseTranslator.scheduleRefOrThrow());
            } catch (Exception e) {
                log.error("Failed to add schedule ref to transaction record for {}", parts.body(), e);
            }
        }
    }

    private static boolean isInternalContractOp(@NonNull final BlockTransactionParts parts) {
        final var function = parts.functionality();
        return function == CONTRACT_CALL || function == CONTRACT_CREATE;
    }
}
