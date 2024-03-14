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

package com.swirlds.platform.builder;

import static com.swirlds.common.io.utility.FileUtils.rethrowIO;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.platform.builder.StaticPlatformBuilder.doStaticSetup;
import static com.swirlds.platform.builder.StaticPlatformBuilder.getGlobalMetrics;
import static com.swirlds.platform.builder.StaticPlatformBuilder.getMetricsProvider;
import static com.swirlds.platform.crypto.CryptoStatic.initNodeSecurity;
import static com.swirlds.platform.gui.internal.BrowserWindowManager.getPlatforms;
import static com.swirlds.platform.util.BootstrapUtils.checkNodesToRun;
import static com.swirlds.platform.util.BootstrapUtils.detectSoftwareUpgrade;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.DefaultPlatformContext;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.CryptographyFactory;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.io.utility.RecycleBin;
import com.swirlds.common.io.utility.RecycleBinImpl;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.merkle.crypto.MerkleCryptography;
import com.swirlds.common.merkle.crypto.MerkleCryptographyFactory;
import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.SwirldsPlatform;
import com.swirlds.platform.config.BasicConfig;
import com.swirlds.platform.config.StateConfig;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.event.deduplication.EventDeduplicator;
import com.swirlds.platform.event.deduplication.StandardEventDeduplicator;
import com.swirlds.platform.gossip.DefaultIntakeEventCounter;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.gossip.NoOpIntakeEventCounter;
import com.swirlds.platform.gossip.sync.config.SyncConfig;
import com.swirlds.platform.internal.SignedStateLoadingException;
import com.swirlds.platform.recovery.EmergencyRecoveryManager;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.address.AddressBookInitializer;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.StartupStateUtils;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.util.MetricsDocUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Objects;

/**
 * TODO fix javadoc
 * <p>
 * Components that make up the platform. Individual components can be swapped out with alternative implementations using
 * this class. Any component not specified will be constructed using the default implementation and configuration.
 */
public class PlatformFactory {

    private final PlatformContext platformContext;
    private final KeysAndCerts keysAndCerts;
    private final RecycleBin recycleBin;
    private final NodeId selfId;
    private final String mainClassName;
    private final String swirldName;
    private final SoftwareVersion appVersion;
    private final EmergencyRecoveryManager emergencyRecoveryManager;
    private final ReservedSignedState initialState;

    private final IntakeEventCounter intakeEventCounter;

    private EventDeduplicator eventDeduplicator;

