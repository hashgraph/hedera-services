/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.token;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAssociateToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDissociateFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFeeScheduleUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGrantKycToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenPause;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenRevokeKycFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnfreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnpause;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUpdate;

import com.hedera.services.fees.annotations.FunctionKey;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.token.process.Dissociation;
import com.hedera.services.txns.token.process.DissociationFactory;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;
import java.util.List;
import java.util.function.Predicate;
import javax.inject.Singleton;

@Module
public final class TokenLogicModule {
    @Provides
    @IntoMap
    @FunctionKey(TokenCreate)
    public static List<TransitionLogic> provideTokenCreateLogic(
            final TokenCreateTransitionLogic tokenCreateLogic) {
        return List.of(tokenCreateLogic);
    }

    @Provides
    @IntoMap
    @FunctionKey(TokenUpdate)
    public static List<TransitionLogic> provideTokenUpdateLogic(
            final TokenUpdateTransitionLogic tokenUpdateLogic) {
        return List.of(tokenUpdateLogic);
    }

    @Provides
    @IntoMap
    @FunctionKey(TokenFeeScheduleUpdate)
    public static List<TransitionLogic> provideFeesUpdateLogic(
            final TokenFeeScheduleUpdateTransitionLogic feesUpdateLogic) {
        return List.of(feesUpdateLogic);
    }

    @Provides
    @IntoMap
    @FunctionKey(TokenFreezeAccount)
    public static List<TransitionLogic> provideTokenFreezeLogic(
            final TokenFreezeTransitionLogic tokenFreezeLogic) {
        return List.of(tokenFreezeLogic);
    }

    @Provides
    @IntoMap
    @FunctionKey(TokenUnfreezeAccount)
    public static List<TransitionLogic> provideTokenUnfreezeLogic(
            final TokenUnfreezeTransitionLogic tokenUnfreezeLogic) {
        return List.of(tokenUnfreezeLogic);
    }

    @Provides
    @IntoMap
    @FunctionKey(TokenGrantKycToAccount)
    public static List<TransitionLogic> provideTokenGrantLogic(
            final TokenGrantKycTransitionLogic tokenGrantLogic) {
        return List.of(tokenGrantLogic);
    }

    @Provides
    @IntoMap
    @FunctionKey(TokenRevokeKycFromAccount)
    public static List<TransitionLogic> provideTokenRevokeLogic(
            final TokenRevokeKycTransitionLogic tokenRevokeLogic) {
        return List.of(tokenRevokeLogic);
    }

    @Provides
    @IntoMap
    @FunctionKey(TokenDelete)
    public static List<TransitionLogic> provideTokenDeleteLogic(
            final TokenDeleteTransitionLogic tokenDeleteLogic) {
        return List.of(tokenDeleteLogic);
    }

    @Provides
    @IntoMap
    @FunctionKey(TokenMint)
    public static List<TransitionLogic> provideTokenMintLogic(
            final TokenMintTransitionLogic tokenMintLogic) {
        return List.of(tokenMintLogic);
    }

    @Provides
    @IntoMap
    @FunctionKey(TokenBurn)
    public static List<TransitionLogic> provideTokenBurnLogic(
            final TokenBurnTransitionLogic tokenBurnLogic) {
        return List.of(tokenBurnLogic);
    }

    @Provides
    @IntoMap
    @FunctionKey(TokenAccountWipe)
    public static List<TransitionLogic> provideTokenWipeLogic(
            final TokenWipeTransitionLogic tokenWipeLogic) {
        return List.of(tokenWipeLogic);
    }

    @Provides
    @IntoMap
    @FunctionKey(TokenAssociateToAccount)
    public static List<TransitionLogic> provideTokenAssocLogic(
            final TokenAssociateTransitionLogic tokenAssocLogic) {
        return List.of(tokenAssocLogic);
    }

    @Provides
    @IntoMap
    @FunctionKey(TokenDissociateFromAccount)
    public static List<TransitionLogic> provideTokenDissocLogic(
            final TokenDissociateTransitionLogic tokenDissocLogic) {
        return List.of(tokenDissocLogic);
    }

    @Provides
    @IntoMap
    @FunctionKey(TokenPause)
    public static List<TransitionLogic> provideTokenPauseLogic(
            TokenPauseTransitionLogic tokenPauseLogic) {
        return List.of(tokenPauseLogic);
    }

    @Provides
    @IntoMap
    @FunctionKey(TokenUnpause)
    public static List<TransitionLogic> provideTokenUnpauseLogic(
            TokenUnpauseTransitionLogic tokenUnpauseLogic) {
        return List.of(tokenUnpauseLogic);
    }

    @Provides
    @Singleton
    public static Predicate<TokenUpdateTransactionBody> provideAffectsExpiryOnly() {
        return HederaTokenStore::affectsExpiryAtMost;
    }

    @Provides
    @Singleton
    public static DissociationFactory provideDissociationFactory() {
        return Dissociation::loadFrom;
    }

    private TokenLogicModule() {
        throw new UnsupportedOperationException("Dagger2 module");
    }
}
