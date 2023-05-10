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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.annotations.NodeSelfId;
import com.hedera.node.app.service.mono.context.properties.NodeLocalProperties;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.spi.config.Profile;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.RecordCache;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.system.Platform;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/** The {@code SubmissionManager} provides functionality to submit transactions to the platform. */
@Singleton
public class SubmissionManager {
    private static final String PLATFORM_TXN_REJECTIONS_NAME = "platformTxnNotCreated/sec";
    private static final String PLATFORM_TXN_REJECTIONS_DESC = "number of platform transactions not created per second";
    private static final String SPEEDOMETER_FORMAT = "%,13.2f";

    private final Platform platform;
    private final RecordCache recordCache;
    private final boolean isProduction;
    private final SpeedometerMetric platformTxnRejections;
    private final AccountID nodeSelfID;

    /**
     * Constructor of {@code SubmissionManager}
     *
     * @param nodeSelfID the {@link AccountID} for referring to this node's operator account'
     * @param platform the {@link Platform} to which transactions will be submitted
     * @param recordCache the {@link RecordCache} that tracks submitted transactions
     * @param nodeLocalProperties the {@link NodeLocalProperties} that keep local properties
     * @param metrics metrics related to submissions
     */
    @Inject
    public SubmissionManager(
            @NodeSelfId @NonNull final AccountID nodeSelfID,
            @NonNull final Platform platform,
            @NonNull final RecordCache recordCache,
            @NonNull final NodeLocalProperties nodeLocalProperties,
            @NonNull final Metrics metrics) {
        this.nodeSelfID = requireNonNull(nodeSelfID);
        this.platform = requireNonNull(platform);
        this.recordCache = requireNonNull(recordCache);
        this.isProduction = requireNonNull(nodeLocalProperties).activeProfile() == Profile.PROD;
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
    public void submit(@NonNull final TransactionBody txBody, @NonNull final Bytes txBytes) throws PreCheckException {
        requireNonNull(txBody);
        requireNonNull(txBytes);

        Bytes payload = txBytes;

        // Unchecked submits are a mechanism to inject transaction to the system, that bypass all
        // pre-checks. This is used in tests to check the reaction to illegal input.
        // FUTURE This should be deprecated and removed. We do not want this in our production system.
        if (txBody.hasUncheckedSubmit()) {
            // We do NOT allow this call in production!
            if (isProduction) {
                throw new PreCheckException(PLATFORM_TRANSACTION_NOT_CREATED);
            }

            // We allow it outside of prod, but it really shouldn't be used.
            payload = txBody.uncheckedSubmitOrThrow().transactionBytes();
        }

        // An honest node does not want to submit duplicate transactions (since it will be charged for it), so we will
        // check whether it has been submitted already. It is still possible under high concurrency that a duplicate
        // transaction could be submitted, but doing this check here makes it much less likely.
        final var txId = txBody.transactionIDOrThrow();
        if (recordCache.get(txId) == null) {
            final var success = platform.createTransaction(PbjConverter.asBytes(payload));
            if (success) {
                recordCache.put(txBody.transactionIDOrThrow(), nodeSelfID);
            } else {
                platformTxnRejections.cycle();
                throw new PreCheckException(PLATFORM_TRANSACTION_NOT_CREATED);
            }
        }
    }
}
