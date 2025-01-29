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

package com.hedera.node.app.workflows.ingest;

import static com.hedera.hapi.node.base.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.DeduplicationCache;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.StatsConfig;
import com.hedera.node.config.types.Profile;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.system.Platform;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * The {@code SubmissionManager} submits transactions to the platform. As this is an honest node, it makes a strong
 * attempt to <strong>avoid</strong> submitting duplicate transactions to the platform.
 *
 * <p>An honest node does not want to submit duplicate transactions (since it will be charged for it). We use an
 * in-memory cache to keep track of transactions that have been submitted, sorted by transaction start time. This cache
 * is not part of state, because it will be different for different nodes.
 *
 * <p>Because this cache is not in state, if this node is restarted, it will forget about transactions it has already
 * submitted and could end up sending a <strong>single</strong> duplicate transaction per transaction. If there is a
 * poorly behaving client and this node reboots, it will no longer know the transaction is a duplicate and will submit
 * it, with the node ending up having to pay for it. If we had a shutdown hook we could save this information off during
 * graceful shutdown and reload it on startup, but we don't have that hook yet, and anyway hard crashes would still
 * impact the node.
 *
 * <p>In addition to tracking transaction IDs submitted <strong>by this node</strong>, the submitted transaction cache
 * will also contain entries for transactions submitted to other nodes, once this node sees those transactions in
 * pre-handle. If a user submits a transaction to 10 nodes, it is possible that none of the 10 nodes will recognize the
 * transaction as a duplicate and will submit all 10 to consensus, causing the user to pay for 1 full transaction and
 * 9 node+network fees. It is also possible that some of those nodes will learn by gossip about a transaction before
 * the user transaction hits the {@link SubmissionManager}, in which case those transactions will fail with
 * {@link com.hedera.hapi.node.base.ResponseCodeEnum#DUPLICATE_TRANSACTION} and the user will only pay for those
 * transactions which were not detected as duplicates during submission.
 *
 * <p>This cache is <strong>NOT</strong> impacted by falling behind or reconnecting, so the only time we will submit
 * duplicate transactions is if the node is restarted. We hope to improve this in the future.
 */
@Singleton
public class SubmissionManager {
    /** Metric settings for keeping track of rejected transactions */
    private static final String PLATFORM_TXN_REJECTIONS_NAME = "platformTxnNotCreated_per_sec";

    private static final String PLATFORM_TXN_REJECTIONS_DESC = "number of platform transactions not created per second";
    private static final String SPEEDOMETER_FORMAT = "%,13.2f";
    private static final Bytes MAIN_NET_LEDGER_ID = Bytes.fromHex("00");
    private static final Bytes TEST_NET_LEDGER_ID = Bytes.fromHex("01");
    private static final Bytes PREVIEW_NET_LEDGER_ID = Bytes.fromHex("02");

    // FUTURE Consider adding a metric to keep track of the number of duplicate transactions submitted by users.

    /** The {@link Platform} to which transactions will be submitted */
    private final Platform platform;

    /** Metrics related to submissions */
    private final SpeedometerMetric platformTxnRejections;
    /** The {@link DeduplicationCache} that keeps track of transactions that have been submitted */
    private final DeduplicationCache submittedTxns;

    private final ConfigProvider configProvider;

    /**
     * Create a new {@code SubmissionManager} instance.
     *
     * @param platform the {@link Platform} to which transactions will be submitted
     * @param deduplicationCache used to prevent submission of duplicate transactions
     * @param configProvider the {@link ConfigProvider}
     * @param metrics             metrics related to submissions
     */
    @Inject
    public SubmissionManager(
            @NonNull final Platform platform,
            @NonNull final DeduplicationCache deduplicationCache,
            @NonNull final ConfigProvider configProvider,
            @NonNull final Metrics metrics) {
        this.platform = requireNonNull(platform);
        this.submittedTxns = requireNonNull(deduplicationCache);
        this.configProvider = requireNonNull(configProvider);

        final var statsConfig = configProvider.getConfiguration().getConfigData(StatsConfig.class);
        this.platformTxnRejections =
                metrics.getOrCreate(new SpeedometerMetric.Config("app", PLATFORM_TXN_REJECTIONS_NAME)
                        .withDescription(PLATFORM_TXN_REJECTIONS_DESC)
                        .withFormat(SPEEDOMETER_FORMAT)
                        .withHalfLife(statsConfig.speedometerHalfLifeSecs()));
    }

    /**
     * Submit a transaction to the {@link Platform}. If the transaction is an unchecked submit, we ignored the given tx
     * bytes and send in the other bytes.
     *
     * @param txBody  the {@link TransactionBody} that should be submitted to the platform
     * @param txBytes the bytes of the data that should be submitted (the full transaction bytes as received from gRPC)
     * @throws NullPointerException if one of the arguments is {@code null}
     * @throws PreCheckException    if the transaction could not be submitted
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
            // check profile dynamically, this way we allow profile overriding in Hapi tests
            final var configuration = configProvider.getConfiguration();
            final var hederaConfig = configuration.getConfigData(HederaConfig.class);
            final var ledgerConfig = configuration.getConfigData(LedgerConfig.class);
            if (hederaConfig.activeProfile() == Profile.PROD
                    || MAIN_NET_LEDGER_ID.equals(ledgerConfig.id())
                    || TEST_NET_LEDGER_ID.equals(ledgerConfig.id())
                    || PREVIEW_NET_LEDGER_ID.equals(ledgerConfig.id())) {
                throw new PreCheckException(PLATFORM_TRANSACTION_NOT_CREATED);
            }

            // We allow it outside of prod, but it really shouldn't be used.
            payload = txBody.uncheckedSubmitOrThrow().transactionBytes();
        }

        // This method is not called at a super high rate, so synchronizing here is perfectly fine. We need to check
        // for containment and then do a bunch of logic that might throw an exception before doing the `add` and we
        // want to be REALLY SURE that we're not submitting duplicate transactions to the network.
        synchronized (submittedTxns) {
            // If we have already submitted this transaction, then fail. Note that both of these calls will throw if
            // the transaction is malformed. This should NEVER happen, because the transaction was already checked
            // before we got here. But if it ever does happen, for any reason, we want it to happen BEFORE we submit,
            // and BEFORE we record the transaction as a duplicate.
            final var txId = txBody.transactionIDOrThrow();
            if (submittedTxns.contains(txId)) {
                throw new PreCheckException(DUPLICATE_TRANSACTION);
            }

            // This call to submit to the platform should almost always work. Maybe under extreme load it will fail,
            // or while the system is being shut down. In any event, the user will receive an error code indicating
            // that the transaction was not submitted and they can retry.
            final var success = platform.createTransaction(payload.toByteArray());
            if (success) {
                submittedTxns.add(txId);
            } else {
                platformTxnRejections.cycle();
                throw new PreCheckException(PLATFORM_TRANSACTION_NOT_CREATED);
            }
        }
    }
}
