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

import static com.swirlds.platform.builder.internal.StaticPlatformBuilder.getGlobalMetrics;
import static com.swirlds.platform.builder.internal.StaticPlatformBuilder.getMetricsProvider;
import static com.swirlds.platform.gui.internal.BrowserWindowManager.getPlatforms;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.io.utility.RecycleBin;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.SwirldsPlatform;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.recovery.EmergencyRecoveryManager;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.util.MetricsDocUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The advanced platform builder is responsible for constructing platform components. This class is exposed so that
 * individual components can be replaced with alternate implementations.
 */
public class PlatformComponentBuilder {

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Final variables defined here are constructed before all components are constructed. It is ok to pass these
    // variables into component constructors.

    private final PlatformContext platformContext;
    final KeysAndCerts keysAndCerts;
    final RecycleBin recycleBin;
    final NodeId selfId;
    final String mainClassName;
    final String swirldName;
    final SoftwareVersion appVersion;
    final ReservedSignedState initialState;
    final EmergencyRecoveryManager emergencyRecoveryManager;
    final Consumer<GossipEvent> preconsensusEventConsumer; // TODO create a record for these
    final Consumer<ConsensusSnapshot> snapshotOverrideConsumer;
    final boolean firstPlatform;

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // All non-final variables defined here are components. In general, default components should not be passed to
    // other components as constructor arguments, and it should be legal to construct components in any order.

    // Future work: move all components here

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // All other variables should be defined below this line.

    /**
     * False if this builder has not yet been used to build a platform (or platform component builder), true if it has.
     */
    private boolean used;

    /**
     * Constructor.
     *
     * @param platformContext           the context for this platform
     * @param keysAndCerts              an object holding all the public/private key pairs and the CSPRNG state for this
     *                                  member
     * @param recycleBin                used to delete files that may be useful for later debugging
     * @param selfId                    the ID for this node
     * @param mainClassName             the name of the app class inheriting from SwirldMain
     * @param swirldName                the name of the swirld being run
     * @param appVersion                the current version of the running application
     * @param initialState              the initial state of the platform
     * @param emergencyRecoveryManager  used in emergency recovery.
     * @param preconsensusEventConsumer the consumer for preconsensus events, null if publishing this data has not been
     *                                  enabled
     * @param snapshotOverrideConsumer  the consumer for snapshot overrides, null if publishing this data has not been
     *                                  enabled
     * @param firstPlatform             if this is the first platform being built (there is static setup that needs to
     *                                  be done, long term plan is to stop using static variables)
     */
    public PlatformComponentBuilder(
            @NonNull final PlatformContext platformContext,
            @NonNull final KeysAndCerts keysAndCerts,
            @NonNull final RecycleBin recycleBin,
            @NonNull final NodeId selfId,
            @NonNull final String mainClassName,
            @NonNull final String swirldName,
            @NonNull final SoftwareVersion appVersion,
            @NonNull final ReservedSignedState initialState,
            @NonNull final EmergencyRecoveryManager emergencyRecoveryManager,
            @Nullable final Consumer<GossipEvent> preconsensusEventConsumer,
            @Nullable final Consumer<ConsensusSnapshot> snapshotOverrideConsumer,
            final boolean firstPlatform) {

        this.platformContext = Objects.requireNonNull(platformContext);
        this.keysAndCerts = Objects.requireNonNull(keysAndCerts);
        this.recycleBin = Objects.requireNonNull(recycleBin);
        this.selfId = Objects.requireNonNull(selfId);
        this.mainClassName = Objects.requireNonNull(mainClassName);
        this.swirldName = Objects.requireNonNull(swirldName);
        this.appVersion = Objects.requireNonNull(appVersion);
        this.initialState = Objects.requireNonNull(initialState);
        this.emergencyRecoveryManager = Objects.requireNonNull(emergencyRecoveryManager);
        this.preconsensusEventConsumer = preconsensusEventConsumer;
        this.snapshotOverrideConsumer = snapshotOverrideConsumer;
        this.firstPlatform = firstPlatform;
    }

    /**
     * Get the platform context.
     *
     * @return the platform context
     */
    @NonNull
    public PlatformContext getPlatformContext() {
        return platformContext;
    }

    /**
     * Get the keys and certs. Used to sign and verify signatures.
     *
     * @return the keys and certs
     */
    @NonNull
    public KeysAndCerts getKeysAndCerts() {
        return keysAndCerts;
    }

    /**
     * Get the recycle bin. Used to "delete" files that may be useful for later debugging.
     *
     * @return the recycle bin
     */
    @NonNull
    public RecycleBin getRecycleBin() {
        return recycleBin;
    }

    /**
     * Get the ID for this node.
     *
     * @return the ID for this node
     */
    @NonNull
    public NodeId getSelfId() {
        return selfId;
    }

    /**
     * Get the name of the app class inheriting from SwirldMain.
     *
     * @return the name of the app class inheriting from SwirldMain
     */
    @NonNull
    public String getMainClassName() {
        return mainClassName;
    }

    /**
     * Get the name of the swirld being run.
     *
     * @return the name of the swirld being run
     */
    @NonNull
    public String getSwirldName() {
        return swirldName;
    }

    /**
     * Get the current version of the running application.
     *
     * @return the current version of the running application
     */
    @NonNull
    public SoftwareVersion getAppVersion() {
        return appVersion;
    }

    /**
     * Get the initial state of the platform. This object holds a reference count for this state until the platform has
     * been built.
     *
     * @return the initial state of the platform
     */
    @NonNull
    public SignedState getInitialState() {
        return initialState.get();
    }

    /**
     * Get the emergency recovery manager.
     *
     * @return the emergency recovery manager
     */
    @NonNull
    public EmergencyRecoveryManager getEmergencyRecoveryManager() {
        return emergencyRecoveryManager;
    }

    /**
     * Get the consumer for preconsensus events. Ignored if null.
     *
     * @return the consumer for preconsensus events
     */
    @Nullable
    public Consumer<GossipEvent> getPreconsensusEventConsumer() {
        return preconsensusEventConsumer;
    }

    /**
     * Get the consumer for snapshot overrides. Ignored if null.
     *
     * @return the consumer for snapshot overrides
     */
    @Nullable
    public Consumer<ConsensusSnapshot> getSnapshotOverrideConsumer() {
        return snapshotOverrideConsumer;
    }

    /**
     * Throw an exception if this builder has been used to build a platform or a platform factory.
     */
    private void throwIfAlreadyUsed() {
        if (used) {
            throw new IllegalStateException("PlatformBuilder has already been used");
        }
    }

    /**
     * Build the platform.
     *
     * @return the platform
     */
    @NonNull
    public Platform build() {
        throwIfAlreadyUsed();
        used = true;

        try (initialState) {
            return new SwirldsPlatform(this);
        } finally {

            // Future work: eliminate the static variables that require this code to exist
            if (firstPlatform) {
                MetricsDocUtils.writeMetricsDocumentToFile(
                        getGlobalMetrics(), getPlatforms(), platformContext.getConfiguration());
                getMetricsProvider().start();
            }
        }
    }
}
