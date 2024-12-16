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

package com.hedera.node.app.tss;

import static com.hedera.node.app.tss.handlers.TssUtils.SIGNATURE_SCHEMA;
import static com.hedera.node.app.tss.handlers.TssUtils.computeParticipantDirectory;
import static java.util.Objects.requireNonNull;

import com.hedera.cryptography.bls.BlsPublicKey;
import com.hedera.cryptography.tss.api.TssParticipantDirectory;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.app.tss.api.FakeGroupElement;
import com.hedera.node.config.data.TssConfig;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.state.service.ReadableRosterStore;
import com.swirlds.state.State;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.function.LongFunction;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Provides access to the {@link TssParticipantDirectory} for the active roster.
 */
@Singleton
public class TssDirectoryAccessor {
    private final Supplier<Configuration> configurationSupplier;

    /**
     * Non-final because it is lazy-initialized once state is available.
     */
    private TssParticipantDirectory tssParticipantDirectory;

    @Inject
    public TssDirectoryAccessor(@NonNull final AppContext appContext) {
        this.configurationSupplier = requireNonNull(appContext).configSupplier();
    }

    /**
     * Generates the participant directory for the active roster.
     * @param state state
     */
    public void generateTssParticipantDirectory(@NonNull final State state) {
        final var readableStoreFactory = new ReadableStoreFactory(state);
        final var rosterStore = readableStoreFactory.getStore(ReadableRosterStore.class);
        // TODO - use the real encryption keys from state
        final LongFunction<BlsPublicKey> encryptionKeyFn =
                nodeId -> new BlsPublicKey(new FakeGroupElement(BigInteger.valueOf(nodeId)), SIGNATURE_SCHEMA);
        activeParticipantDirectoryFrom(rosterStore, encryptionKeyFn);
    }

    /**
     * Returns the {@link TssParticipantDirectory} for the active roster in the given store.
     *
     * @param rosterStore the store from which to retrieve the active roster
     * @param encryptionKeyFn the function to get the TSS encryption keys
     * @return the {@link TssParticipantDirectory} for the active roster
     */
    public TssParticipantDirectory activeParticipantDirectoryFrom(
            @NonNull final ReadableRosterStore rosterStore, @NonNull final LongFunction<BlsPublicKey> encryptionKeyFn) {
        // Since the active roster can only change when restarting the JVM, we only compute it once
        // per instantiation of this singleton accessor
        if (tssParticipantDirectory != null) {
            return tssParticipantDirectory;
        }
        final var activeRoster = requireNonNull(rosterStore.getActiveRoster());
        final var maxSharesPerNode =
                configurationSupplier.get().getConfigData(TssConfig.class).maxSharesPerNode();
        tssParticipantDirectory = computeParticipantDirectory(activeRoster, maxSharesPerNode, encryptionKeyFn);
        return tssParticipantDirectory;
    }

    /**
     * Returns the {@link TssParticipantDirectory} for the active roster.
     * @return the {@link TssParticipantDirectory} for the active roster
     * @throws NullPointerException if the participant directory has not been generated
     */
    public @NonNull TssParticipantDirectory activeParticipantDirectoryOrThrow() {
        return requireNonNull(tssParticipantDirectory);
    }
}
