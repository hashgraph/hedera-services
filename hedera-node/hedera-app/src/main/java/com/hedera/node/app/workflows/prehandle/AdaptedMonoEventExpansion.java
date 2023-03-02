package com.hedera.node.app.workflows.prehandle;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.node.app.service.mono.context.properties.GlobalStaticProperties;
import com.hedera.node.app.service.mono.sigs.EventExpansion;
import com.hedera.node.app.service.mono.utils.MiscUtils;
import com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.state.merkle.MerkleHederaState;
import com.swirlds.common.system.events.Event;
import com.swirlds.common.system.transaction.Transaction;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Singleton
public class AdaptedMonoEventExpansion {
    private final EventExpansion eventExpansion;
    private final PreHandleWorkflow preHandleWorkflow;
    private final GlobalStaticProperties staticProperties;

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
                throw new AssertionError("Not implemented");
            }
        });
        if (!forWorkflows.isEmpty()) {
            ((PreHandleWorkflowImpl) preHandleWorkflow).preHandle(forWorkflows.iterator(), state);
        }
    }
}
