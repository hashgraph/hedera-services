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

package com.hedera.node.app.integration;

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hedera.node.app.service.mono.utils.replay.ReplayAssetRecording.REPLAY_ASSETS_DIR;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;

import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.integration.facilities.ReplayAdvancingConsensusNow;
import com.hedera.node.app.integration.infra.InMemoryWritableStoreFactory;
import com.hedera.node.app.integration.infra.ReplayFacilityHandleContext;
import com.hedera.node.app.integration.infra.ReplayFacilityTransactionDispatcher;
import com.hedera.node.app.integration.infra.RecordingName;
import com.hedera.node.app.service.mono.config.HederaNumbers;
import com.hedera.node.app.service.mono.context.annotations.CompositeProps;
import com.hedera.node.app.service.mono.context.properties.BootstrapProperties;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.replay.ReplayAssetRecording;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.validation.StandardizedAttributeValidator;
import com.hedera.node.app.workflows.handle.validation.StandardizedExpiryValidator;
import com.hedera.test.mocks.MockAccountNumbers;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.nio.file.Paths;
import java.util.function.LongSupplier;
import javax.inject.Singleton;

@Module
public interface ReplayFacilityModule {

    @Binds
    @Singleton
    HandleContext bindHandleContext(ReplayFacilityHandleContext context);

    @Binds
    @Singleton
    AttributeValidator bindAttributeValidator(StandardizedAttributeValidator attributeValidator);

    @Binds
    @Singleton
    TransactionDispatcher bindTransactionDispatcher(ReplayFacilityTransactionDispatcher transactionDispatcher);

    @Provides
    @Singleton
    static HederaAccountNumbers provideHederaAccountNumbers() {
        return new MockAccountNumbers();
    }

    @Provides
    @Singleton
    static ReplayAssetRecording provideAssetRecording(@NonNull final @RecordingName String recordingName) {
        final var baseAssetDir = Paths.get(REPLAY_ASSETS_DIR, recordingName);
        // Skip the hedera-node/ prefix, since we're already running a hedera-app/ unit test
        final var testAssetDir = baseAssetDir.subpath(1, baseAssetDir.getNameCount());
        return new ReplayAssetRecording(testAssetDir.toFile());
    }

    @Provides
    @Singleton
    static LongSupplier provideConsensusSecondNow(@NonNull final ReplayAdvancingConsensusNow consensusNow) {
        return () -> consensusNow.get().getEpochSecond();
    }

    @Provides
    @Singleton
    @CompositeProps
    static PropertySource provideCompositeProps() {
        return new BootstrapProperties(false);
    }

    @Provides
    @Singleton
    static ExpiryValidator provideExpiryValidator(
            @NonNull final InMemoryWritableStoreFactory storeFactory,
            @NonNull final AttributeValidator attributeValidator,
            @NonNull final LongSupplier consensusSecondNow,
            @NonNull final HederaNumbers hederaNumbers) {
        return new StandardizedExpiryValidator(
                id -> {
                    final var accounts = storeFactory
                            .getServiceStates()
                            .get(TokenService.NAME)
                            .<EntityNum, Account>get(TokenServiceImpl.ACCOUNTS_KEY);
                    final var autoRenewAccount = accounts.get(id.asEntityNum());
                    validateTrue(autoRenewAccount != null, INVALID_AUTORENEW_ACCOUNT);
                    validateFalse(autoRenewAccount.deleted(), ACCOUNT_DELETED);
                },
                attributeValidator,
                consensusSecondNow,
                hederaNumbers);
    }
}
