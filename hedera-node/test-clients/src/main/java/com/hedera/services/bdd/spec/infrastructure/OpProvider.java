/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.infrastructure;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.LIVE_HASH_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.stream.Stream;

public interface OpProvider {
    ResponseCodeEnum[] STANDARD_PERMISSIBLE_QUERY_PRECHECKS = {
        OK, BUSY, INSUFFICIENT_TX_FEE, PLATFORM_TRANSACTION_NOT_CREATED
    };

    ResponseCodeEnum[] STANDARD_PERMISSIBLE_PRECHECKS = {
        OK,
        BUSY,
        INVALID_SIGNATURE,
        DUPLICATE_TRANSACTION,
        INVALID_PAYER_SIGNATURE,
        INSUFFICIENT_PAYER_BALANCE,
        PLATFORM_TRANSACTION_NOT_CREATED
    };

    ResponseCodeEnum[] STANDARD_PERMISSIBLE_OUTCOMES = {
        SUCCESS, LIVE_HASH_NOT_FOUND, INVALID_SIGNATURE, INSUFFICIENT_PAYER_BALANCE, UNKNOWN
    };

    default List<HapiSpecOperation> suggestedInitializers() {
        return Collections.emptyList();
    }

    Optional<HapiSpecOperation> get();

    default ResponseCodeEnum[] standardQueryPrechecksAnd(ResponseCodeEnum... more) {
        return plus(STANDARD_PERMISSIBLE_QUERY_PRECHECKS, more);
    }

    default ResponseCodeEnum[] standardPrechecksAnd(ResponseCodeEnum... more) {
        return plus(STANDARD_PERMISSIBLE_PRECHECKS, more);
    }

    default ResponseCodeEnum[] standardOutcomesAnd(ResponseCodeEnum... more) {
        return plus(STANDARD_PERMISSIBLE_OUTCOMES, more);
    }

    default ResponseCodeEnum[] plus(ResponseCodeEnum[] some, ResponseCodeEnum[] more) {
        return Stream.concat(Stream.of(some), Stream.of(more)).toArray(n -> new ResponseCodeEnum[n]);
    }

    default String unique(String opName, Class<?> providerType) {
        return opName + "-" + providerType.getSimpleName();
    }

    String UNIQUE_PAYER_ACCOUNT = "uniquePayerAccount";
    long UNIQUE_PAYER_ACCOUNT_INITIAL_BALANCE = 500_000_000_000L;
    long TRANSACTION_FEE = 50_000_000_000L;
    SplittableRandom BASE_RANDOM = new SplittableRandom();
}
