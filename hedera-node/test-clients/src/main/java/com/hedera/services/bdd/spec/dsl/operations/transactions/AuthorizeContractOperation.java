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

import static com.hedera.node.app.service.mono.pbj.PbjConverter.toPbj;
import static com.hedera.services.bdd.spec.keys.DefaultKeyGen.DEFAULT_KEY_GEN;
import static com.hedera.services.bdd.spec.keys.KeyShape.CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.ED25519;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.ED25519_ON;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

import com.hedera.services.bdd.SpecOperation;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.dsl.SpecEntity;
import com.hedera.services.bdd.spec.dsl.entities.SpecAccount;
import com.hedera.services.bdd.spec.dsl.entities.SpecContract;
import com.hedera.services.bdd.spec.dsl.entities.SpecToken;
import com.hedera.services.bdd.spec.dsl.operations.AbstractSpecOperation;
import com.hedera.services.bdd.spec.keys.KeyShape;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Authorizes a contract to act on behalf of an entity.
 */
public class AuthorizeContractOperation extends AbstractSpecOperation implements SpecOperation {
    private final String managedKeyName;
    private final SpecEntity target;
    private final SpecContract[] contracts;

    // non-standard ArrayList initializer
    @SuppressWarnings({"java:S3599", "java:S1171"})
    public AuthorizeContractOperation(@NonNull final SpecEntity target, @NonNull final SpecContract... contracts) {
        super(new ArrayList<>() {
            {
                add(target);
                addAll(List.of(contracts));
            }
        });
        this.target = target;
        this.contracts = requireNonNull(contracts);
        this.managedKeyName = target.name() + "_"
                + Arrays.stream(contracts).map(SpecContract::name).collect(joining("|")) + "ManagedKey";
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    protected SpecOperation computeDelegate(@NonNull final HapiSpec spec) {
        final var controller = managedKeyShape().signedWith(sigControl());
        final var key = spec.keys().generateSubjectTo(spec, controller, DEFAULT_KEY_GEN);
        spec.registry().saveKey(managedKeyName, key);
        return switch (target) {
            case SpecAccount account -> cryptoUpdate(account.name()).key(managedKeyName);
            case SpecToken token -> tokenUpdate(token.name()).adminKey(managedKeyName);
            default -> throw new IllegalStateException("Cannot authorize contracts for " + target);
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onSuccess(@NonNull final HapiSpec spec) {
        switch (target) {
            case SpecAccount account -> account.updateKeyFrom(
                    toPbj(spec.registry().getKey(managedKeyName)), spec);
            case SpecToken token -> {
                // (FUTURE) - update the admin key on the token model
            }
            default -> throw new IllegalStateException("Cannot authorize contract for " + target);
        }
    }

    private KeyShape managedKeyShape() {
        final var shapes = new KeyShape[contracts.length + 1];
        Arrays.fill(shapes, CONTRACT);
        shapes[0] = ED25519;
        return KeyShape.threshOf(1, shapes);
    }

    private Object sigControl() {
        final var controls = new Object[contracts.length + 1];
        controls[0] = ED25519_ON;
        for (var i = 0; i < contracts.length; i++) {
            controls[i + 1] = contracts[i].name();
        }
        return sigs(controls);
    }
}
