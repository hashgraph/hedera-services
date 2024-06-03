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
import static com.hedera.services.bdd.spec.dsl.utils.DslUtils.atMostOnce;
import static com.hedera.services.bdd.spec.keys.KeyFactory.KeyType.SIMPLE;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.services.bdd.junit.hedera.HederaNetwork;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.dsl.EvmAddressableEntity;
import com.hedera.services.bdd.spec.dsl.SpecEntity;
import com.hedera.services.bdd.spec.dsl.operations.queries.GetTokenInfoOperation;
import com.hedera.services.bdd.spec.dsl.operations.transactions.AuthorizeContractOperation;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenCreate;
import com.hederahashgraph.api.proto.java.ContractID;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Represents a Hedera token that may exist on one or more target networks and be
 * registered with more than one {@link HapiSpec} if desired.
 */
public class SpecToken extends AbstractSpecEntity<HapiTokenCreate, Token> implements SpecEntity, EvmAddressableEntity {
    public static final String DEFAULT_TREASURY_NAME_SUFFIX = "Treasury";

    protected final Token.Builder builder = Token.newBuilder();

    private long initialSupply = 0;

    @Nullable
    private SpecAccount autoRenewAccount;

    @Nullable
    private SpecAccount treasuryAccount;

    @Nullable
    private Set<SpecTokenKey> keys;

    public SpecToken(@NonNull final String name, @NonNull final TokenType tokenType) {
        super(name);
        builder.tokenType(tokenType);
    }

    /**
     * Returns an operation that retrieves the token information.
     *
     * @return the operation
     */
    public GetTokenInfoOperation getInfo() {
        return new GetTokenInfoOperation(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SpecEntity> prerequisiteEntities() {
        final List<SpecEntity> prerequisites = new ArrayList<>();
        if (treasuryAccount == null) {
            treasuryAccount = new SpecAccount(name + DEFAULT_TREASURY_NAME_SUFFIX);
        }
        prerequisites.add(treasuryAccount);
        if (autoRenewAccount != null) {
            prerequisites.add(autoRenewAccount);
        }
        return prerequisites;
    }

    /**
     * Returns a builder for the model token to be created, or throws if the entity is locked.
     *
     * @return the builder
     */
    public Token.Builder builder() {
        throwIfLocked();
        return builder;
    }

    /**
     * Gets the token model for the given network, or throws if it doesn't exist.
     *
     * @param network the network
     * @return the token model
     */
    public Token tokenOrThrow(@NonNull final HederaNetwork network) {
        return modelOrThrow(network);
    }

    /**
     * Sets the account that will pay for auto-renewals of the token.
     *
     * @param autoRenewAccount the account
     */
    public void setAutoRenewAccount(@NonNull final SpecAccount autoRenewAccount) {
        this.autoRenewAccount = requireNonNull(autoRenewAccount);
    }

    /**
     * Sets the types of keys to associate with the token.
     *
     * @param keys the types of keys to associate with the token
     */
    public void setKeys(@NonNull final Set<SpecTokenKey> keys) {
        this.keys = requireNonNull(keys);
    }

    /**
     * Returns an operation to authorize the given contract to act on behalf of this token.
     *
     * @param contract the contract to authorize
     * @return the operation
     */
    public AuthorizeContractOperation authorizeContract(@NonNull final SpecContract contract) {
        requireNonNull(contract);
        return new AuthorizeContractOperation(this, contract);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Address addressOn(@NonNull HederaNetwork network) {
        requireNonNull(network);
        final var networkToken = tokenOrThrow(network);
        return headlongAddressOf(networkToken.tokenIdOrThrow());
    }

    @Override
    public String toString() {
        return "SpecToken{" + "name='" + name + '\'' + '}';
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Creation<HapiTokenCreate, Token> newCreation(@NonNull final HapiSpec spec) {
        final var model = builder.build();
        final var op = tokenCreate(name)
                .tokenType(com.hederahashgraph.api.proto.java.TokenType.forNumber(
                        model.tokenType().protoOrdinal()))
                .treasury(requireNonNull(treasuryAccount).name())
                .initialSupply(initialSupply);
        if (autoRenewAccount != null) {
            op.autoRenewAccount(autoRenewAccount.name());
        }
        if (keys != null) {
            keys.forEach(key -> generateKeyAndCustomizeOp(key, spec, op));
        }
        return new Creation<>(op, model);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Result<Token> resultForSuccessful(
            @NonNull final Creation<HapiTokenCreate, Token> creation, @NonNull final HapiSpec spec) {
        final var newTokenNum = creation.op().numOfCreatedTokenOrThrow();
        final var allKeyMetadata = creation.op().allCreatedKeyMetadata(spec);
        return new Result<>(
                creation.model()
                        .copyBuilder()
                        .tokenId(TokenID.newBuilder().tokenNum(newTokenNum).build())
                        .build(),
                atMostOnce(siblingSpec -> {
                    allKeyMetadata.forEach(keyMetadata -> keyMetadata.registerAs(name, siblingSpec));
                    siblingSpec
                            .registry()
                            .saveTokenId(
                                    name,
                                    com.hederahashgraph.api.proto.java.TokenID.newBuilder()
                                            .setTokenNum(newTokenNum)
                                            .build());
                    creation.op().registerAttributes(siblingSpec);
                    siblingSpec
                            .registry()
                            .saveContractId(
                                    name,
                                    ContractID.newBuilder()
                                            .setContractNum(newTokenNum)
                                            .build());
                }));
    }

    private void generateKeyAndCustomizeOp(
            @NonNull final SpecTokenKey tokenKey, @NonNull final HapiSpec spec, @NonNull final HapiTokenCreate op) {
        final var key = spec.keys().generate(spec, SIMPLE);
        final var keyName = name + "_" + tokenKey;
        spec.registry().saveKey(keyName, key);
        switch (tokenKey) {
            case ADMIN_KEY -> op.adminKey(keyName);
            case KYC_KEY -> op.kycKey(keyName);
            case FREEZE_KEY -> op.freezeKey(keyName);
            case WIPE_KEY -> op.wipeKey(keyName);
            case SUPPLY_KEY -> op.supplyKey(keyName);
            case FEE_SCHEDULE_KEY -> op.feeScheduleKey(keyName);
            case PAUSE_KEY -> op.pauseKey(keyName);
            case METADATA_KEY -> op.metadataKey(keyName);
        }
    }
}
