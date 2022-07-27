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
package com.hedera.services.txns.submission;

import com.hedera.services.txns.TransitionLogicLookup;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Tests if the specific HAPI function requested by a {@code Transaction} is well-formed; note that
 * these tests are always specific to the requested function and are repeated at consensus.
 *
 * <p>For more details, please see
 * https://github.com/hashgraph/hedera-services/blob/master/docs/transaction-prechecks.md
 */
@Singleton
public class SemanticPrecheck {
    private final TransitionLogicLookup transitionLogic;

    @Inject
    public SemanticPrecheck(TransitionLogicLookup transitionLogic) {
        this.transitionLogic = transitionLogic;
    }

    ResponseCodeEnum validate(
            TxnAccessor accessor,
            HederaFunctionality requiredFunction,
            ResponseCodeEnum failureType) {
        final var txn = accessor.getTxn();
        final var logic = transitionLogic.lookupFor(requiredFunction, txn);
        return logic.map(l -> l.validateSemantics(accessor)).orElse(failureType);
    }
}
