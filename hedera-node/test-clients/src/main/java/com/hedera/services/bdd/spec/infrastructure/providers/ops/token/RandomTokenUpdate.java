/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.infrastructure.providers.ops.token;

import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUppercase;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_KYC_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_WIPE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NAME_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_SYMBOL_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.EntityNameProvider;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenUpdate;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class RandomTokenUpdate implements OpProvider {
    private final List<Consumer<HapiTokenUpdate>> randomUpdates = List.of(
            this::randomKeysUpdate,
            this::randomNameUpdate,
            this::randomSymbolUpdate,
            this::randomTreasuryUpdate,
            this::randomAutoRenewPeriodUpdate,
            this::randomAutoRenewAccountUpdate);

    private static final List<BiConsumer<HapiTokenUpdate, String>> KEY_SETTERS = List.of(
            HapiTokenUpdate::kycKey,
            HapiTokenUpdate::wipeKey,
            HapiTokenUpdate::adminKey,
            HapiTokenUpdate::supplyKey,
            HapiTokenUpdate::freezeKey);

    private static final int DEFAULT_MAX_STRING_LEN = 100;
    private static final long MAX_PERIOD = 1_000_000_000;

    private static double fieldUpdateProb = 0.5;

    private final EntityNameProvider<Key> keys;
    private final RegistrySourcedNameProvider<TokenID> tokens;
    private final RegistrySourcedNameProvider<AccountID> accounts;

    private final ResponseCodeEnum[] permissibleOutcomes = standardOutcomesAnd(
            INVALID_KYC_KEY,
            INVALID_WIPE_KEY,
            INVALID_SIGNATURE,
            TOKEN_WAS_DELETED,
            INVALID_ADMIN_KEY,
            INVALID_FREEZE_KEY,
            INVALID_SUPPLY_KEY,
            TOKEN_IS_IMMUTABLE,
            TOKEN_NAME_TOO_LONG,
            TOKEN_SYMBOL_TOO_LONG,
            INVALID_RENEWAL_PERIOD,
            INVALID_AUTORENEW_ACCOUNT,
            INVALID_TREASURY_ACCOUNT_FOR_TOKEN);

    public RandomTokenUpdate(
            EntityNameProvider<Key> keys,
            RegistrySourcedNameProvider<TokenID> tokens,
            RegistrySourcedNameProvider<AccountID> accounts) {
        this.keys = keys;
        this.tokens = tokens;
        this.accounts = accounts;
    }

    @Override
    public Optional<HapiSpecOperation> get() {
        Optional<String> token = tokens.getQualifying();
        if (token.isEmpty()) {
            return Optional.empty();
        }

        var op = tokenUpdate(token.get())
                .hasPrecheckFrom(STANDARD_PERMISSIBLE_PRECHECKS)
                .hasKnownStatusFrom(permissibleOutcomes);

        randomUpdates.forEach(o -> o.accept(op));

        return Optional.of(op);
    }

    private void randomNameUpdate(HapiTokenUpdate op) {
        if (BASE_RANDOM.nextDouble() < fieldUpdateProb) {
            op.name(randomUppercase(1 + BASE_RANDOM.nextInt(DEFAULT_MAX_STRING_LEN)));
        }
    }

    private void randomSymbolUpdate(HapiTokenUpdate op) {
        if (BASE_RANDOM.nextDouble() < fieldUpdateProb) {
            op.symbol(randomUppercase(1 + BASE_RANDOM.nextInt(DEFAULT_MAX_STRING_LEN)));
        }
    }

    private void randomTreasuryUpdate(HapiTokenUpdate op) {
        if (BASE_RANDOM.nextDouble() < fieldUpdateProb) {
            Optional<String> newTreasury = accounts.getQualifying();
            newTreasury.ifPresent(op::treasury);
        }
    }

    private void randomAutoRenewAccountUpdate(HapiTokenUpdate op) {
        if (BASE_RANDOM.nextDouble() < fieldUpdateProb) {
            Optional<String> newAutoRenewAccount = accounts.getQualifying();
            newAutoRenewAccount.ifPresent(op::autoRenewAccount);
        }
    }

    private void randomAutoRenewPeriodUpdate(HapiTokenUpdate op) {
        if (BASE_RANDOM.nextDouble() < fieldUpdateProb) {
            op.autoRenewPeriod(BASE_RANDOM.nextLong(MAX_PERIOD));
        }
    }

    private void randomKeysUpdate(HapiTokenUpdate op) {
        for (BiConsumer<HapiTokenUpdate, String> keySetter : KEY_SETTERS) {
            if (BASE_RANDOM.nextDouble() < fieldUpdateProb) {
                var key = keys.getQualifying();
                key.ifPresent(s -> keySetter.accept(op, s));
            }
        }
    }
}
