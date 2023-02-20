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

package com.hedera.services.bdd.spec.infrastructure.providers.ops.token;

import static com.hedera.services.bdd.spec.infrastructure.providers.ops.token.RandomTokenDissociation.explicit;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.listeners.TokenAccountRegistryRel;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RandomTokenTransfer implements OpProvider {
    private static final Logger log = LogManager.getLogger(RandomTokenTransfer.class);

    private final RegistrySourcedNameProvider<TokenAccountRegistryRel> tokenRels;

    public RandomTokenTransfer(RegistrySourcedNameProvider<TokenAccountRegistryRel> tokenRels) {
        this.tokenRels = tokenRels;
    }

    private final ResponseCodeEnum[] permissibleOutcomes = standardOutcomesAnd(
            INSUFFICIENT_TOKEN_BALANCE,
            ACCOUNT_FROZEN_FOR_TOKEN,
            ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN,
            TOKEN_NOT_ASSOCIATED_TO_ACCOUNT,
            TOKEN_WAS_DELETED);

    @Override
    public Optional<HapiSpecOperation> get() {
        var xferRel = tokenRels.getQualifying();
        if (xferRel.isEmpty()) {
            return Optional.empty();
        }

        HapiSpecOperation op;
        var rel = explicit(xferRel.get());
        var token = rel.getRight();
        if (BASE_RANDOM.nextBoolean()) {
            op = cryptoTransfer(moving(1, token)
                            .between(spec -> spec.registry().getTreasury(token), ignore -> rel.getLeft()))
                    .hasPrecheckFrom(STANDARD_PERMISSIBLE_PRECHECKS)
                    .hasKnownStatusFrom(permissibleOutcomes);
        } else {
            op = cryptoTransfer(moving(1, token).between(ignore -> rel.getLeft(), spec -> spec.registry()
                            .getTreasury(token)))
                    .hasPrecheckFrom(STANDARD_PERMISSIBLE_PRECHECKS)
                    .hasKnownStatusFrom(permissibleOutcomes)
                    .showingResolvedStatus();
        }

        return Optional.of(op);
    }
}