    // TODO javadoc
    PlatformFactory(@NonNull final PlatformBuilder builder) {

        // TODO: Give a detailed description of what is allowed to be constructed in this method

        selfId = builder.getSelfId();
        appVersion = builder.getAppVersion();
        swirldName = builder.getSwirldName();
        mainClassName = builder.getAppName(); // TODO verify correctness

        final Configuration configuration = builder.buildConfiguration();

        final Cryptography cryptography = CryptographyFactory.create(configuration);
        final MerkleCryptography merkleCryptography = MerkleCryptographyFactory.create(configuration, cryptography);

        // For backwards compatibility with the old static access pattern.
        CryptographyHolder.set(cryptography);
        MerkleCryptoFactory.set(merkleCryptography);

        final boolean firstTimeSetup = doStaticSetup(configuration, builder.getConfigPath());

        final AddressBook configAddressBook = builder.loadConfigAddressBook();

        checkNodesToRun(List.of(selfId));

        keysAndCerts = initNodeSecurity(configAddressBook, configuration).get(selfId);
        platformContext = new DefaultPlatformContext(
                configuration, getMetricsProvider().createPlatformMetrics(selfId), cryptography, Time.getCurrent());

        // the AddressBook is not changed after this point, so we calculate the hash now
        platformContext.getCryptography().digestSync(configAddressBook);

        recycleBin = rethrowIO(() -> new RecycleBinImpl(
                configuration, platformContext.getMetrics(), getStaticThreadManager(), Time.getCurrent(), selfId));

        final BasicConfig basicConfig = configuration.getConfigData(BasicConfig.class);
        final StateConfig stateConfig = configuration.getConfigData(StateConfig.class);
        emergencyRecoveryManager =
                new EmergencyRecoveryManager(stateConfig, basicConfig.getEmergencyRecoveryFileLoadDir());

        try {
            initialState = StartupStateUtils.getInitialState(
                    platformContext,
                    recycleBin,
                    appVersion,
                    builder.getGenesisStateBuilder(),
                    builder.getAppName(),
                    swirldName,
                    selfId,
                    configAddressBook,
                    emergencyRecoveryManager);
        } catch (final SignedStateLoadingException e) {
            throw new RuntimeException(e); // TODO
        }

        final boolean softwareUpgrade = detectSoftwareUpgrade(appVersion, initialState.get());

        // Initialize the address book from the configuration and platform saved state.
        final AddressBookInitializer addressBookInitializer = new AddressBookInitializer(
                selfId, appVersion, softwareUpgrade, initialState.get(), configAddressBook.copy(), platformContext);

        if (addressBookInitializer.hasAddressBookChanged()) {
            final State state = initialState.get().getState();
            // Update the address book with the current address book read from config.txt.
            // Eventually we will not do this, and only transactions will be capable of
            // modifying the address book.
            state.getPlatformState()
                    .setAddressBook(
                            addressBookInitializer.getCurrentAddressBook().copy());

            state.getPlatformState()
                    .setPreviousAddressBook(
                            addressBookInitializer.getPreviousAddressBook() == null
                                    ? null
                                    : addressBookInitializer
                                            .getPreviousAddressBook()
                                            .copy());
        }

        // At this point the initial state must have the current address book set.  If not, something is wrong.
        if (initialState.get().getState().getPlatformState().getAddressBook() == null) {
            throw new IllegalStateException("The current address book of the initial state is null.");
        }

        if (platformContext.getConfiguration().getConfigData(SyncConfig.class).waitForEventsInIntake()) {
            intakeEventCounter =
                    new DefaultIntakeEventCounter(initialState.get().getAddressBook());
        } else {
            intakeEventCounter = new NoOpIntakeEventCounter();
        }

        // TODO this may need to move
        if (firstTimeSetup) {
            MetricsDocUtils.writeMetricsDocumentToFile(getGlobalMetrics(), getPlatforms(), configuration);
            getMetricsProvider().start();
        }
    }

    @NonNull
    public Platform build() {
        // TODO add used field
        try (initialState) {
            return new SwirldsPlatform(this);
        }
    }

    @NonNull
    public PlatformContext getPlatformContext() {
        return platformContext;
    }

    @NonNull
    public KeysAndCerts getKeysAndCerts() {
        return keysAndCerts;
    }

    @NonNull
    public RecycleBin getRecycleBin() {
        return recycleBin;
    }

    @NonNull
    public NodeId getSelfId() {
        return selfId;
    }

    @NonNull
    public String getMainClassName() {
        return mainClassName;
    }

    @NonNull
    public String getSwirldName() {
        return swirldName;
    }

    @NonNull
    public SoftwareVersion getAppVersion() {
        return appVersion;
    }

    @NonNull
    public SignedState getInitialState() {
        return initialState.get();
    }

    @NonNull
    public EmergencyRecoveryManager getEmergencyRecoveryManager() {
        return emergencyRecoveryManager;
    }

    @NonNull
    public IntakeEventCounter getIntakeEventCounter() {
        return intakeEventCounter;
    }

    @NonNull
    public PlatformFactory withEventDeduplicator(@NonNull final EventDeduplicator eventDeduplicator) {
        this.eventDeduplicator = Objects.requireNonNull(eventDeduplicator);
        return this;
    }

    /**
     * Get the event deduplicator. Builds the event deduplicator if it has not been built or set.
     *
     * @return the event deduplicator
     */
    @NonNull
    public EventDeduplicator getEventDeduplicator() {
        if (eventDeduplicator == null) {
            eventDeduplicator = new StandardEventDeduplicator(platformContext, intakeEventCounter);
        }
        return eventDeduplicator;
    }

    // Rules:
    //
    // - something in the build context is NOT a component
    // - default component must be constructed ONLY with things in the build context
    // - the order of component construction should not matter
    // - all components must have an interface and a default implementation
    // - all components must be constructed by the PlatformComponentBuilder and accept custom implementations
    // - test implementations of components should not be on the classpath for production deployments
    // - it is ok to have multiple component implementations on the classpath if they are both production legal
    //   (i.e. with a config flag choosing which one to use)

}
