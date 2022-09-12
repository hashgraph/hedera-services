/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEES_LIST_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FEE_SCHEDULE_KEY;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Provides the state transition for updating token fee schedule. */
@Singleton
public class TokenFeeScheduleUpdateTransitionLogic implements TransitionLogic {
    private final AccountStore accountStore;
    private final TypedTokenStore tokenStore;
    private final TransactionContext txnCtx;
    private final SigImpactHistorian sigImpactHistorian;
    private final GlobalDynamicProperties dynamicProperties;

    private Function<CustomFee, FcCustomFee> grpcFeeConverter = FcCustomFee::fromGrpc;

    @Inject
    public TokenFeeScheduleUpdateTransitionLogic(
            final TypedTokenStore tokenStore,
            final TransactionContext txnCtx,
            final AccountStore accountStore,
            final SigImpactHistorian sigImpactHistorian,
            final GlobalDynamicProperties dynamicProperties) {
        this.txnCtx = txnCtx;
        this.tokenStore = tokenStore;
        this.accountStore = accountStore;
        this.dynamicProperties = dynamicProperties;
        this.sigImpactHistorian = sigImpactHistorian;
    }

    @Override
    public void doStateTransition() {
        /* --- Translate from gRPC types --- */
        var op = txnCtx.accessor().getTxn().getTokenFeeScheduleUpdate();
        var grpcTokenId = op.getTokenId();
        var targetTokenId = Id.fromGrpcToken(grpcTokenId);

        /* --- Load the model objects --- */
        var token = tokenStore.loadToken(targetTokenId);
        validateTrue(token.hasFeeScheduleKey(), TOKEN_HAS_NO_FEE_SCHEDULE_KEY);

        /* --- Validate and initialize custom fees list --- */
        final var tooManyFees = op.getCustomFeesCount() > dynamicProperties.maxCustomFeesAllowed();
        validateFalse(tooManyFees, CUSTOM_FEES_LIST_TOO_LONG);
        final var customFees = op.getCustomFeesList().stream().map(grpcFeeConverter).toList();
        customFees.forEach(
                fee -> {
                    fee.validateWith(token, accountStore, tokenStore);
                    fee.nullOutCollector();
                });
        token.setCustomFees(customFees);

        /* --- Persist the updated models --- */
        sigImpactHistorian.markEntityChanged(targetTokenId.num());
        tokenStore.commitToken(token);
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return TransactionBody::hasTokenFeeScheduleUpdate;
    }

    @Override
    public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
        return this::validate;
    }

    private ResponseCodeEnum validate(TransactionBody txnBody) {
        final var op = txnBody.getTokenFeeScheduleUpdate();

        return op.hasTokenId() ? OK : INVALID_TOKEN_ID;
    }

    /* --- Only used by unit tests --- */
    void setGrpcFeeConverter(Function<CustomFee, FcCustomFee> grpcFeeConverter) {
        this.grpcFeeConverter = grpcFeeConverter;
    }
}
