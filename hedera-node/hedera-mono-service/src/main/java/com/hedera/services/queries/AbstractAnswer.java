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
package com.hedera.services.queries;

import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Transaction;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

public abstract class AbstractAnswer implements AnswerService {
    private final HederaFunctionality function;
    private final Function<Query, Transaction> paymentExtractor;
    private final Function<Query, ResponseType> responseTypeExtractor;
    private final Function<Response, ResponseCodeEnum> statusExtractor;
    private final BiFunction<Query, StateView, ResponseCodeEnum> validityCheck;

    public AbstractAnswer(
            HederaFunctionality function,
            Function<Query, Transaction> paymentExtractor,
            Function<Query, ResponseType> responseTypeExtractor,
            Function<Response, ResponseCodeEnum> statusExtractor,
            BiFunction<Query, StateView, ResponseCodeEnum> validityCheck) {
        this.function = function;
        this.validityCheck = validityCheck;
        this.statusExtractor = statusExtractor;
        this.paymentExtractor = paymentExtractor;
        this.responseTypeExtractor = responseTypeExtractor;
    }

    @Override
    public boolean needsAnswerOnlyCost(Query query) {
        return COST_ANSWER == responseTypeExtractor.apply(query);
    }

    @Override
    public boolean requiresNodePayment(Query query) {
        return typicallyRequiresNodePayment(responseTypeExtractor.apply(query));
    }

    @Override
    public ResponseCodeEnum checkValidity(Query query, StateView view) {
        return validityCheck.apply(query, view);
    }

    @Override
    public HederaFunctionality canonicalFunction() {
        return function;
    }

    @Override
    public ResponseCodeEnum extractValidityFrom(Response response) {
        return statusExtractor.apply(response);
    }

    @Override
    public Optional<SignedTxnAccessor> extractPaymentFrom(Query query) {
        var paymentTxn = paymentExtractor.apply(query);

        return Optional.of(SignedTxnAccessor.uncheckedFrom(paymentTxn));
    }
}
