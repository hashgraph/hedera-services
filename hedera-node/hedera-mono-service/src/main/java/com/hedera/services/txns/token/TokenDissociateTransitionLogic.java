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
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Provides the state transition for dissociating tokens from an account. */
@Singleton
public class TokenDissociateTransitionLogic implements TransitionLogic {
    private final TransactionContext txnCtx;
    private final DissociateLogic dissociateLogic;

    @Inject
    public TokenDissociateTransitionLogic(
            TransactionContext txnCtx, DissociateLogic dissociateLogic) {
        this.txnCtx = txnCtx;
        this.dissociateLogic = dissociateLogic;
    }

    @Override
    public void doStateTransition() {
        /* --- Translate from gRPC types --- */
        var op = txnCtx.accessor().getTxn().getTokenDissociate();
        final var accountId = Id.fromGrpcAccount(op.getAccount());
        dissociateLogic.dissociate(accountId, op.getTokensList());
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return TransactionBody::hasTokenDissociate;
    }

    @Override
    public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
        return this::validate;
    }

    public ResponseCodeEnum validate(TransactionBody txnBody) {
        return dissociateLogic.validateSyntax(txnBody);
    }
}
