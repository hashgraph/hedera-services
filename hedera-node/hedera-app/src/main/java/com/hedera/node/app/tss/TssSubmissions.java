// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.tss;

import static java.time.temporal.ChronoUnit.SECONDS;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.NetworkAdminConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Base class for submitting node transactions to the network within an application context using a given executor.
 */
public class TssSubmissions {
    private final Executor executor;
    private final AppContext appContext;

    public TssSubmissions(@NonNull final Executor executor, @NonNull final AppContext appContext) {
        this.executor = requireNonNull(executor);
        this.appContext = requireNonNull(appContext);
    }

    /**
     * Attempts to submit a transaction to the network, retrying based on the given configuration.
     * <p>
     * Returns a future that completes when the transaction has been submitted; or completes exceptionally
     * if the transaction could not be submitted after the configured number of retries.
     *
     * @param spec the spec to build the transaction to submit
     * @param onFailure a consumer to call if the transaction fails to submit
     * @return a future that completes when the transaction has been submitted, exceptionally if it was not
     */
    protected CompletableFuture<Void> submit(
            @NonNull final Consumer<TransactionBody.Builder> spec,
            @NonNull final BiConsumer<TransactionBody, String> onFailure) {
        final var selfId = appContext.selfNodeInfoSupplier().get().accountId();
        final var consensusNow = appContext.instantSource().instant();
        final var config = appContext.configSupplier().get();
        final var adminConfig = config.getConfigData(NetworkAdminConfig.class);
        final var hederaConfig = config.getConfigData(HederaConfig.class);
        return appContext
                .gossip()
                .submitFuture(
                        selfId,
                        consensusNow,
                        Duration.of(hederaConfig.transactionMaxValidDuration(), SECONDS),
                        spec,
                        executor,
                        adminConfig.timesToTrySubmission(),
                        adminConfig.distinctTxnIdsToTry(),
                        adminConfig.retryDelay(),
                        onFailure);
    }
}
