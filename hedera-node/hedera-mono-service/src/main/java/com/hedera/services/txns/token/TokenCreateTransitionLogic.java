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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.token.validators.CreateChecks;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Provides the state transition for token creation. */
@Singleton
public class TokenCreateTransitionLogic implements TransitionLogic {
    private final TransactionContext txnCtx;
    private final CreateLogic createLogic;
    private final CreateChecks createChecks;

    @Inject
    public TokenCreateTransitionLogic(
            final TransactionContext txnCtx,
            final CreateLogic createLogic,
            final CreateChecks createChecks) {
        this.txnCtx = txnCtx;
        this.createLogic = createLogic;
        this.createChecks = createChecks;
    }

    @Override
    public void doStateTransition() {
        final var op = txnCtx.accessor().getTxn().getTokenCreation();

        final var now = txnCtx.consensusTime().getEpochSecond();

        createLogic.create(now, txnCtx.activePayer(), op);
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return TransactionBody::hasTokenCreation;
    }

    @Override
    public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
        return createChecks.validatorForConsTime(txnCtx.consensusTime());
    }
}
