package com.hedera.services.bdd.suites.regression.factories;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.meta.InitialAccountIdentifiers;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.BiasedDelegatingProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.crypto.RandomAccount;
import com.hedera.services.bdd.spec.infrastructure.selectors.RandomSelector;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;

import java.util.function.Function;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.suites.regression.factories.RegressionProviderFactory.intPropOrElse;

public class IdFuzzingProviderFactory {
    /**
     * How many different ECDSA keys we will re-use as we continually create, update, and delete
     * accounts with random {@link InitialAccountIdentifiers} based on this fixed set of keys.
     */
    private static final int NUM_DISTINCT_ECDSA_KEYS = 42;

    public static Function<HapiSpec, OpProvider> idFuzzingWith(final String resource) {
        return spec -> {
            final var props = RegressionProviderFactory.propsFrom(resource);

            final var keys =
                    new RegistrySourcedNameProvider<>(
                            Key.class, spec.registry(), new RandomSelector());
            final var accounts =
                    new RegistrySourcedNameProvider<>(
                            AccountID.class, spec.registry(), new RandomSelector());

            return new BiasedDelegatingProvider()
                    /* --- <inventory> --- */
                    .withInitialization(onlyEcdsaKeys())
                    /* ----- CRYPTO ----- */
                    .withOp(
                            new RandomAccount(keys, accounts, true)
                                    .ceiling(intPropOrElse(
                                                    "randomAccount.ceilingNum",
                                                    RandomAccount.DEFAULT_CEILING_NUM,
                                                    props)),
                            intPropOrElse("randomAccount.bias", 0, props));
        };
    }

    private static HapiSpecOperation[] onlyEcdsaKeys() {
        return IntStream.range(0, NUM_DISTINCT_ECDSA_KEYS)
                .mapToObj( i -> newKeyNamed("Fuzz#" + i).shape(SigControl.SECP256K1_ON))
                .toArray(HapiSpecOperation[]::new);
    }
}
