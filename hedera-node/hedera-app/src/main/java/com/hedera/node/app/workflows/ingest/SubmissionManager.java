/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.ingest;

import static com.hedera.hapi.node.base.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.context.properties.NodeLocalProperties;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.spi.config.Profile;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.RecordCache;
import com.hedera.node.app.workflows.onset.WorkflowOnset;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.system.Platform;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The {@code SubmissionManager} provides functionality to submit transactions to the platform. */
@Singleton
public class SubmissionManager {

    private static final Logger LOG = LoggerFactory.getLogger(SubmissionManager.class);
    private static final String PLATFORM_TXN_REJECTIONS_NAME = "platformTxnNotCreated/sec";
    private static final String PLATFORM_TXN_REJECTIONS_DESC = "number of platform transactions not created per second";
    private static final String SPEEDOMETER_FORMAT = "%,13.2f";

    private final Platform platform;
    private final RecordCache recordCache;
    private final NodeLocalProperties nodeLocalProperties;
    private final SpeedometerMetric platformTxnRejections;

    /**
     * Constructor of {@code SubmissionManager}
     *
     * @param platform the {@link Platform} to which transactions will be submitted
     * @param recordCache the {@link RecordCache} that tracks submitted transactions
     * @param nodeLocalProperties the {@link NodeLocalProperties} that keep local properties
     * @param metrics metrics related to submissions
     */
    @Inject
    public SubmissionManager(
            @NonNull final Platform platform,
            @NonNull final RecordCache recordCache,
            @NonNull final NodeLocalProperties nodeLocalProperties,
            @NonNull final Metrics metrics) {
        this.platform = requireNonNull(platform);
        this.recordCache = requireNonNull(recordCache);
        this.nodeLocalProperties = requireNonNull(nodeLocalProperties);
        this.platformTxnRejections =
                metrics.getOrCreate(new SpeedometerMetric.Config("app", PLATFORM_TXN_REJECTIONS_NAME)
                        .withDescription(PLATFORM_TXN_REJECTIONS_DESC)
                        .withFormat(SPEEDOMETER_FORMAT)
                        .withHalfLife(nodeLocalProperties.statsSpeedometerHalfLifeSecs()));
    }

    /**
     * Submit a transaction to the {@link Platform}. If the transaction is an unchecked submit,
     * we ignored the given tx bytes and send in the other bytes.
     *
     * @param txBody the {@link TransactionBody} that should be submitted to the platform
     * @param txBytes the bytes of the data that should be submitted (the full transaction bytes
     *                as received from gRPC)
     * @throws NullPointerException if one of the arguments is {@code null}
     * @throws PreCheckException if the transaction could not be submitted
     */
    public void submit(@NonNull final TransactionBody txBody, @NonNull final byte[] txBytes) throws PreCheckException {
        requireNonNull(txBody);
        requireNonNull(txBytes);

        byte[] payload = txBytes;

        // Unchecked submits are a mechanism to inject transaction to the system, that bypass all
        // pre-checks. This is used in tests to check the reaction to illegal input.
        if (txBody.hasUncheckedSubmit()) {
            LOG.warn("Unchecked submit is not supported in this version of Hedera");
            if (nodeLocalProperties.activeProfile() == Profile.PROD) {
                // we do not allow unchecked submits in PROD
                throw new PreCheckException(PLATFORM_TRANSACTION_NOT_CREATED);
            }
            final var uncheckedSubmit = txBody.uncheckedSubmitOrThrow();
            final var uncheckedTxBytes = uncheckedSubmit.transactionBytes();
            WorkflowOnset.parse(
                    uncheckedTxBytes.toReadableSequentialData(),
                    Transaction.PROTOBUF,
                    PLATFORM_TRANSACTION_NOT_CREATED);
            payload = PbjConverter.asBytes(uncheckedTxBytes);
            uncheckedTxBytes.getBytes(0, payload);
        }

        final var success = platform.createTransaction(payload);
        if (success) {
            recordCache.addPreConsensus(txBody.transactionIDOrThrow());
        } else {
            platformTxnRejections.cycle();
            throw new PreCheckException(PLATFORM_TRANSACTION_NOT_CREATED);
        }
    }
}
