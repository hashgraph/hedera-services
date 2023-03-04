/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.prehandle;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.node.app.service.mono.context.properties.GlobalStaticProperties;
import com.hedera.node.app.service.mono.sigs.EventExpansion;
import com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.state.merkle.MerkleHederaState;
import com.swirlds.common.system.events.Event;
import com.swirlds.common.system.transaction.Transaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class AdaptedMonoEventExpansion {
    private static final Logger log = LogManager.getLogger(AdaptedMonoEventExpansion.class);
    private final EventExpansion eventExpansion;
    private final PreHandleWorkflow preHandleWorkflow;
    private final GlobalStaticProperties staticProperties;

    @Inject
    public AdaptedMonoEventExpansion(
            @NonNull final EventExpansion eventExpansion,
            @NonNull final PreHandleWorkflow preHandleWorkflow,
            @NonNull final GlobalStaticProperties staticProperties) {
        this.eventExpansion = Objects.requireNonNull(eventExpansion);
        this.preHandleWorkflow = Objects.requireNonNull(preHandleWorkflow);
        this.staticProperties = Objects.requireNonNull(staticProperties);
    }

    public void expand(final Event event, final HederaState state) {
        final var typesForWorkflows = staticProperties.workflowsEnabled();
        final List<Transaction> forWorkflows = new ArrayList<>();
        event.forEachTransaction(txn -> {
            try {
                final var accessor = SignedTxnAccessor.from(txn.getContents());
                if (typesForWorkflows.contains(accessor.getFunction())) {
                    forWorkflows.add(txn);
                } else {
                    eventExpansion.expandSingle(txn, (MerkleHederaState) state);
                }
            } catch (final InvalidProtocolBufferException e) {
                log.warn("Unable to parse preHandle transaction", e);
            }
        });
        if (!forWorkflows.isEmpty()) {
            ((PreHandleWorkflowImpl) preHandleWorkflow).preHandle(forWorkflows.iterator(), state);
        }
    }
}
