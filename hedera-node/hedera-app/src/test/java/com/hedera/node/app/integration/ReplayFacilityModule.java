package com.hedera.node.app.integration;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.integration.facilities.ReplayAdvancingConsensusNow;
import com.hedera.node.app.integration.infra.InMemoryWritableStoreFactory;
import com.hedera.node.app.integration.infra.ReplayFacilityHandleContext;
import com.hedera.node.app.integration.infra.ReplayFacilityTransactionDispatcher;
import com.hedera.node.app.service.mono.config.HederaNumbers;
import com.hedera.node.app.service.mono.context.annotations.CompositeProps;
import com.hedera.node.app.service.mono.context.properties.BootstrapProperties;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.replay.ReplayAssetRecording;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.spi.exceptions.HandleException;
import com.hedera.node.app.spi.fixtures.state.MapWritableStates;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.dispatcher.WritableStoreFactory;
import com.hedera.node.app.workflows.handle.validation.StandardizedAttributeValidator;
import com.hedera.node.app.workflows.handle.validation.StandardizedExpiryValidator;
import com.hedera.test.mocks.MockAccountNumbers;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Singleton;
import java.io.File;
import java.util.function.LongSupplier;

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hedera.node.app.spi.exceptions.HandleException.validateFalse;
import static com.hedera.node.app.spi.exceptions.HandleException.validateTrue;

@Module
public interface ReplayFacilityModule {
    String REPLAY_ASSETS_DIR = "src/test/resources/replay-assets";

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
    static ReplayAssetRecording provideAssetRecording() {
        return new ReplayAssetRecording(new File(REPLAY_ASSETS_DIR));
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
        return new StandardizedExpiryValidator(id -> {
            final var accounts = storeFactory.getServiceStates()
                    .get(TokenService.NAME)
                    .<EntityNum, MerkleAccount>get(TokenServiceImpl.ACCOUNTS_KEY);
            final var autoRenewAccount = accounts.get(id.asEntityNum());
            validateTrue(autoRenewAccount != null, INVALID_AUTORENEW_ACCOUNT);
            validateFalse(autoRenewAccount.isDeleted(), ACCOUNT_DELETED);
        }, attributeValidator, consensusSecondNow, hederaNumbers);
    }
}
