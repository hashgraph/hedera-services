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

import static com.hedera.hapi.node.base.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static com.hedera.node.app.spi.HapiUtils.TIMESTAMP_COMPARATOR;
import static com.hedera.node.app.spi.HapiUtils.asTimestamp;
import static com.hedera.node.app.spi.HapiUtils.isBefore;
import static com.hedera.node.app.spi.HapiUtils.minus;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.context.properties.NodeLocalProperties;
import com.hedera.node.app.service.mono.context.properties.Profile;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.system.Platform;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;
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
 * submitted and could end up sending a <strong>single</strong> duplicate transaction. If there is a poorly behaving
 * client and this node reboots, it will no longer know the transaction is a duplicate and will submit it, with the
 * node ending up having to pay for it. If we had a shutdown hook we could save this information off during graceful
 * shutdown and reload it on startup, but we don't have that hook yet, and anyway hard crashes would still impact the
 * node.
 *
 * <p>This cache is <strong>NOT</strong> impacted by falling behind or reconnecting, so the only time we will submit
 * duplicate transactions is if the node is restarted. We hope to improve this in the future.
 */
@Singleton
public class SubmissionManager {
    /** Metric settings for keeping track of rejected transactions */
    private static final String PLATFORM_TXN_REJECTIONS_NAME = "platformTxnNotCreated/sec";

    private static final String PLATFORM_TXN_REJECTIONS_DESC = "number of platform transactions not created per second";
    private static final String SPEEDOMETER_FORMAT = "%,13.2f";

    // FUTURE Consider adding a metric to keep track of the number of duplicate transactions submitted by users.

    /** The {@link Platform} to which transactions will be submitted */
    private final Platform platform;
    /** Whether this node is running in production mode. We hope to remove this logic in the future.  */
    private final boolean isProduction;
    /** Used for looking up the max transaction duration window. To be replaced by some new config object */
    private final GlobalDynamicProperties props;
    /** Metrics related to submissions */
    private final SpeedometerMetric platformTxnRejections;
    /**
     * The {@link TransactionID}s that this node has already submitted to the platform, sorted by transaction start
     * time, such that earlier start times come first. We guard this data structure within a synchronized block.
     */
    private final Set<TransactionID> submittedTxns = new TreeSet<>((t1, t2) ->
            TIMESTAMP_COMPARATOR.compare(t1.transactionValidStartOrThrow(), t2.transactionValidStartOrThrow()));

    /**
     * Create a new {@code SubmissionManager} instance.
     *
     * @param platform the {@link Platform} to which transactions will be submitted
     * @param props the {@link GlobalDynamicProperties} with the setting for max transaction duration
     * @param nodeLocalProperties the {@link NodeLocalProperties} that keep local properties
     * @param metrics             metrics related to submissions
     */
    @Inject
    public SubmissionManager(
            @NonNull final Platform platform,
            @NonNull final GlobalDynamicProperties props,
            @NonNull final NodeLocalProperties nodeLocalProperties,
            @NonNull final Metrics metrics) {
        this.platform = requireNonNull(platform);
        this.props = requireNonNull(props);
        this.isProduction = requireNonNull(nodeLocalProperties).activeProfile() == Profile.PROD;
        this.platformTxnRejections =
                metrics.getOrCreate(new SpeedometerMetric.Config("app", PLATFORM_TXN_REJECTIONS_NAME)
                        .withDescription(PLATFORM_TXN_REJECTIONS_DESC)
                        .withFormat(SPEEDOMETER_FORMAT)
                        .withHalfLife(nodeLocalProperties.statsSpeedometerHalfLifeSecs()));
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
            if (isProduction) {
                throw new PreCheckException(PLATFORM_TRANSACTION_NOT_CREATED);
            }

            // We allow it outside of prod, but it really shouldn't be used.
            payload = txBody.uncheckedSubmitOrThrow().transactionBytes();
        }

        // This method is not called at a super high rate, so synchronizing here is perfectly fine. We could have
        // made the data structure serializable, but that doesn't actually help much here because we need to check
        // for containment and then do a bunch of logic that might throw an exception before doing the `add`.
        synchronized (submittedTxns) {
            // We don't want to use another thread to prune the set, so we will take the opportunity here to do so.
            // Remember that at this point we have passed through all the throttles, so this method is only called
            // at most 10,000 / (Number of nodes) times per second, which is not a lot.
            removeExpiredTransactions();

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
            final var success = platform.createTransaction(PbjConverter.asBytes(payload));
            if (success) {
                submittedTxns.add(txId);
            } else {
                platformTxnRejections.cycle();
                throw new PreCheckException(PLATFORM_TRANSACTION_NOT_CREATED);
            }
        }
    }

    /**
     * Removes all expired {@link TransactionID}s from the cache. This method is not threadsafe and should only be
     * called from within a block synchronized on {@link #submittedTxns}.
     */
    private void removeExpiredTransactions() {
        final var itr = submittedTxns.iterator();
        // Compute the earliest valid start timestamp that is still within the max transaction duration window.
        final var now = asTimestamp(Instant.now());
        final var earliestValidState = minus(now, props.maxTxnDuration());

        // Loop in order and expunge every entry where the timestamp is before the current time.
        // Also remove the associated transaction from the submittedTxns set.
        while (itr.hasNext()) {
            final var txId = itr.next();
            // If the timestamp is before the current time, then it has expired
            if (isBefore(txId.transactionValidStartOrThrow(), earliestValidState)) {
                itr.remove();
            } else {
                return;
            }
        }
    }
}
