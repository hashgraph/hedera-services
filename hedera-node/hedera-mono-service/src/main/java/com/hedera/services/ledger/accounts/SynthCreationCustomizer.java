/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.ledger.accounts;

import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Customizes a HAPI contract create transaction with the "inheritable" properties of a parent
 * account; exists to simplify re-use of {@link ContractCustomizer}.
 */
@Singleton
public class SynthCreationCustomizer {
    private final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;

    @Inject
    public SynthCreationCustomizer(
            final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger) {
        this.accountsLedger = accountsLedger;
    }

    /**
     * Given a synthetic HAPI contract create transaction, updates it to reflect the inheritable
     * properties of the given caller account.
     *
     * @param synthCreate a HAPI contract creation
     * @param callerId a known caller account
     * @return the HAPI transaction customized with the caller's inheritable properties
     */
    public TransactionBody customize(final TransactionBody synthCreate, final AccountID callerId) {
        final var customizer = ContractCustomizer.fromSponsorContract(callerId, accountsLedger);
        final var customBuilder = synthCreate.getContractCreateInstance().toBuilder();
        customizer.customizeSynthetic(customBuilder);
        return synthCreate.toBuilder().setContractCreateInstance(customBuilder).build();
    }
}
