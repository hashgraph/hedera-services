/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.hip796.operations;

import static com.hedera.services.bdd.suites.HapiSuite.FUNGIBLE_INITIAL_SUPPLY;
import static com.hedera.services.bdd.suites.HapiSuite.NON_FUNGIBLE_INITIAL_SUPPLY;
import static com.hedera.services.bdd.suites.hip796.operations.TokenAttributeNames.managementContractOf;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import com.hederahashgraph.api.proto.java.CustomFee;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A higher-level operation that performs all the operations necessary to instantiate
 * a token and its associated entities within <i>both</i> the {@link HapiSpecRegistry}
 * and the target network.
 */
public class TokenDefOperation extends UtilOp {
    private long decimals;
    private long initialSupply;
    private long autoRenewPeriod;
    private String autoRenewAccount;
    private String managingContract;
    private TokenSupplyType tokenSupplyType = TokenSupplyType.INFINITE;
    private Set<TokenFeature> features = EnumSet.noneOf(TokenFeature.class);
    private final List<DesiredPartition> desiredPartitions = new ArrayList<>();
    private final Map<String, DesiredAccountTokenRelation> desiredAccountTokenRelations = new HashMap<>();
    private final List<Function<HapiSpec, CustomFee>> feeScheduleSuppliers = new ArrayList<>();
    private final String specRegistryName;

    private final TokenType type;

    public TokenDefOperation(
            @NonNull final String specRegistryName,
            @NonNull final TokenType type,
            @NonNull final TokenFeature... features) {
        this.type = requireNonNull(type);
        requireNonNull(features);
        this.specRegistryName = requireNonNull(specRegistryName);
        this.features =
                features.length > 0 ? EnumSet.copyOf(Arrays.asList(features)) : EnumSet.noneOf(TokenFeature.class);
    }

    public TokenDefOperation withCustomFee(final Function<HapiSpec, CustomFee> supplier) {
        feeScheduleSuppliers.add(supplier);
        return this;
    }

    public TokenDefOperation managedByContract() {
        managingContract = managementContractOf(specRegistryName);
        return this;
    }

    public TokenDefOperation autoRenewAccount(@NonNull final String autoRenewAccount) {
        this.autoRenewAccount = requireNonNull(autoRenewAccount);
        return this;
    }

    public TokenDefOperation withPartitions(@NonNull final String... partitions) {
        requireNonNull(partitions);
        for (final var partition : partitions) {
            withPartition(partition);
        }
        return this;
    }

    public TokenDefOperation withPartition(@NonNull final String partition) {
        requireNonNull(partition);
        return withPartition(partition, p -> {});
    }

    public TokenDefOperation withPartition(
            @NonNull final String partition, @NonNull final Consumer<DesiredPartition> spec) {
        requireNonNull(spec);
        requireNonNull(partition);
        final var desiredPartition =
                new DesiredPartition(partition).name(partition).memo(partition);
        desiredPartition.initialSupply(
                type == TokenType.FUNGIBLE_COMMON ? FUNGIBLE_INITIAL_SUPPLY : NON_FUNGIBLE_INITIAL_SUPPLY);
        spec.accept(desiredPartition);
        desiredPartitions.add(desiredPartition);
        return this;
    }

    public TokenDefOperation withRelation(@NonNull final String accountName) {
        requireNonNull(accountName);
        desiredAccountTokenRelations.put(accountName, new DesiredAccountTokenRelation());
        return this;
    }

    public TokenDefOperation withRelation(
            @NonNull final String accountName, @NonNull final Consumer<DesiredAccountTokenRelation> spec) {
        requireNonNull(spec);
        requireNonNull(accountName);
        final var desiredAccountTokenRelation = new DesiredAccountTokenRelation();
        spec.accept(desiredAccountTokenRelation);
        desiredAccountTokenRelations.put(accountName, desiredAccountTokenRelation);
        return this;
    }

    public TokenDefOperation initialSupply(long initialSupply) {
        this.initialSupply = initialSupply;
        return this;
    }

    public TokenDefOperation decimals(long decimals) {
        this.decimals = decimals;
        return this;
    }

    public TokenDefOperation autoRenewPeriod(long autoRenewPeriod) {
        this.autoRenewPeriod = autoRenewPeriod;
        return this;
    }

    @Override
    protected boolean submitOp(@NonNull final HapiSpec spec) throws Throwable {
        return false;
    }
}
