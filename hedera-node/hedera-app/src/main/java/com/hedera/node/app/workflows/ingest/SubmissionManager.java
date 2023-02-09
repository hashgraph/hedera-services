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

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.transaction.UncheckedSubmitBody;
import com.hedera.node.app.service.mono.context.properties.NodeLocalProperties;
import com.hedera.node.app.service.mono.context.properties.Profile;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.RecordCache;
import com.hedera.node.app.workflows.onset.WorkflowOnset;
import com.hedera.pbj.runtime.io.BytesBuffer;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.system.Platform;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.ByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The {@code SubmissionManager} provides functionality to submit transactions to the platform. */
public class SubmissionManager {

    private static final Logger LOG = LoggerFactory.getLogger(SubmissionManager.class);
    private static final String PLATFORM_TXN_REJECTIONS_NAME = "platformTxnNotCreated/sec";
    private static final String PLATFORM_TXN_REJECTIONS_DESC =
            "number of platform transactions not created per second";
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
     * @param metrics {@link com.swirlds.common.metrics.Metrics} to use for metrics related to submissions
     */
    public SubmissionManager(
            @NonNull final Platform platform,
            @NonNull final RecordCache recordCache,
            @NonNull final NodeLocalProperties nodeLocalProperties,
            @NonNull final Metrics metrics) {
        this.platform = requireNonNull(platform);
        this.recordCache = requireNonNull(recordCache);
        this.nodeLocalProperties = requireNonNull(nodeLocalProperties);
        this.platformTxnRejections = metrics.getOrCreate(
                new SpeedometerMetric.Config("app", PLATFORM_TXN_REJECTIONS_NAME)
                        .withDescription(PLATFORM_TXN_REJECTIONS_DESC)
                        .withFormat(SPEEDOMETER_FORMAT)
                        .withHalfLife(nodeLocalProperties.statsSpeedometerHalfLifeSecs()));
    }

    /**
     * Submit a transaction to the {@link Platform}
     *
     * @param txBody the {@link TransactionBody} that should be submitted to the platform
     * @param byteArray the {@link ByteBuffer} of the data that should be submitted
     * @throws NullPointerException if one of the arguments is {@code null}
     * @throws PreCheckException if the transaction could not be submitted
     */
    public void submit(
            @NonNull final TransactionBody txBody,
            @NonNull final byte[] byteArray)
            throws PreCheckException {
        requireNonNull(txBody);
        requireNonNull(byteArray);

        byte[] payload = byteArray;

        // Unchecked submits are a mechanism to inject transaction to the system, that bypass all
        // pre-checks.This is used in tests to check the reaction to illegal input.
        final var optUncheckedSubmit = txBody.uncheckedSubmit();
        if (optUncheckedSubmit.isPresent()) {
            if (nodeLocalProperties.activeProfile() == Profile.PROD) {
                // we do not allow unchecked submits in PROD
                throw new PreCheckException(PLATFORM_TRANSACTION_NOT_CREATED);
            }
            final var uncheckedSubmit = optUncheckedSubmit.get();
            final var txBytes = uncheckedSubmit.transactionBytes();
            WorkflowOnset.parse(BytesBuffer.wrap(txBytes), UncheckedSubmitBody.PROTOBUF, PLATFORM_TRANSACTION_NOT_CREATED);
            payload = new byte[byteBuffer.limit()];
            txBytes.getBytes(0, payload);
        }

        final var success = platform.createTransaction(payload);
        if (success) {
            recordCache.addPreConsensus(txBody.transactionID());
        } else {
            platformTxnRejections.cycle();
            throw new PreCheckException(PLATFORM_TRANSACTION_NOT_CREATED);
        }
    }
}
