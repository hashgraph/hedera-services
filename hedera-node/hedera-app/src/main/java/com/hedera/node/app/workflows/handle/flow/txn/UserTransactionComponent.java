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

package com.hedera.node.app.workflows.handle.flow.txn;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.node.app.service.token.records.TokenContext;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.handle.flow.dispatch.user.UserDispatchComponent;
import com.hedera.node.app.workflows.handle.flow.txn.modules.ActiveConfigModule;
import com.hedera.node.app.workflows.handle.flow.txn.modules.ContextModule;
import com.hedera.node.app.workflows.handle.flow.txn.modules.DispatchSubcomponentsModule;
import com.hedera.node.app.workflows.handle.flow.txn.modules.LastHandledTime;
import com.hedera.node.app.workflows.handle.flow.txn.modules.PreHandleResultModule;
import com.hedera.node.app.workflows.handle.flow.txn.modules.StateModule;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.state.HederaState;
import com.swirlds.state.spi.info.NodeInfo;
import dagger.BindsInstance;
import dagger.Subcomponent;
import java.time.Instant;
import javax.inject.Provider;

/**
 * The Dagger subcomponent to provide the bindings for the platform transaction scope.
 */
@Subcomponent(
        modules = {
            StateModule.class,
            ActiveConfigModule.class,
            ContextModule.class,
            PreHandleResultModule.class,
            DispatchSubcomponentsModule.class
        })
@UserTxnScope
public interface UserTransactionComponent {
    @Subcomponent.Factory
    interface Factory {
        UserTransactionComponent create(
                @BindsInstance PlatformState platformState,
                @BindsInstance ConsensusEvent platformEvent,
                @BindsInstance NodeInfo creator,
                @BindsInstance ConsensusTransaction platformTxn,
                @BindsInstance Instant consensusTime,
                @BindsInstance @LastHandledTime Instant lastHandledConsensusTime);
    }

    /**
     * Returns whether this is the first transaction ever handled.
     *
     * @return true if this is the first transaction ever handled
     */
    boolean isGenesisTxn();

    /**
     * The functionality of the user transaction
     * @return the functionality
     */
    HederaFunctionality functionality();

    /**
     * The workflow for the user transaction to produce stream items
     *
     * @return the workflow
     */
    UserTxnWorkflow workflow();

    /**
     * The consensus time of the user transaction
     * @return the consensus time
     */
    Instant consensusNow();

    /**
     * The state used for the user transaction processing
     * @return the state
     */
    HederaState state();

    /**
     * The platform state provided by the platform to handle the user transaction
     * @return the platform state
     */
    PlatformState platformState();

    /**
     * The consensus event provided by the platform to handle the user transaction
     * @return the consensus event
     */
    ConsensusEvent platformEvent();

    /**
     * The creator node info of the user transaction
     * @return the creator
     */
    NodeInfo creator();

    /**
     * The platform transaction of the user transaction
     * @return the consensus transaction
     */
    ConsensusTransaction platformTxn();

    /**
     * The record list builder that will wrap all record streams for the user transaction
     * @return the record list builder
     */
    RecordListBuilder recordListBuilder();

    /**
     * The transaction info returned when we prehandle the user transaction
     * @return the transaction info
     */
    TransactionInfo txnInfo();

    /**
     * The token context for the user transaction
     * @return the token context
     */
    TokenContext tokenContext();

    /**
     * The savepoint stack used for the user transaction
     * @return the savepoint stack
     */
    SavepointStackImpl stack();

    /**
     * The prehandle result for the user transaction
     * @return the prehandle result
     */
    PreHandleResult preHandleResult();

    /**
     * The readable store factory for the user transaction
     * @return the store factory
     */
    ReadableStoreFactory readableStoreFactory();

    /**
     * The provider for the user dispatch subcomponent
     * @return the provider
     */
    Provider<UserDispatchComponent.Factory> userDispatchProvider();

    Configuration configuration();

    @LastHandledTime
    Instant lastHandledConsensusTime();
}
