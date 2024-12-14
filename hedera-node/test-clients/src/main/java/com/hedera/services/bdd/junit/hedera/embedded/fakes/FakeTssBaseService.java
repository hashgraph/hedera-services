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

package com.hedera.services.bdd.junit.hedera.embedded.fakes;

import static com.hedera.node.app.hapi.utils.CommonUtils.noThrowSha384HashOf;
import static java.util.Objects.requireNonNull;

import com.hedera.cryptography.tss.api.TssMessage;
import com.hedera.cryptography.tss.api.TssParticipantDirectory;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.node.app.services.ServiceMigrator;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.tss.TssBaseService;
import com.hedera.node.app.tss.TssBaseServiceImpl;
import com.hedera.node.app.tss.handlers.TssHandlers;
import com.hedera.node.app.tss.stores.ReadableTssStoreImpl;
import com.hedera.node.app.version.ServicesSoftwareVersion;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.bdd.junit.HapiTest;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.state.State;
import com.swirlds.state.lifecycle.SchemaRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ForkJoinPool;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A fake implementation of the {@link TssBaseService} that,
 * <ul>
 *     <Li>Lets the author of an embedded {@link HapiTest} control whether the TSS base service ignores
 *     signature requests; and, when requests are <i>not</i> ignored,</Li>
 *     <li>"Signs" messages by scheduling callback to its consumers using the SHA-384 hash of the
 *     message as the signature.</li>
 * </ul>
 * <b>Important:</b> this class is not thread-safe, and should only be used as a mock in repeatable mode
 * when specs are run one at a time.
 */
public class FakeTssBaseService implements TssBaseService {
    private static final Logger log = LogManager.getLogger(FakeTssBaseService.class);

    private final FakeTssLibrary tssLibrary = new FakeTssLibrary();
    private final TssBaseServiceImpl delegate;
    private final Queue<Runnable> pendingTssSubmission = new ArrayDeque<>();
    /**
     * Copy-on-write list to avoid concurrent modification exceptions if a consumer unregisters
     * itself in its callback.
     */
    private final List<BiConsumer<byte[], byte[]>> consumers = new CopyOnWriteArrayList<>();

    /**
     * The type of signing to perform.
     */
    public enum Signing {
        /**
         * If not ignoring requests, provide a fake signature by hashing the message.
         */
        FAKE,
        /**
         * Delegate to the real TSS base service.
         */
        DELEGATE
    }

    private Signing signing = Signing.DELEGATE;
    private boolean ignoreRequests = false;

    public FakeTssBaseService(@NonNull final AppContext appContext) {
        delegate = new TssBaseServiceImpl(
                appContext,
                ForkJoinPool.commonPool(),
                pendingTssSubmission::offer,
                tssLibrary,
                pendingTssSubmission::offer,
                new NoOpMetrics());
    }

    /**
     * Returns the fake TSS library.
     * @return the fake TSS library
     */
    public FakeTssLibrary fakeTssLibrary() {
        return tssLibrary;
    }

    /**
     * Returns whether there is a pending TSS submission.
     *
     * @return if the fake TSS base service has a pending TSS submission
     */
    public boolean hasTssSubmission() {
        return !pendingTssSubmission.isEmpty();
    }

    /**
     * Executes the pending TSS submission and clears it.
     */
    public void executeNextTssSubmission() {
        requireNonNull(pendingTssSubmission.poll()).run();
    }

    /**
     * When called, will start ignoring any requests for ledger signatures.
     */
    public void startIgnoringRequests() {
        ignoreRequests = true;
    }

    /**
     * When called, will stop ignoring any requests for ledger signatures.
     */
    public void stopIgnoringRequests() {
        ignoreRequests = false;
    }

    /**
     * Switches to using fake signatures.
     */
    public void useFakeSignatures() {
        signing = Signing.FAKE;
    }

    /**
     * Switches to using real signatures.
     */
    public void useRealSignatures() {
        signing = Signing.DELEGATE;
    }

