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

package com.hedera.node.app.workflows.handle.flow;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.HederaRecordCache;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.state.HederaState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class SkipHandleProcess implements HandleProcess {

    private static final Logger logger = LogManager.getLogger(SkipHandleProcess.class);
    private final TransactionChecker transactionChecker;
    private final HederaRecordCache recordCache;
    private final ExchangeRateManager exchangeRateManager;

    @Inject
    public SkipHandleProcess(
            @NonNull final TransactionChecker transactionChecker,
            @NonNull final HederaRecordCache recordCache,
            @NonNull final ExchangeRateManager exchangeRateManager) {
        this.transactionChecker = transactionChecker;
        this.recordCache = recordCache;
        this.exchangeRateManager = exchangeRateManager;
    }

    @Override
    public void processUserTransaction(
            @NonNull final Instant consensusNow,
            @NonNull final HederaState state,
            @NonNull final PlatformState platformState,
            @NonNull final ConsensusEvent platformEvent,
            @NonNull final NodeInfo creator,
            @NonNull final ConsensusTransaction platformTxn,
            @NonNull final RecordListBuilder recordListBuilder) {
        // Reparse the transaction (so we don't need to get the prehandle result)
        final var recordBuilder = recordListBuilder.userTransactionRecordBuilder();
        final TransactionInfo transactionInfo;
        try {
            transactionInfo = transactionChecker.parseAndCheck(platformTxn.getApplicationPayload());
        } catch (PreCheckException e) {
            logger.error(
                    "Bad old transaction (version {}) from creator {}", platformEvent.getSoftwareVersion(), creator, e);
            // We don't care since we're checking a transaction with an older software version. We were going to
            // skip the transaction handling anyway
            return;
        }

        // Initialize record builder list
        recordBuilder
                .transaction(transactionInfo.transaction())
                .transactionBytes(transactionInfo.signedBytes())
                .transactionID(transactionInfo.transactionID())
                .exchangeRate(exchangeRateManager.exchangeRates())
                .memo(transactionInfo.txBody().memo());

        // Place a BUSY record in the cache
        final var record = recordBuilder.status(ResponseCodeEnum.BUSY).build();
        recordCache.add(creator.nodeId(), transactionInfo.payerID(), List.of(record));
    }
}
