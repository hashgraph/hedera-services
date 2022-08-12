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
import com.hedera.services.utils.accessors.TokenWipeAccessor;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Provides the state transition for wiping [part of] a token balance. */
@Singleton
public class TokenWipeTransitionLogic implements TransitionLogic {
    private final TransactionContext txnCtx;
    private final WipeLogic wipeLogic;

    @Inject
    public TokenWipeTransitionLogic(final TransactionContext txnCtx, final WipeLogic wipeLogic) {
        this.txnCtx = txnCtx;
        this.wipeLogic = wipeLogic;
    }

    @Override
    public void doStateTransition() {
        /* --- Translate from gRPC types --- */
        final var accessor = (TokenWipeAccessor) txnCtx.swirldsTxnAccessor().getDelegate();
        final var targetTokenId = accessor.targetToken();
        final var targetAccountId = accessor.accountToWipe();
        final var serialNumbersList = accessor.serialNums();
        final var amount = accessor.amount();

        wipeLogic.wipe(targetTokenId, targetAccountId, amount, serialNumbersList);
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return TransactionBody::hasTokenWipe;
    }
}
