/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.logic;

import static com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.stream.NonBlockingHandoff;
import com.hedera.services.stream.RecordStreamObject;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Publishes records to the Platform-managed record stream, using the {@link
 * com.swirlds.common.stream.StreamAligned} contract to maintain the invariant that a new record
 * file (i.e., block) starts <b>only</b> at a consensus timestamp that was the first assigned to a
 * user transaction. This invariant makes it easy to further guarantee that the block number
 * increases if and only if a new record file begins.
 *
 * <p>This means that all the child records for a user transaction, whether top-level or scheduled,
 * will be in the same record file. In particular, all child records for a contract transaction will
 * be in the same block as the record of the top-level transaction.
 *
 * <p>It also means that all system-created records from a single call to {@code handleTransaction}
 * will be in the same record file. (Although this is less important at this time.)
 */
@Singleton
public class RecordStreaming {
    private static final Logger log = LogManager.getLogger(RecordStreaming.class);
    public static final long PENDING_USER_TXN_BLOCK_NO = -1;

    private final BlockManager blockManager;
    private final RecordsHistorian recordsHistorian;
    private final NonBlockingHandoff nonBlockingHandoff;

    private long blockNo = PENDING_USER_TXN_BLOCK_NO;

    @Inject
    public RecordStreaming(
            final BlockManager blockManager,
            final RecordsHistorian recordsHistorian,
            final NonBlockingHandoff nonBlockingHandoff) {
        this.blockManager = blockManager;
        this.recordsHistorian = recordsHistorian;
        this.nonBlockingHandoff = nonBlockingHandoff;
    }

    /**
     * Resets the block number to reflect that it has not been updated as a side effect of streaming
     * user-generated records in this call to {@code handleTransaction}.
     *
     * <p>This is to handle the edge case that an internal error causes no user record to be
     * generated for a call to {@code handleTransaction}; we need to keep using the current block
     * number until the next call, so we preserve the invariant above that a new record file (i.e.,
     * block) starts <b>only</b> at a consensus timestamp that was the first assigned to a user
     * transaction.
     */
    public void resetBlockNo() {
        blockNo = PENDING_USER_TXN_BLOCK_NO;
    }

    /**
     * Streams all the records, both child and top-level, that resulted from handling the
     * transaction in the current {@link com.hedera.services.context.TransactionContext}.
     * <b>IMPORTANT:</b> this may have the side effect of increasing the block number.
     */
    public void streamUserTxnRecords() {
        blockNo =
                blockManager.updateAndGetAlignmentBlockNumber(
                        recordsHistorian.getTopLevelRecord().getTimestamp());
        if (recordsHistorian.hasPrecedingChildRecords()) {
            for (final var childRso : recordsHistorian.getPrecedingChildRecords()) {
                stream(childRso.withBlockNumber(blockNo));
            }
        }
        stream(recordsHistorian.getTopLevelRecord().withBlockNumber(blockNo));
        if (recordsHistorian.hasFollowingChildRecords()) {
            for (final var childRso : recordsHistorian.getFollowingChildRecords()) {
                stream(childRso.withBlockNumber(blockNo));
            }
        }
        blockManager.updateCurrentBlockHash(recordsHistorian.lastRunningHash());
    }

    /**
     * Streams a system record such as an auto-renewal or auto-removal record. If any user
     * transaction records were streamed in this call to {@code handleTransaction}, uses the same
     * {@link com.swirlds.common.stream.StreamAligned} block number as the last such record.
     * Otherwise, uses the current block number according to the {@link
     * BlockManager#getAlignmentBlockNumber()}.
     *
     * @param rso the system record to stream
     */
    public void streamSystemRecord(final RecordStreamObject rso) {
        if (blockNo != PENDING_USER_TXN_BLOCK_NO) {
            stream(rso.withBlockNumber(blockNo));
        } else {
            stream(rso.withBlockNumber(blockManager.getAlignmentBlockNumber()));
        }
        blockManager.updateCurrentBlockHash(rso.getRunningHash());
    }

    private void stream(final RecordStreamObject rso) {
        if (blockManager.shouldLogEveryTransaction()) {
            logTransaction(rso);
        }
        while (!nonBlockingHandoff.offer(rso)) {
            // Cannot proceed until we have handed off the record.
        }
    }

    private void logTransaction(final RecordStreamObject rso) {
        final var consTimestamp = rso.getTimestamp().toString();
        final var blockNumber = rso.getStreamAlignment();
        final var txId = rso.getTransactionRecord().getTransactionID();
        final var txIdString =
                txId.getAccountID().getShardNum()
                        + "."
                        + txId.getAccountID().getRealmNum()
                        + "."
                        + txId.getAccountID().getAccountNum()
                        + "-"
                        + txId.getTransactionValidStart().getSeconds()
                        + "-"
                        + txId.getTransactionValidStart().getNanos();
        final var status = rso.getTransactionRecord().getReceipt().getStatus().toString();
        var type = "UNRECOGNIZED";

        try {
            final var transaction = rso.getTransaction();
            final var txBody = extractTransactionBody(transaction);
            type = txBody.getDataCase().toString();
        } catch (InvalidProtocolBufferException e) {
            log.error("Couldn't get transaction body", e);
        }

        final var logString =
                "Consensus timestamp: {}, Block number: {}, Transaction ID: {}, Transaction"
                        + " type: {}, Status: {}";
        log.info(logString, consTimestamp, blockNumber, txIdString, type, status);
    }

    @VisibleForTesting
    long getBlockNo() {
        return blockNo;
    }
}
