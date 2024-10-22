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

package com.swirlds.platform;

import static com.swirlds.logging.legacy.LogMarker.RECONNECT;

import com.swirlds.common.AddressBook;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.stream.RunningEventHashOverride;
import com.swirlds.logging.legacy.LogMarker;
import com.swirlds.platform.components.AppNotifier;
import com.swirlds.platform.components.SavedStateController;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.validation.AddressBookUpdate;
import com.swirlds.platform.eventhandling.EventConfig;
import com.swirlds.platform.listeners.ReconnectCompleteNotification;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.nexus.SignedStateNexus;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.address.AddressBookUtils;
import com.swirlds.platform.system.status.actions.ReconnectCompleteAction;
import com.swirlds.platform.wiring.PlatformWiring;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// FUTURE WORK: this data should be traveling out over the wiring framework.

/**
 * Encapsulates the logic for loading the reconnect state.
 */
public class ReconnectStateLoader {

    private static final Logger logger = LogManager.getLogger(ReconnectStateLoader.class);
    private final Platform platform;
    private final PlatformContext platformContext;
    private final PlatformWiring platformWiring;
    private final SwirldStateManager swirldStateManager;
    private final SignedStateNexus latestImmutableStateNexus;
    private final SavedStateController savedStateController;
    private final AddressBook addressBook;

    /**
     * Constructor.
     *
     * @param platform                  the platform
     * @param platformContext           the platform context
     * @param platformWiring            the platform wiring
     * @param swirldStateManager        manages the mutable state
     * @param latestImmutableStateNexus holds the latest immutable state
     * @param savedStateController      manages how states are saved
     * @param addressBook               the address book
     */
    public ReconnectStateLoader(
            @NonNull final Platform platform,
            @NonNull final PlatformContext platformContext,
            @NonNull final PlatformWiring platformWiring,
            @NonNull final SwirldStateManager swirldStateManager,
            @NonNull final SignedStateNexus latestImmutableStateNexus,
            @NonNull final SavedStateController savedStateController,
            @NonNull final AddressBook addressBook) {
        this.platform = Objects.requireNonNull(platform);
        this.platformContext = Objects.requireNonNull(platformContext);
        this.platformWiring = Objects.requireNonNull(platformWiring);
        this.swirldStateManager = Objects.requireNonNull(swirldStateManager);
        this.latestImmutableStateNexus = Objects.requireNonNull(latestImmutableStateNexus);
        this.savedStateController = Objects.requireNonNull(savedStateController);
        this.addressBook = Objects.requireNonNull(addressBook);
    }

    /**
     * Used to load the state received from the sender.
     *
     * @param signedState the signed state that was received from the sender
     */
    public void loadReconnectState(@NonNull final SignedState signedState) {
        // the state was received, so now we load its data into different objects
        logger.info(LogMarker.STATE_HASH.getMarker(), "RECONNECT: loadReconnectState: reloading state");
        logger.debug(RECONNECT.getMarker(), "`loadReconnectState` : reloading state");
        try {
            platformWiring.overrideIssDetectorState(signedState.reserve("reconnect state to issDetector"));

            // It's important to call init() before loading the signed state. The loading process makes copies
            // of the state, and we want to be sure that the first state in the chain of copies has been initialized.
            final Hash reconnectHash = signedState.getState().getHash();
            signedState
                    .getSwirldState()
                    .init(
                            platform,
                            InitTrigger.RECONNECT,
                            signedState.getState().getReadablePlatformState().getCreationSoftwareVersion());
            if (!Objects.equals(signedState.getState().getHash(), reconnectHash)) {
                throw new IllegalStateException(
                        "State hash is not permitted to change during a reconnect init() call. Previous hash was "
                                + reconnectHash + ", new hash is "
                                + signedState.getState().getHash());
            }

            // Before attempting to load the state, verify that the platform AB matches the state AB.
            AddressBookUtils.verifyReconnectAddressBooks(addressBook, signedState.getAddressBook());

            swirldStateManager.loadFromSignedState(signedState);
            // kick off transition to RECONNECT_COMPLETE before beginning to save the reconnect state to disk
            // this guarantees that the platform status will be RECONNECT_COMPLETE before the state is saved
            platformWiring
                    .getStatusActionSubmitter()
                    .submitStatusAction(new ReconnectCompleteAction(signedState.getRound()));
            latestImmutableStateNexus.setState(signedState.reserve("set latest immutable to reconnect state"));
            savedStateController.reconnectStateReceived(
                    signedState.reserve("savedStateController.reconnectStateReceived"));

            platformWiring.sendStateToHashLogger(signedState);
            // this will send the state to the signature collector which will send it to be written to disk.
            // in the future, we might not send it to the collector because it already has all the signatures
            // if this is the case, we must make sure to send it to the writer directly
            platformWiring
                    .getSignatureCollectorStateInput()
                    .put(signedState.reserve("loading reconnect state into sig collector"));
            platformWiring.consensusSnapshotOverride(Objects.requireNonNull(
                    signedState.getState().getReadablePlatformState().getSnapshot()));

            platformWiring
                    .getAddressBookUpdateInput()
                    .inject(new AddressBookUpdate(
                            signedState.getState().getReadablePlatformState().getPreviousAddressBook(),
                            signedState.getState().getReadablePlatformState().getAddressBook()));

            final AncientMode ancientMode = platformContext
                    .getConfiguration()
                    .getConfigData(EventConfig.class)
                    .getAncientMode();

            platformWiring.updateEventWindow(new EventWindow(
                    signedState.getRound(),
                    signedState.getState().getReadablePlatformState().getAncientThreshold(),
                    signedState.getState().getReadablePlatformState().getAncientThreshold(),
                    ancientMode));

            final RunningEventHashOverride runningEventHashOverride = new RunningEventHashOverride(
                    signedState.getState().getReadablePlatformState().getLegacyRunningEventHash(), true);
            platformWiring.updateRunningHash(runningEventHashOverride);
            platformWiring.getPcesWriterRegisterDiscontinuityInput().inject(signedState.getRound());

            // Notify any listeners that the reconnect has been completed
            platformWiring
                    .getNotifierWiring()
                    .getInputWire(AppNotifier::sendReconnectCompleteNotification)
                    .put(new ReconnectCompleteNotification(
                            signedState.getRound(),
                            signedState.getConsensusTimestamp(),
                            signedState.getState().getSwirldState()));

        } catch (final RuntimeException e) {
            logger.debug(RECONNECT.getMarker(), "`loadReconnectState` : FAILED, reason: {}", e.getMessage());
            // if the loading fails for whatever reason, we clear all data again in case some of it has been loaded
            platformWiring.clear();
            throw e;
        }
    }
}
