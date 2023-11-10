package com.hedera.services.bdd.suites.hip796.operations;

import com.hedera.hapi.node.base.TokenType;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static com.hedera.services.bdd.suites.HapiSuite.FUNGIBLE_INITIAL_SUPPLY;
import static com.hedera.services.bdd.suites.HapiSuite.NON_FUNGIBLE_INITIAL_SUPPLY;
import static java.util.Objects.requireNonNull;

/**
 * A higher-level operation that performs all the operations necessary to instantiate
 * a token and its associated entities within <i>both</i> the {@link HapiSpecRegistry}
 * and the target network.
 */
public class TokenDefOperation extends UtilOp {
    private long decimals;
    private long initialSupply;
    private Set<TokenFeature> features = EnumSet.noneOf(TokenFeature.class);
    private List<DesiredPartition> desiredPartitions = new ArrayList<>();
    private Map<String, DesiredAccountTokenRelation> desiredAccountTokenRelations = new HashMap<>();
    private final String specRegistryName;

    private String managingContract;
    private final TokenType type;

    public TokenDefOperation(
            @NonNull final String specRegistryName,
            @NonNull final TokenType type,
            @NonNull final TokenFeature... features) {
        this.type = requireNonNull(type);
        requireNonNull(features);
        this.specRegistryName = requireNonNull(specRegistryName);
        this.features = features.length > 0 ? EnumSet.copyOf(Arrays.asList(features)) : EnumSet.noneOf(TokenFeature.class);
    }

    public TokenDefOperation managedByContract() {
        managingContract = TokenAttributeNames.managementContractOf(specRegistryName);
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
        return withPartition(partition, p -> {
        });
    }

    public TokenDefOperation withPartition(
            @NonNull final String partition,
            @NonNull final Consumer<DesiredPartition> spec) {
        requireNonNull(spec);
        requireNonNull(partition);
        final var desiredPartition = new DesiredPartition(partition)
                .name(partition)
                .memo(partition);
        desiredPartition.initialSupply(type == TokenType.FUNGIBLE_COMMON
                ? FUNGIBLE_INITIAL_SUPPLY
                : NON_FUNGIBLE_INITIAL_SUPPLY);
        spec.accept(desiredPartition);
        desiredPartitions.add(desiredPartition);
        return this;
    }

    public TokenDefOperation withRelation(
            @NonNull final String accountName,
            @NonNull final Consumer<DesiredAccountTokenRelation> spec) {
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

    @Override
    protected boolean submitOp(@NonNull final HapiSpec spec) throws Throwable {
        return false;
    }
}
