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

package com.hedera.node.app.workflows.handle.flow;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.node.app.signature.SignatureVerificationFuture;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.handle.flow.annotations.HandleScope;
import com.hedera.node.app.workflows.handle.flow.infra.PreHandleLogic;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Map;
import java.util.function.Supplier;

@Module
public interface StagingModule {
    @Provides
    @HandleScope
    static PreHandleResult providePreHandleResult(
            @NonNull ReadableStoreFactory storeFactory,
            @NonNull NodeInfo creator,
            @NonNull ConsensusTransaction platformTxn,
            @NonNull PreHandleLogic preHandleLogic) {
        return preHandleLogic.getCurrentPreHandleResult(storeFactory, creator, platformTxn);
    }

    @Provides
    @HandleScope
    static TransactionInfo provideTransactionInfo(@NonNull PreHandleResult preHandleResult) {
        return preHandleResult.txInfo();
    }

    @Provides
    @HandleScope
    static Map<Key, SignatureVerificationFuture> provideKeyVerifications(@NonNull PreHandleResult preHandleResult) {
        return preHandleResult.getVerificationResults();
    }

    @Provides
    @HandleScope
    static int provideLegacyFeeCalcNetworkVpt(@NonNull TransactionInfo txnInfo) {
        return txnInfo.signatureMap().sigPair().size();
    }

    @Provides
    @HandleScope
    static HederaFunctionality provideFunctionality(@NonNull TransactionInfo txnInfo){
        return txnInfo.functionality();
    }
}
