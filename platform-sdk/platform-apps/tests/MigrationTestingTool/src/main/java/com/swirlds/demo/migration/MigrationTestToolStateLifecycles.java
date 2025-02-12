/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.swirlds.demo.migration;

import static com.swirlds.demo.migration.MigrationTestingToolMain.PREVIOUS_SOFTWARE_VERSION;
import static com.swirlds.demo.migration.TransactionUtils.isSystemTransaction;
import static com.swirlds.logging.legacy.LogMarker.STARTUP;

import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.hedera.pbj.runtime.ParseException;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.state.StateLifecycles;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.events.Event;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.platform.system.transaction.Transaction;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Iterator;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class handles the lifecycle events for the {@link MigrationTestingToolState}.
 */
public class MigrationTestToolStateLifecycles implements StateLifecycles<MigrationTestingToolState> {
    private static final Logger logger = LogManager.getLogger(MigrationTestToolStateLifecycles.class);

    public NodeId selfId;

    @Override
    public void onStateInitialized(
            @NonNull MigrationTestingToolState state,
            @NonNull Platform platform,
            @NonNull InitTrigger trigger,
            @Nullable SoftwareVersion previousVersion) {
        final MerkleMap<AccountID, MapValue> merkleMap = state.getMerkleMap();
        if (merkleMap != null) {
            logger.info(STARTUP.getMarker(), "MerkleMap initialized with {} values", merkleMap.size());
        }
        final VirtualMap<?, ?> virtualMap = state.getVirtualMap();
        if (virtualMap != null) {
            logger.info(STARTUP.getMarker(), "VirtualMap initialized with {} values", virtualMap.size());
        }
        selfId = platform.getSelfId();

        if (trigger == InitTrigger.GENESIS) {
            logger.warn(STARTUP.getMarker(), "InitTrigger was {} when expecting RESTART or RECONNECT", trigger);
            selfId = platform.getSelfId();
        }

        if (previousVersion == null || previousVersion.compareTo(PREVIOUS_SOFTWARE_VERSION) != 0) {
            logger.warn(
                    STARTUP.getMarker(),
                    "previousSoftwareVersion was {} when expecting it to be {}",
                    previousVersion,
                    PREVIOUS_SOFTWARE_VERSION);
        }

        if (trigger == InitTrigger.GENESIS) {
            logger.info(STARTUP.getMarker(), "Doing genesis initialization");
            state.genesisInit();
        }
    }

    @Override
    public void onHandleConsensusRound(
            @NonNull Round round,
            @NonNull MigrationTestingToolState state,
            @NonNull Consumer<ScopedSystemTransaction<StateSignatureTransaction>> stateSignatureTransactionCallback) {
        state.throwIfImmutable();
        for (final Iterator<ConsensusEvent> eventIt = round.iterator(); eventIt.hasNext(); ) {
            final ConsensusEvent event = eventIt.next();
            for (final Iterator<ConsensusTransaction> transIt = event.consensusTransactionIterator();
                    transIt.hasNext(); ) {
                final ConsensusTransaction trans = transIt.next();
                if (trans.isSystem()) {
                    continue;
                }
                if (isSystemTransaction(trans.getApplicationTransaction())) {
                    consumeSystemTransaction(trans, event, stateSignatureTransactionCallback);
                    continue;
                }

                final MigrationTestingToolTransaction mTrans =
                        TransactionUtils.parseTransaction(trans.getApplicationTransaction());
                mTrans.applyTo(state);
            }
        }
    }

    @Override
    public void onPreHandle(
            @NonNull Event event,
            @NonNull MigrationTestingToolState state,
            @NonNull Consumer<ScopedSystemTransaction<StateSignatureTransaction>> stateSignatureTransactionCallback) {
        event.forEachTransaction(transaction -> {

            // We don't want to consume deprecated EventTransaction.STATE_SIGNATURE_TRANSACTION system transactions in
            // the callback, since it's intended to be used only
            // for the new form of encoded system transactions in Bytes.
            // We skip the current iteration, if it processes a deprecated system transaction with the
            // EventTransaction.STATE_SIGNATURE_TRANSACTION type.
            if (transaction.isSystem()) {
                return;
            }

            if (isSystemTransaction(transaction.getApplicationTransaction())) {
                consumeSystemTransaction(transaction, event, stateSignatureTransactionCallback);
            }
        });
    }

    @Override
    public boolean onSealConsensusRound(@NonNull Round round, @NonNull MigrationTestingToolState state) {
        // no-op
        return true;
    }

    @Override
    public void onUpdateWeight(
            @NonNull MigrationTestingToolState state,
            @NonNull AddressBook configAddressBook,
            @NonNull PlatformContext context) {
        // no-op
    }

    @Override
    public void onNewRecoveredState(@NonNull MigrationTestingToolState recoveredState) {
        // no-op
    }

    private void consumeSystemTransaction(
            final @NonNull Transaction transaction,
            final @NonNull Event event,
            final @NonNull Consumer<ScopedSystemTransaction<StateSignatureTransaction>>
                            stateSignatureTransactionCallback) {
        try {
            final var stateSignatureTransaction =
                    StateSignatureTransaction.PROTOBUF.parse(transaction.getApplicationTransaction());
            stateSignatureTransactionCallback.accept(new ScopedSystemTransaction<>(
                    event.getCreatorId(), event.getSoftwareVersion(), stateSignatureTransaction));
        } catch (final ParseException e) {
            logger.error("Failed to parse StateSignatureTransaction", e);
        }
    }
}
