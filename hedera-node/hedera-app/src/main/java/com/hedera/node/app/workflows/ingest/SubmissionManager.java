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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static java.util.Objects.requireNonNull;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Parser;
import com.hedera.node.app.service.mono.context.properties.NodeLocalProperties;
import com.hedera.node.app.service.mono.context.properties.Profile;
import com.hedera.node.app.service.mono.records.RecordCache;
import com.hedera.node.app.service.mono.stats.MiscSpeedometers;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.system.Platform;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.ByteBuffer;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The {@code SubmissionManager} provides functionality to submit transactions to the platform. */
@Singleton
public class SubmissionManager {

    private static final Logger LOG = LoggerFactory.getLogger(SubmissionManager.class);

    private final Platform platform;
    private final RecordCache recordCache;
    private final NodeLocalProperties nodeLocalProperties;
    private final MiscSpeedometers speedometers;

    /**
     * Constructor of {@code SubmissionManager}
     *
     * @param platform the {@link Platform} to which transactions will be submitted
     * @param recordCache the {@link RecordCache} that tracks submitted transactions
     * @param nodeLocalProperties the {@link NodeLocalProperties} that keep local properties
     * @param speedometers metrics related to submissions
     */
    @Inject
    public SubmissionManager(
            @NonNull final Platform platform,
            @NonNull final RecordCache recordCache,
            @NonNull final NodeLocalProperties nodeLocalProperties,
            @NonNull final MiscSpeedometers speedometers) {
        this.platform = requireNonNull(platform);
        this.recordCache = requireNonNull(recordCache);
        this.nodeLocalProperties = requireNonNull(nodeLocalProperties);
        this.speedometers = requireNonNull(speedometers);
    }

    /**
     * Submit a transaction to the {@link Platform}
     *
     * @param txBody the {@link TransactionBody} that should be submitted to the platform
     * @param byteArray the {@link ByteBuffer} of the data that should be submitted
     * @param parser the {@link Parser} that is used to eventually parse the {@link
     *     TransactionBody#getUncheckedSubmit()}
     * @throws NullPointerException if one of the arguments is {@code null}
     * @throws PreCheckException if the transaction could not be submitted
     */
    public void submit(
            @NonNull final TransactionBody txBody,
            @NonNull final byte[] byteArray,
            @NonNull final Parser<TransactionBody> parser)
            throws PreCheckException {
        requireNonNull(txBody);
        requireNonNull(byteArray);
        requireNonNull(parser);

        byte[] payload = byteArray;

        // Unchecked submits are a mechanism to inject transaction to the system, that bypass all
        // pre-checks.This is used in tests to check the reaction to illegal input.
        if (txBody.hasUncheckedSubmit()) {
            if (nodeLocalProperties.activeProfile() == Profile.PROD) {
                // we do not allow unchecked submits in PROD
                throw new PreCheckException(PLATFORM_TRANSACTION_NOT_CREATED);
            }
            try {
                payload =
                        parser.parseFrom(txBody.getUncheckedSubmit().getTransactionBytes())
                                .toByteArray();
            } catch (InvalidProtocolBufferException e) {
                LOG.warn("Transaction bytes from UncheckedSubmit not a valid gRPC transaction!", e);
                throw new PreCheckException(PLATFORM_TRANSACTION_NOT_CREATED);
            }
        }

        final var success = platform.createTransaction(payload);
        if (success) {
            recordCache.addPreConsensus(txBody.getTransactionID());
        } else {
            speedometers.cyclePlatformTxnRejections();
            throw new PreCheckException(PLATFORM_TRANSACTION_NOT_CREATED);
        }
    }
}
