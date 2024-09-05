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

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.dsl.entities.SpecAccount;
import com.hedera.services.bdd.spec.dsl.operations.AbstractSpecOperation;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * Deletes an account and transfers its balance to a beneficiary account.
 */
public class DeleteAccountOperation extends AbstractSpecOperation implements SpecOperation {
    private final SpecAccount target;
    private final SpecAccount beneficiary;

    public DeleteAccountOperation(@NonNull final SpecAccount target, @NonNull final SpecAccount beneficiary) {
        super(List.of(target, beneficiary));
        this.target = target;
        this.beneficiary = beneficiary;
    }

    @Override
    protected @NonNull SpecOperation computeDelegate(@NonNull final HapiSpec spec) {
        return cryptoDelete(target.name()).transfer(beneficiary.name());
    }

    @Override
    public String toString() {
        return "DeleteAccountOperation{" + "target=" + target + ", beneficiary=" + beneficiary + '}';
    }
}
