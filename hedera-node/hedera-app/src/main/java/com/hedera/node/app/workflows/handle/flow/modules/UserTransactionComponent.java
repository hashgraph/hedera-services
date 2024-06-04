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

package com.hedera.node.app.workflows.handle.flow.modules;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.node.app.service.token.records.TokenContext;
import com.hedera.node.app.signature.KeyVerifier;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.handle.flow.annotations.UserTransactionScope;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.state.HederaState;
import dagger.BindsInstance;
import dagger.Subcomponent;
import java.time.Instant;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Subcomponent(modules = {StateModule.class, ActiveConfigModule.class, ContextModule.class, StagingModule.class})
@UserTransactionScope
public interface UserTransactionComponent {
    @Subcomponent.Factory
    interface Factory {
        UserTransactionComponent create(
                @BindsInstance PlatformState platformState,
                @BindsInstance ConsensusEvent platformEvent,
                @BindsInstance NodeInfo creator,
                @BindsInstance ConsensusTransaction platformTxn,
                @BindsInstance Instant consensusTime);
    }

    HederaFunctionality functionality();

    Supplier<Stream<SingleTransactionRecord>> recordStream();

    Instant consensusNow();

    HederaState state();

    PlatformState platformState();

    ConsensusEvent platformEvent();

    NodeInfo creator();

    ConsensusTransaction platformTxn();

    RecordListBuilder recordListBuilder();

    TransactionInfo txnInfo();

    TokenContext tokenContext();

    SavepointStackImpl savepointStack();

    SingleTransactionRecordBuilderImpl recordBuilder();

    PreHandleResult preHandleResult();

    ReadableStoreFactory readableStoreFactory();

    KeyVerifier keyVerifier();
}
