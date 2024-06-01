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
import com.hedera.hapi.node.base.Key;
import com.hedera.node.app.fees.FeeAccumulatorImpl;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.signature.SignatureVerificationFuture;
import com.hedera.node.app.spi.fees.FeeAccumulator;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.ServiceApiFactory;
import com.hedera.node.app.workflows.handle.flow.annotations.PlatformTransactionScope;
import com.hedera.node.app.workflows.handle.flow.infra.PreHandleLogic;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;

@Module
public interface StagingModule {
    @Provides
    @PlatformTransactionScope
    static PreHandleResult providePreHandleResult(
            @NonNull ReadableStoreFactory storeFactory,
            @NonNull NodeInfo creator,
            @NonNull ConsensusTransaction platformTxn,
            @NonNull PreHandleLogic preHandleLogic) {
        return preHandleLogic.getCurrentPreHandleResult(storeFactory, creator, platformTxn);
    }

    @Provides
    @PlatformTransactionScope
    static TransactionInfo provideTransactionInfo(@NonNull PreHandleResult preHandleResult) {
        return preHandleResult.txInfo();
    }

    @Provides
    @PlatformTransactionScope
    static Map<Key, SignatureVerificationFuture> provideKeyVerifications(@NonNull PreHandleResult preHandleResult) {
        return preHandleResult.getVerificationResults();
    }

    @Provides
    @PlatformTransactionScope
    static int provideLegacyFeeCalcNetworkVpt(@NonNull TransactionInfo txnInfo) {
        return txnInfo.signatureMap().sigPair().size();
    }

    @Provides
    @PlatformTransactionScope
    static HederaFunctionality provideFunctionality(@NonNull TransactionInfo txnInfo) {
        return txnInfo.functionality();
    }

    @Provides
    @PlatformTransactionScope
    static FeeAccumulator provideFeeAccumulator(
            @NonNull SavepointStackImpl stack,
            @NonNull Configuration configuration,
            @NonNull StoreMetricsService storeMetricsService,
            @NonNull SingleTransactionRecordBuilderImpl recordBuilder) {
        final var serviceApiFactory = new ServiceApiFactory(stack, configuration, storeMetricsService);
        final var tokenApi = serviceApiFactory.getApi(TokenServiceApi.class);
        return new FeeAccumulatorImpl(tokenApi, recordBuilder);
    }
}
