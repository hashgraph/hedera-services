/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.txns;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.SystemDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.SystemUndelete;

import com.hedera.node.app.service.mono.fees.annotations.FunctionKey;
import com.hedera.node.app.service.mono.legacy.handler.SmartContractRequestHandler;
import com.hedera.node.app.service.mono.txns.consensus.ConsensusLogicModule;
import com.hedera.node.app.service.mono.txns.contract.ContractLogicModule;
import com.hedera.node.app.service.mono.txns.contract.ContractSysDelTransitionLogic;
import com.hedera.node.app.service.mono.txns.contract.ContractSysUndelTransitionLogic;
import com.hedera.node.app.service.mono.txns.crypto.CryptoLogicModule;
import com.hedera.node.app.service.mono.txns.customfees.CustomFeeSchedules;
import com.hedera.node.app.service.mono.txns.customfees.FcmCustomFeeSchedules;
import com.hedera.node.app.service.mono.txns.ethereum.EthereumLogicModule;
import com.hedera.node.app.service.mono.txns.file.FileLogicModule;
import com.hedera.node.app.service.mono.txns.file.FileSysDelTransitionLogic;
import com.hedera.node.app.service.mono.txns.file.FileSysUndelTransitionLogic;
import com.hedera.node.app.service.mono.txns.network.NetworkLogicModule;
import com.hedera.node.app.service.mono.txns.schedule.ScheduleLogicModule;
import com.hedera.node.app.service.mono.txns.span.ExpandHandleSpan;
import com.hedera.node.app.service.mono.txns.span.SpanMapManager;
import com.hedera.node.app.service.mono.txns.submission.BasicSubmissionFlow;
import com.hedera.node.app.service.mono.txns.token.TokenLogicModule;
import com.hedera.node.app.service.mono.txns.util.UtilLogicModule;
import com.hedera.node.app.service.mono.txns.validation.ContextOptionValidator;
import com.hedera.node.app.service.mono.txns.validation.OptionValidator;
import com.hedera.node.app.service.mono.utils.accessors.AccessorFactory;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;
import java.util.List;
import javax.inject.Singleton;

@Module(
        includes = {
            FileLogicModule.class,
            TokenLogicModule.class,
            CryptoLogicModule.class,
            NetworkLogicModule.class,
            ContractLogicModule.class,
            EthereumLogicModule.class,
            ScheduleLogicModule.class,
            ConsensusLogicModule.class,
            UtilLogicModule.class
        })
public interface TransactionsModule {
    @Binds
    @Singleton
    SubmissionFlow bindSubmissionFlow(BasicSubmissionFlow basicSubmissionFlow);

    @Binds
    @Singleton
    OptionValidator bindOptionValidator(ContextOptionValidator contextOptionValidator);

    @Binds
    @Singleton
    CustomFeeSchedules bindCustomFeeSchedules(FcmCustomFeeSchedules fcmCustomFeeSchedules);

    @Provides
    @Singleton
    static ExpandHandleSpan provideExpandHandleSpan(SpanMapManager spanMapManager, AccessorFactory factory) {
        return new ExpandHandleSpan(spanMapManager, factory);
    }

    @Provides
    @Singleton
    static ContractSysDelTransitionLogic.LegacySystemDeleter provideLegacySystemDeleter(
            SmartContractRequestHandler contracts) {
        return contracts::systemDelete;
    }

    @Provides
    @Singleton
    static ContractSysUndelTransitionLogic.LegacySystemUndeleter provideLegacySystemUndeleter(
            SmartContractRequestHandler contracts) {
        return contracts::systemUndelete;
    }

    @Provides
    @IntoMap
    @FunctionKey(SystemDelete)
    static List<TransitionLogic> provideSystemDeleteLogic(
            FileSysDelTransitionLogic fileSysDelTransitionLogic,
            ContractSysDelTransitionLogic contractSysDelTransitionLogic) {
        return List.of(fileSysDelTransitionLogic, contractSysDelTransitionLogic);
    }

    @Provides
    @IntoMap
    @FunctionKey(SystemUndelete)
    static List<TransitionLogic> provideSystemUndeleteLogic(
            FileSysUndelTransitionLogic fileSysUndelTransitionLogic,
            ContractSysUndelTransitionLogic contractSysUndelTransitionLogic) {
        return List.of(fileSysUndelTransitionLogic, contractSysUndelTransitionLogic);
    }
}
