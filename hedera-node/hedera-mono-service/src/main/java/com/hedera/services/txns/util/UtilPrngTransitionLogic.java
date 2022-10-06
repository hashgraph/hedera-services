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
package com.hedera.services.txns.util;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Provides the state transition for generating a pseudorandom bytes or pseudorandom number. */
@Singleton
public class UtilPrngTransitionLogic implements TransitionLogic {
    private final TransactionContext txnCtx;
    private final PrngLogic prngLogic;

    @Inject
    public UtilPrngTransitionLogic(final TransactionContext txnCtx, final PrngLogic prngLogic) {
        this.txnCtx = txnCtx;
        this.prngLogic = prngLogic;
    }

    @Override
    public void doStateTransition() {
        final var op = txnCtx.accessor().getTxn().getUtilPrng();
        final var range = op.getRange();

        prngLogic.generatePseudoRandom(range);
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return TransactionBody::hasUtilPrng;
    }

    @Override
    public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
        return this::validate;
    }

    private ResponseCodeEnum validate(final TransactionBody prngTxn) {
        return prngLogic.validateSemantics(prngTxn);
    }
}
