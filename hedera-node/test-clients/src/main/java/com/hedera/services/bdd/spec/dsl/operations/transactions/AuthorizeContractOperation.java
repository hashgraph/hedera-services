/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.dsl.operations.transactions;

import static com.hedera.services.bdd.spec.keys.DefaultKeyGen.DEFAULT_KEY_GEN;
import static com.hedera.services.bdd.spec.keys.KeyShape.CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.ED25519;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.ED25519_ON;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;

import com.hedera.services.bdd.SpecOperation;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.dsl.SpecEntity;
import com.hedera.services.bdd.spec.dsl.entities.SpecAccount;
import com.hedera.services.bdd.spec.dsl.entities.SpecContract;
import com.hedera.services.bdd.spec.dsl.entities.SpecToken;
import com.hedera.services.bdd.spec.dsl.operations.AbstractSpecOperation;
import com.hedera.services.bdd.spec.keys.KeyShape;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * Authorizes a contract to act on behalf of an entity.
 */
public class AuthorizeContractOperation extends AbstractSpecOperation implements SpecOperation {
    private static final KeyShape MANAGED_KEY_SHAPE = KeyShape.threshOf(1, ED25519, CONTRACT);

    private final SpecEntity target;
    private final SpecContract contract;

    public AuthorizeContractOperation(@NonNull final SpecEntity target, @NonNull final SpecContract contract) {
        super(List.of(target, contract));
        this.target = target;
        this.contract = contract;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    protected SpecOperation computeDelegate(@NonNull final HapiSpec spec) {
        final var newKeyControl = MANAGED_KEY_SHAPE.signedWith(sigs(ED25519_ON, contract.name()));
        final var key = spec.keys().generateSubjectTo(spec, newKeyControl, DEFAULT_KEY_GEN);
        final var newKeyName = target.name() + "_" + contract.name() + "ManagedKey";
        spec.registry().saveKey(newKeyName, key);
        return switch (target) {
            case SpecAccount account -> cryptoUpdate(account.name()).key(newKeyName);
            case SpecToken token -> tokenUpdate(token.name()).adminKey(newKeyName);
            default -> throw new IllegalStateException("Cannot authorize contract for " + target);
        };
    }
}