    @Override
    public Status getStatus(
            @NonNull final Roster roster,
            @NonNull final Bytes ledgerId,
            @NonNull final ReadableTssStoreImpl tssBaseStore) {
        requireNonNull(roster);
        requireNonNull(ledgerId);
        requireNonNull(tssBaseStore);
        return delegate.getStatus(roster, ledgerId, tssBaseStore);
    }

    @Override
    public void bootstrapLedgerId(
            @NonNull final Roster roster,
            @NonNull final HandleContext context,
            @NonNull final Consumer<Bytes> ledgerIdConsumer) {
        requireNonNull(roster);
        requireNonNull(context);
        requireNonNull(ledgerIdConsumer);
        delegate.bootstrapLedgerId(roster, context, ledgerIdConsumer);
    }

    @Override
    public void requestLedgerSignature(
            @NonNull final byte[] messageHash, @NonNull final Instant lastUsedConsensusTime) {
        requireNonNull(messageHash);
        requireNonNull(lastUsedConsensusTime);
        switch (signing) {
            case FAKE -> {
                if (ignoreRequests) {
                    return;
                }
                final var mockSignature = noThrowSha384HashOf(messageHash);
                // Simulate asynchronous completion of the ledger signature
                CompletableFuture.runAsync(() -> consumers.forEach(consumer -> {
                    try {
                        consumer.accept(messageHash, mockSignature);
                    } catch (Exception e) {
                        log.error(
                                "Failed to provide signature {} on message {} to consumer {}",
                                CommonUtils.hex(mockSignature),
                                CommonUtils.hex(messageHash),
                                consumer,
                                e);
                    }
                }));
            }
            case DELEGATE -> delegate.requestLedgerSignature(messageHash, lastUsedConsensusTime);
        }
    }

    @Override
    public void setCandidateRoster(@NonNull final Roster roster, @NonNull final HandleContext context) {
        requireNonNull(roster);
        requireNonNull(context);
        delegate.setCandidateRoster(roster, context);
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        delegate.registerSchemas(registry);
    }

    @Override
    public void registerLedgerSignatureConsumer(@NonNull final BiConsumer<byte[], byte[]> consumer) {
        requireNonNull(consumer);
        consumers.add(consumer);
        delegate.registerLedgerSignatureConsumer(consumer);
    }

    @Override
    public void unregisterLedgerSignatureConsumer(@NonNull final BiConsumer<byte[], byte[]> consumer) {
        requireNonNull(consumer);
        consumers.remove(consumer);
        delegate.unregisterLedgerSignatureConsumer(consumer);
    }

    @Override
    public TssHandlers tssHandlers() {
        return delegate.tssHandlers();
    }

    @Override
    @NonNull
    public Roster chooseRosterForNetwork(
            @NonNull State state,
            @NonNull InitTrigger trigger,
            @NonNull ServiceMigrator serviceMigrator,
            @NonNull ServicesSoftwareVersion version,
            @NonNull final Configuration configuration,
            @NonNull final Roster overrideRoster) {
        return delegate.chooseRosterForNetwork(state, trigger, serviceMigrator, version, configuration, overrideRoster);
    }

    @Override
    public void regenerateKeyMaterial(@NonNull final State state) {
        delegate.regenerateKeyMaterial(state);
    }

    @Override
    public void generateParticipantDirectory(@NonNull final State state) {
        delegate.generateParticipantDirectory(state);
    }

    @Override
    public Bytes ledgerIdFrom(
            @NonNull final TssParticipantDirectory directory, @NonNull final List<TssMessage> tssMessages) {
        requireNonNull(directory);
        requireNonNull(tssMessages);
        return delegate.ledgerIdFrom(directory, tssMessages);
    }

    @Override
    public TssMessage getTssMessageFromBytes(Bytes wrap, TssParticipantDirectory directory) {
        return delegate.getTssMessageFromBytes(wrap, directory);
    }

    @Override
    public void manageTssStatus(final State state) {
        delegate.manageTssStatus(state);
    }
}
