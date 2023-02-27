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

package com.hedera.node.app.workflows.handle;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.properties.GlobalStaticProperties;
import com.hedera.node.app.service.mono.ledger.ids.EntityIdSource;
import com.hedera.node.app.service.mono.txns.TransitionLogicLookup;
import com.hedera.node.app.service.mono.txns.TransitionRunner;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hedera.node.app.spi.exceptions.HandleStatusException;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AdaptedMonoTransitionRunner extends TransitionRunner {
    private final TransactionDispatcher dispatcher;
    private final Set<HederaFunctionality> functionsToDispatch;

    @Inject
    public AdaptedMonoTransitionRunner(
            @NonNull final EntityIdSource ids,
            @NonNull final TransactionContext txnCtx,
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final TransitionLogicLookup lookup,
            @NonNull final GlobalStaticProperties staticProperties) {
        super(ids, txnCtx, lookup);
        this.dispatcher = dispatcher;
        this.functionsToDispatch = staticProperties.workflowsEnabled();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean tryTransition(final @NonNull TxnAccessor accessor) {
        final var function = accessor.getFunction();
        if (functionsToDispatch.contains(function)) {
            try {
                dispatcher.dispatchHandle(function, accessor.getTxn());
                txnCtx.setStatus(SUCCESS);
            } catch (final HandleStatusException e) {
                super.resolveFailure(e.getStatus(), accessor, e);
            }
            return true;
        } else {
            return super.tryTransition(accessor);
        }
    }
}
