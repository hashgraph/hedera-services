/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.junit.support.validators;

import static com.hedera.node.app.hapi.utils.CommonUtils.functionOf;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.NONE;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.NodeStakeUpdate;
import static com.hederahashgraph.api.proto.java.TransactionBody.DataCase.NODE_STAKE_UPDATE;
import static java.util.Objects.requireNonNull;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.node.app.hapi.utils.CommonUtils;
import com.hedera.node.app.hapi.utils.exception.UnknownHederaFunctionality;
import com.hedera.services.bdd.junit.support.RecordStreamValidator;
import com.hedera.services.bdd.junit.support.RecordWithSidecars;
import com.hedera.services.bdd.suites.records.TransactionBodyValidation;
import com.hedera.services.stream.proto.RecordStreamItem;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This validator checks all the transactions submitted have {@link com.hederahashgraph.api.proto.java.TransactionBody}
 * set, by comparing checking if there are any NONE functionality transactions at the end of the CI test run:
 *
 * <p>It uses the {@link TransactionBodyValidation} suite to perform the queries.
 */
public class TransactionBodyValidator implements RecordStreamValidator {
    private static final Logger log = LogManager.getLogger(TransactionBodyValidator.class);

    private final TransactionBodyClassifier transactionBodyClassifier = new TransactionBodyClassifier();

    @Override
    public void validateRecordsAndSidecars(@NonNull final List<RecordWithSidecars> recordsWithSidecars) {
        requireNonNull(recordsWithSidecars);
        validateTransactionBody(recordsWithSidecars);
    }

    private void validateTransactionBody(final List<RecordWithSidecars> recordsWithSidecars) {
        String errorMsg = "Invalid TransactionBody type HederaFunctionality.NONE with record: {}";

        for (final var recordWithSidecars : recordsWithSidecars) {
            final var items = recordWithSidecars.recordFile().getRecordStreamItemsList();
            for (final var item : items) {
                try {
                    transactionBodyClassifier.incorporate(item);
                    if (transactionBodyClassifier.isInvalid()) {
                        log.error(errorMsg, item);
                        throw new IllegalStateException(errorMsg + item);
                    }
                } catch (InvalidProtocolBufferException e) {
                    log.error(errorMsg, e);
                    throw new IllegalStateException(errorMsg + item, e);
                }
            }
        }
    }

    public static class TransactionBodyClassifier {
        private final Set<HederaFunctionality> transactionType = new HashSet<>();

        public void incorporate(@NonNull final RecordStreamItem item) throws InvalidProtocolBufferException {
            requireNonNull(item);
            var txnType = NONE;
            TransactionBody txnBody = CommonUtils.extractTransactionBody(item.getTransaction());

            try {
                txnType = functionOf(txnBody);
            } catch (UnknownHederaFunctionality ex) {
                txnType = checkNodeStakeUpdate(txnBody);
            }
            transactionType.add(txnType);
        }

        private HederaFunctionality checkNodeStakeUpdate(final TransactionBody txn) {
            TransactionBody.DataCase dataCase = txn.getDataCase();
            return dataCase.equals(NODE_STAKE_UPDATE) ? NodeStakeUpdate : NONE;
        }

        public boolean isInvalid() {
            return transactionType.contains(NONE);
        }
    }
}
