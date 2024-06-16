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

package com.hedera.node.app.workflows.handle.flow.txn.modules;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.node.app.signature.SignatureVerificationFuture;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.handle.flow.dispatch.user.logic.PreHandleResultManager;
import com.hedera.node.app.workflows.handle.flow.txn.UserTxnScope;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.state.spi.info.NodeInfo;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;

/**
 * The module that provides the pre-handle result and related information in UserTxnScope.
 */
@Module
public interface PreHandleResultModule {
    @Provides
    @UserTxnScope
    static PreHandleResult providePreHandleResult(
            @NonNull final NodeInfo creator,
            @NonNull final ConsensusTransaction platformTxn,
            @NonNull final PreHandleResultManager preHandleResultManager,
            @NonNull final ReadableStoreFactory storeFactory) {
        return preHandleResultManager.getCurrentPreHandleResult(creator, platformTxn, storeFactory);
    }

    @Provides
    @UserTxnScope
    static TransactionInfo provideTransactionInfo(@NonNull final PreHandleResult preHandleResult) {
        return preHandleResult.txInfo();
    }

    @Provides
    @UserTxnScope
    static Map<Key, SignatureVerificationFuture> provideKeyVerifications(
            @NonNull final PreHandleResult preHandleResult) {
        return preHandleResult.getVerificationResults();
    }

    @Provides
    @UserTxnScope
    static int provideLegacyFeeCalcNetworkVpt(@NonNull final TransactionInfo txnInfo) {
        return txnInfo.signatureMap().sigPair().size();
    }

    @Provides
    @UserTxnScope
    static HederaFunctionality provideFunctionality(@NonNull final TransactionInfo txnInfo) {
        return txnInfo.functionality();
    }

    @Provides
    @UserTxnScope
    static Key providePayerKey(@NonNull final PreHandleResult preHandleResult) {
        return preHandleResult.payerKey() == null ? Key.DEFAULT : preHandleResult.payerKey();
    }
}
