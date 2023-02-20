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

package com.hedera.node.app.service.mono.sigs;

import com.hedera.node.app.service.mono.config.FileNumbers;
import com.hedera.node.app.service.mono.context.MutableStateChildren;
import com.hedera.node.app.service.mono.context.NodeInfo;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.contracts.sources.EvmSigsVerifier;
import com.hedera.node.app.service.mono.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.node.app.service.mono.keys.HederaKeyActivation;
import com.hedera.node.app.service.mono.keys.OnlyIfSigVerifiableValid;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.sigs.annotations.WorkingStateSigReqs;
import com.hedera.node.app.service.mono.sigs.metadata.StateChildrenSigMetadataLookup;
import com.hedera.node.app.service.mono.sigs.metadata.TokenMetaUtils;
import com.hedera.node.app.service.mono.sigs.order.PolicyBasedSigWaivers;
import com.hedera.node.app.service.mono.sigs.order.SigRequirements;
import com.hedera.node.app.service.mono.sigs.order.SignatureWaivers;
import com.hedera.node.app.service.mono.sigs.utils.PrecheckUtils;
import com.hedera.node.app.service.mono.sigs.verification.SyncVerifier;
import com.hedera.node.app.service.mono.state.logic.PayerSigValidity;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.system.Platform;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import javax.inject.Singleton;

@Module
public interface SigsModule {
    @Binds
    @Singleton
    EvmSigsVerifier provideSoliditySigsVerifier(TxnAwareEvmSigsVerifier txnAwareEvmSigsVerifier);

    @Binds
    @Singleton
    SignatureWaivers provideSignatureWaivers(PolicyBasedSigWaivers policyBasedSigWaivers);

    @Provides
    @Singleton
    static SyncVerifier provideSyncVerifier(Platform platform) {
        return platform.getCryptography()::verifySync;
    }

    @Provides
    @Singleton
    static BiPredicate<JKey, TransactionSignature> provideValidityTest() {
        return new OnlyIfSigVerifiableValid();
    }

    @Provides
    @Singleton
    @WorkingStateSigReqs
    static SigRequirements provideWorkingStateSigReqs(
            final FileNumbers fileNumbers,
            final SignatureWaivers signatureWaivers,
            final MutableStateChildren workingState,
            final GlobalDynamicProperties properties) {
        final var sigMetaLookup = new StateChildrenSigMetadataLookup(
                fileNumbers, workingState, TokenMetaUtils::signingMetaFrom, properties);
        return new SigRequirements(sigMetaLookup, signatureWaivers);
    }

    @Provides
    @Singleton
    static Predicate<TransactionBody> provideQueryPaymentTest(final NodeInfo nodeInfo) {
        return PrecheckUtils.queryPaymentTestFor(nodeInfo);
    }

    @Provides
    @Singleton
    static PayerSigValidity providePayerSigValidity() {
        return HederaKeyActivation::payerSigIsActive;
    }

    @Provides
    @Singleton
    static ExpansionHelper provideExpansionHelper() {
        return HederaToPlatformSigOps::expandIn;
    }
}
