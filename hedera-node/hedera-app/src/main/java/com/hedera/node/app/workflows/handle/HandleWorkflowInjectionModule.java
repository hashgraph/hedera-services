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

import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.sigs.Expansion;
import com.hedera.node.app.service.mono.sigs.PlatformSigOps;
import com.hedera.node.app.service.mono.sigs.factories.ReusableBodySigningFactory;
import com.hedera.node.app.service.mono.sigs.factories.TxnScopedPlatformSigFactory;
import com.hedera.node.app.service.mono.utils.NonAtomicReference;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.state.WorkingStateAccessor;
import com.hedera.node.app.workflows.handle.validation.MonoExpiryValidator;
import com.hedera.node.app.workflows.handle.validation.StandardizedAttributeValidator;
import com.swirlds.common.system.Platform;
import com.swirlds.common.utility.AutoCloseableWrapper;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import javax.inject.Singleton;

@Module(includes = {HandlersInjectionModule.class})
public interface HandleWorkflowInjectionModule {
    @Provides
    static Expansion.CryptoSigsCreation provideCryptoSigsCreation() {
        return PlatformSigOps::createCryptoSigsFrom;
    }

    @Provides
    static Function<TxnAccessor, TxnScopedPlatformSigFactory> provideScopedFactoryProvider() {
        return ReusableBodySigningFactory::new;
    }

    @Binds
    @Singleton
    ExpiryValidator bindEntityExpiryValidator(MonoExpiryValidator monoEntityExpiryValidator);

    @Binds
    @Singleton
    AttributeValidator bindAttributeValidator(StandardizedAttributeValidator attributeValidator);

    Runnable NO_OP = () -> {};

    @Provides
    static Supplier<AutoCloseableWrapper<HederaState>> provideStateSupplier(
            @NonNull final WorkingStateAccessor workingStateAccessor) {
        return () -> new AutoCloseableWrapper<>(workingStateAccessor.getHederaState(), NO_OP);
    }

    @Provides
    @SuppressWarnings({"unchecked", "rawtypes"})
    static NonAtomicReference<HederaState> provideMutableStateSupplier(@NonNull final Platform platform) {
        // Always return the latest mutable state until we support state proofs
        return new NonAtomicReference<>();
    }

    @Provides
    @Singleton
    static LongSupplier provideConsensusSecond(@NonNull final TransactionContext txnCtx) {
        return () -> txnCtx.consensusTime().getEpochSecond();
    }
}
