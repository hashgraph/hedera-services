// SPDX-License-Identifier: Apache-2.0
package com.hedera.services.bdd.junit.hedera.embedded.fakes;

import static com.hedera.node.app.history.schemas.V071HistorySchema.LEDGER_ID_STATE_ID;

import com.hedera.hapi.node.state.hints.HintsConstruction;
import com.hedera.hapi.node.state.history.ChainOfTrustProof;
import com.hedera.hapi.node.state.history.HistoryProof;
import com.hedera.hapi.node.state.history.HistoryProofConstruction;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.node.app.history.HistoryService;
import com.hedera.node.app.history.WritableHistoryStore;
import com.hedera.node.app.history.handlers.HistoryHandlers;
import com.hedera.node.app.history.impl.HistoryLibraryImpl;
import com.hedera.node.app.history.impl.HistoryServiceImpl;
import com.hedera.node.app.history.impl.OnProofFinished;
import com.hedera.node.app.service.roster.impl.ActiveRosters;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.TssConfig;
import com.hedera.node.internal.network.Network;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.lifecycle.SchemaRegistry;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.SortedMap;
import java.util.function.Supplier;
import org.hiero.consensus.fakes.noop.NoOpMetrics;

public class FakeHistoryService implements HistoryService {
    private final HistoryService delegate;
    private final Queue<Runnable> pendingHintsSubmissions = new ArrayDeque<>();

    public FakeHistoryService(
            @NonNull final AppContext appContext, @NonNull final Supplier<Network> genesisNetworkSupplier) {
        delegate = new HistoryServiceImpl(
                new NoOpMetrics(),
                pendingHintsSubmissions::offer,
                appContext,
                new HistoryLibraryImpl(),
                genesisNetworkSupplier);
    }

    @Override
    public boolean isReady() {
        return delegate.isReady();
    }

    @Override
    public void reconcile(
            @NonNull final ActiveRosters activeRosters,
            @Nullable final Bytes currentMetadata,
            @NonNull final WritableHistoryStore historyStore,
            @NonNull final Instant now,
            @NonNull final TssConfig tssConfig,
            final boolean isActive,
            @Nullable final HintsConstruction activeConstruction,
            final boolean freshGenesisRequested) {
        delegate.reconcile(
                activeRosters,
                currentMetadata,
                historyStore,
                now,
                tssConfig,
                isActive,
                activeConstruction,
                freshGenesisRequested);
    }

    @NonNull
    @Override
    public ChainOfTrustProof getCurrentChainOfTrustProof(@NonNull final Bytes metadata) {
        return delegate.getCurrentChainOfTrustProof(metadata);
    }

    /**
     * Sets up genesis like the real service, then sets the ledger id to the configured {@code ledger.id} if genesis left
     * it empty. Embedded networks use mock signatures, so the History service never finishes a genesis proof to set a
     * real ledger id, yet features such as CLPR require one in state. The configured id is what queries already report
     * as the ledger id when none is in state, so their responses do not change.
     */
    @Override
    public boolean doGenesisSetup(
            @NonNull final WritableStates writableStates, @NonNull final Configuration configuration) {
        final var result = delegate.doGenesisSetup(writableStates, configuration);
        final var ledgerId = writableStates.<ProtoBytes>getSingleton(LEDGER_ID_STATE_ID);
        final var current = ledgerId.get();
        if (current == null || current.value().length() == 0) {
            ledgerId.put(new ProtoBytes(
                    configuration.getConfigData(LedgerConfig.class).id()));
        }
        return result;
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        delegate.registerSchemas(registry);
    }

    @Override
    public HistoryHandlers handlers() {
        return delegate.handlers();
    }

    @Override
    public void onFinishedConstruction(@Nullable OnProofFinished cb) {
        delegate.onFinishedConstruction(cb);
    }

    @Override
    public void setLatestHistoryProof(@NonNull HistoryProof historyProof) {
        delegate.setLatestHistoryProof(historyProof);
    }

    @Override
    public void stop() {
        delegate.stop();
    }

    @Override
    public void onFinished(
            @NonNull final WritableHistoryStore historyStore,
            @NonNull final HistoryProofConstruction construction,
            @NonNull final SortedMap<Long, Long> targetNodeWeights) {
        delegate.onFinished(historyStore, construction, targetNodeWeights);
    }

    @Override
    public Bytes historyProofVerificationKey() {
        return delegate.historyProofVerificationKey();
    }
}
