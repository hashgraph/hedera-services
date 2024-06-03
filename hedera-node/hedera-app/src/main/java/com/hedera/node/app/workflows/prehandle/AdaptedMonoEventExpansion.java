/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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
import com.hedera.node.app.service.mono.context.NodeInfo;
import com.hedera.node.app.service.mono.context.StateChildrenProvider;
import com.hedera.node.app.service.mono.context.properties.GlobalStaticProperties;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.sigs.EventExpansion;
import com.hedera.node.app.service.mono.sigs.order.SigReqsManager;
import com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.swirlds.platform.system.events.Event;
import com.swirlds.platform.system.transaction.Transaction;
import com.swirlds.state.HederaState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class AdaptedMonoEventExpansion {
    private static final Logger log = LogManager.getLogger(AdaptedMonoEventExpansion.class);
    private final EventExpansion eventExpansion;
    private final PreHandleWorkflow preHandleWorkflow;
    private final GlobalStaticProperties staticProperties;
    private final Provider<SigReqsManager> sigReqsManagerProvider;

    @Inject
    public AdaptedMonoEventExpansion(
            @NonNull final EventExpansion eventExpansion,
            @NonNull final PreHandleWorkflow preHandleWorkflow,
            @NonNull final GlobalStaticProperties staticProperties,
            @NonNull final Provider<SigReqsManager> sigReqsManagerProvider) {
        this.eventExpansion = Objects.requireNonNull(eventExpansion);
        this.preHandleWorkflow = Objects.requireNonNull(preHandleWorkflow);
        this.staticProperties = Objects.requireNonNull(staticProperties);
        this.sigReqsManagerProvider = sigReqsManagerProvider;
    }

    public void expand(final Event event, final HederaState state, final NodeInfo nodeInfo) {
        final var typesForWorkflows = staticProperties.workflowsEnabled();
        final List<Transaction> forWorkflows = new ArrayList<>();
        event.forEachTransaction(txn -> {
            try {
                final var accessor =
                        SignedTxnAccessor.from(txn.getApplicationPayload().toByteArray());
                if (typesForWorkflows.contains(accessor.getFunction())) {
                    forWorkflows.add(txn);
                } else {
                    eventExpansion.expandSingle(txn, sigReqsManagerProvider.get(), (StateChildrenProvider) state);
                }
            } catch (final InvalidProtocolBufferException e) {
                log.warn("Unable to parse preHandle transaction", e);
            }
        });
        if (!forWorkflows.isEmpty()) {
            final var readableStoreFactory = new ReadableStoreFactory(state);
            final var creatorAccountID =
                    PbjConverter.toPbj(nodeInfo.accountOf(event.getCreatorId().id()));
            preHandleWorkflow.preHandle(readableStoreFactory, creatorAccountID, forWorkflows.stream());
        }
    }
}
