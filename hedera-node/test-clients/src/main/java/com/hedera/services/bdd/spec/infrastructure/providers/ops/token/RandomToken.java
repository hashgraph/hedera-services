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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.EntityNameProvider;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenCreate;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public class RandomToken implements OpProvider {
    private static final int FREEZE_KEY_INDEX = 4;
    private static final List<BiConsumer<HapiTokenCreate, String>> KEY_SETTERS = List.of(
            HapiTokenCreate::kycKey,
            HapiTokenCreate::wipeKey,
            HapiTokenCreate::adminKey,
            HapiTokenCreate::supplyKey,
            HapiTokenCreate::freezeKey);

    public static final int DEFAULT_CEILING_NUM = 100;
    public static final int DEFAULT_MAX_STRING_LEN = 100;
    public static final long DEFAULT_MAX_SUPPLY = 1_000;

    private int ceilingNum = DEFAULT_CEILING_NUM;
    private double kycKeyProb = 0.5;
    private double wipeKeyProb = 0.5;
    private double adminKeyProb = 0.5;
    private double supplyKeyProb = 0.5;
    private double freezeKeyProb = 0.5;
    private double autoRenewProb = 0.5;

    private final AtomicInteger opNo = new AtomicInteger();
    private final EntityNameProvider<Key> keys;
    private final RegistrySourcedNameProvider<TokenID> tokens;
    private final RegistrySourcedNameProvider<AccountID> accounts;

    private final ResponseCodeEnum[] permissibleOutcomes = standardOutcomesAnd(
            /* Auto-renew account might be deleted by the time our TokenCreate reaches consensus */
            INVALID_AUTORENEW_ACCOUNT,
            /* Treasury account might be deleted by the time our TokenCreate reaches consensus */
            INVALID_TREASURY_ACCOUNT_FOR_TOKEN);

    public RandomToken ceiling(int n) {
        ceilingNum = n;
        return this;
    }

    public RandomToken(
            EntityNameProvider<Key> keys,
            RegistrySourcedNameProvider<TokenID> tokens,
            RegistrySourcedNameProvider<AccountID> accounts) {
        this.keys = keys;
        this.tokens = tokens;
        this.accounts = accounts;
    }

    @Override
    public Optional<HapiSpecOperation> get() {
        if (tokens.numPresent() >= ceilingNum) {
            return Optional.empty();
        }

        int id = opNo.getAndIncrement();
        HapiTokenCreate op = tokenCreate(my("token" + id))
                .advertisingCreation()
                .hasPrecheckFrom(STANDARD_PERMISSIBLE_PRECHECKS)
                .hasKnownStatusFrom(permissibleOutcomes);

        var prefix = randomlyConfigureKeys(op);
        op.setTokenPrefix(prefix);

        randomlyConfigureSupply(op);
        randomlyConfigureAutoRenew(op);
        randomlyConfigureStrings(op);

        return Optional.of(op);
    }

    private void randomlyConfigureStrings(HapiTokenCreate op) {
        op.name(randomUppercase(1 + BASE_RANDOM.nextInt(DEFAULT_MAX_STRING_LEN)));
        op.symbol(randomUppercase(1 + BASE_RANDOM.nextInt(DEFAULT_MAX_STRING_LEN)));
    }

    private void randomlyConfigureSupply(HapiTokenCreate op) {
        op.initialSupply(BASE_RANDOM.nextLong(0, DEFAULT_MAX_SUPPLY));
        op.decimals(BASE_RANDOM.nextInt(0, Integer.MAX_VALUE));
    }

    private void randomlyConfigureAutoRenew(HapiTokenCreate op) {
        if (BASE_RANDOM.nextDouble() < autoRenewProb) {
            var account = accounts.getQualifying();
            account.ifPresent(op::autoRenewAccount);
        }
    }

    private static final int kycFlagIndex = 1;
    private static final int wipeFlagIndex = 2;
    private static final int adminFlagIndex = 3;
    private static final int supplyFlagIndex = 4;
    private static final int freezeFlagIndex = 5;

    static boolean wasCreatedWithFreeze(String token) {
        return token.charAt(freezeFlagIndex) == 'Y';
    }

    static boolean wasCreatedWithWipe(String token) {
        return token.charAt(wipeFlagIndex) == 'Y';
    }

    static boolean wasCreatedWithAdmin(String token) {
        return token.charAt(adminFlagIndex) == 'Y';
    }

    static boolean wasCreatedWithSupply(String token) {
        return token.charAt(supplyFlagIndex) == 'Y';
    }

    static boolean wasCreatedWithKyc(String token) {
        return token.charAt(kycFlagIndex) == 'Y';
    }

    private String randomlyConfigureKeys(HapiTokenCreate op) {
        double[] probs = new double[] {kycKeyProb, wipeKeyProb, adminKeyProb, supplyKeyProb, freezeKeyProb};

        var sb = new StringBuilder("[");
        for (int i = 0; i < probs.length; i++) {
            if (BASE_RANDOM.nextDouble() < probs[i]) {
                var key = keys.getQualifying();
                if (key.isPresent()) {
                    if (i == FREEZE_KEY_INDEX) {
                        op.freezeDefault(BASE_RANDOM.nextBoolean());
                    }
                    KEY_SETTERS.get(i).accept(op, key.get());
                    sb.append("Y");
                } else {
                    sb.append("N");
                }
            } else {
                sb.append("N");
            }
        }
        return sb.append("]").toString();
    }

    private String my(String opName) {
        return unique(opName, RandomToken.class);
    }
}
