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

package com.hedera.services.bdd.spec.dsl.entities;

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.headlongAddressOf;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.services.bdd.junit.hedera.HederaNetwork;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.dsl.EvmAddressableEntity;
import com.hedera.services.bdd.spec.dsl.SpecEntity;
import com.hedera.services.bdd.spec.dsl.operations.transactions.DeleteAccountOperation;
import com.hedera.services.bdd.spec.dsl.operations.transactions.GetBalanceOperation;
import com.hedera.services.bdd.spec.dsl.utils.KeyMetadata;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoCreate;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Represents a Hedera account that may exist on one or more target networks and be
 * registered with more than one {@link HapiSpec} if desired.
 */
public class SpecAccount extends AbstractSpecEntity<HapiCryptoCreate, Account>
        implements SpecEntity, EvmAddressableEntity {
    private final Account.Builder builder = Account.newBuilder();

    public SpecAccount(@NonNull final String name) {
        super(name);
    }

    /**
     * Returns a builder for the model account to be created, or throws if the entity is locked.
     *
     * @return the builder
     */
    public Account.Builder builder() {
        throwIfLocked();
        return builder;
    }

    /**
     * Returns an operation to delete the account, transferring its balance to the given beneficiary.
     *
     * @param beneficiary the beneficiary
     * @return the operation
     */
    public DeleteAccountOperation deleteWithTransfer(@NonNull final SpecAccount beneficiary) {
        requireNonNull(beneficiary);
        return new DeleteAccountOperation(this, beneficiary);
    }

    /**
     * Returns an operation to get the balance of the account.
     *
     * @return the operation
     */
    public GetBalanceOperation getBalance() {
        return new GetBalanceOperation(this);
    }

    /**
     * Gets the account model for the given network, or throws if it doesn't exist.
     *
     * @param network the network
     * @return the account model
     */
    public Account accountOrThrow(@NonNull final HederaNetwork network) {
        return modelOrThrow(network);
    }

    @Override
    public Address addressOn(@NonNull final HederaNetwork network) {
        requireNonNull(network);
        final var networkAccount = accountOrThrow(network);
        return headlongAddressOf(networkAccount);
    }

    @Override
    public String toString() {
        return "SpecAccount{" + "name='" + name + '\'' + '}';
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Creation<HapiCryptoCreate, Account> newCreation(@NonNull final HapiSpec spec) {
        final var model = builder.build();
        final var op = cryptoCreate(name).balance(model.tinybarBalance());
        return new Creation<>(op, model);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Result<Account> resultForSuccessful(
            @NonNull final Creation<HapiCryptoCreate, Account> creation, @NonNull final HapiSpec spec) {
        final var newAccountNum = creation.op().numOfCreatedAccount();
        final var protoKey = creation.op().getKey();
        final var keyMetadata = KeyMetadata.from(protoKey, spec);
        return new Result<>(
                creation.model()
                        .copyBuilder()
                        .accountId(
                                AccountID.newBuilder().accountNum(newAccountNum).build())
                        .key(keyMetadata.pbjKey())
                        .build(),
                siblingSpec -> {
                    keyMetadata.registerAs(name, siblingSpec);
                    siblingSpec
                            .registry()
                            .saveAccountId(
                                    name,
                                    com.hederahashgraph.api.proto.java.AccountID.newBuilder()
                                            .setAccountNum(newAccountNum)
                                            .build());
                    if (creation.model().receiverSigRequired()) {
                        siblingSpec.registry().saveSigRequirement(name, Boolean.TRUE);
                    }
                });
    }
}
