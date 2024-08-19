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

package com.hedera.services.bdd.suites.regression;

import static com.hedera.services.bdd.junit.ContextRequirement.PROPERTY_OVERRIDES;
import static com.hedera.services.bdd.junit.ContextRequirement.THROTTLE_OVERRIDES;
import static com.hedera.services.bdd.junit.TestTags.TOKEN;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(TOKEN)
public class AirdropThrottlingCheck extends HapiSuite {

    private static final Logger LOG = LogManager.getLogger(AirdropThrottlingCheck.class);
    private final AtomicLong duration = new AtomicLong(10);
    private final AtomicReference<TimeUnit> unit = new AtomicReference<>(SECONDS);
    private final AtomicInteger maxOpsPerSec = new AtomicInteger(100);
    private static final int EXPECTED_MAX_AIRDROPS_PER_SEC = 100;
    private static final double ALLOWED_THROTTLE_NOISE_TOLERANCE = 0.05;

    protected static final String OWNER = "owner";
    // receivers
    protected static final String RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS = "receiverWithUnlimitedAutoAssociations";
    // tokens
    protected static final String FUNGIBLE_TOKEN = "fungibleToken";
    protected static final String NON_FUNGIBLE_TOKEN = "nonFungibleToken";

    public static void main(String... args) {
        new AirdropThrottlingCheck().runSuiteSync();
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(airdropsAreLimitedByConsThrottle());
    }

    @LeakyHapiTest(
            requirement = {PROPERTY_OVERRIDES, THROTTLE_OVERRIDES},
            overrides = {"tokens.airdrops.enabled", "entities.unlimitedAutoAssociationsEnabled"},
            throttles = "testSystemFiles/mainnet-throttles.json")
    final Stream<DynamicTest> airdropsAreLimitedByConsThrottle() {
        return hapiTest(
                overriding("tokens.airdrops.enabled", "true"),
                runWithProvider(airdropFactory())
                        .lasting(duration::get, unit::get)
                        .maxOpsPerSec(maxOpsPerSec::get),
                getTokenInfo(NON_FUNGIBLE_TOKEN)
                        .hasTotalSupplySatisfying(supply -> {
                            final var allowedMaxSupply = (int) (unit.get().toSeconds(duration.get())
                                    * EXPECTED_MAX_AIRDROPS_PER_SEC
                                    * (1.0 + ALLOWED_THROTTLE_NOISE_TOLERANCE));
                            final var allowedMinSupply = (int) (unit.get().toSeconds(duration.get())
                                    * EXPECTED_MAX_AIRDROPS_PER_SEC
                                    * (1.0 - ALLOWED_THROTTLE_NOISE_TOLERANCE));
                            Assertions.assertTrue(
                                    supply <= allowedMaxSupply,
                                    String.format(
                                            "Expected max supply to be less than %d, but was %d",
                                            allowedMaxSupply, supply));
                            Assertions.assertTrue(
                                    supply >= allowedMinSupply,
                                    String.format(
                                            "Expected min supply to be at least %d, but was %d",
                                            allowedMinSupply, supply));
                        })
                        .logged());
    }

    private Function<HapiSpec, OpProvider> airdropFactory() {
        final List<byte[]> someMetadata =
                IntStream.range(0, 100).mapToObj(TxnUtils::randomUtf8Bytes).toList();
        final SplittableRandom r = new SplittableRandom();
        return spec -> new OpProvider() {
            @Override
            public List<SpecOperation> suggestedInitializers() {
                return List.of(
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS),
                        // base tokens
                        tokenCreate(FUNGIBLE_TOKEN)
                                .treasury(OWNER)
                                .tokenType(FUNGIBLE_COMMON)
                                .initialSupply(100000L)
                                .maxSupply(100000000L),
                        newKeyNamed("nftSupplyKey"),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .treasury(OWNER)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0L)
                                .name(NON_FUNGIBLE_TOKEN)
                                .supplyKey("nftSupplyKey"),
                        cryptoCreate(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS).maxAutomaticTokenAssociations(-1));
            }

            @Override
            public Optional<HapiSpecOperation> get() {
                //                final var numMetadataThisMint = r.nextBytes(new byte[48]);

                var mintOp = mintToken(NON_FUNGIBLE_TOKEN, List.of(ByteString.copyFromUtf8("a" + r.nextInt())));

                return Optional.of(mintOp);
            }
        };
    }

    protected static SpecOperation[] setUpTokensAndAllReceivers() {
        var nftSupplyKey = "nftSupplyKey";
        final var t = new ArrayList<SpecOperation>(List.of(
                cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS),
                // base tokens
                tokenCreate(FUNGIBLE_TOKEN)
                        .treasury(OWNER)
                        .tokenType(FUNGIBLE_COMMON)
                        .initialSupply(1000L),
                tokenCreate("dummy").treasury(OWNER).tokenType(FUNGIBLE_COMMON).initialSupply(100L),
                newKeyNamed(nftSupplyKey),
                tokenCreate(NON_FUNGIBLE_TOKEN)
                        .treasury(OWNER)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .initialSupply(0L)
                        .name(NON_FUNGIBLE_TOKEN)
                        .supplyKey(nftSupplyKey),
                mintToken(
                        NON_FUNGIBLE_TOKEN,
                        List.of(
                                ByteString.copyFromUtf8("a"),
                                ByteString.copyFromUtf8("b"),
                                ByteString.copyFromUtf8("c"),
                                ByteString.copyFromUtf8("d"),
                                ByteString.copyFromUtf8("e"),
                                ByteString.copyFromUtf8("f")))));

        return t.toArray(new SpecOperation[0]);
    }

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }
}
