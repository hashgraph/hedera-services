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

package com.hedera.services.bdd.suites.contract.precompile.token;

import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.dsl.entities.SpecTokenKey.ADMIN_KEY;
import static com.hedera.services.bdd.spec.dsl.entities.SpecTokenKey.METADATA_KEY;
import static com.hedera.services.bdd.spec.dsl.entities.SpecTokenKey.PAUSE_KEY;
import static com.hedera.services.bdd.spec.dsl.entities.SpecTokenKey.SUPPLY_KEY;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_MILLION_HBARS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hedera.services.bdd.spec.dsl.annotations.Account;
import com.hedera.services.bdd.spec.dsl.annotations.Contract;
import com.hedera.services.bdd.spec.dsl.annotations.FungibleToken;
import com.hedera.services.bdd.spec.dsl.annotations.NonFungibleToken;
import com.hedera.services.bdd.spec.dsl.entities.SpecAccount;
import com.hedera.services.bdd.spec.dsl.entities.SpecContract;
import com.hedera.services.bdd.spec.dsl.entities.SpecFungibleToken;
import com.hedera.services.bdd.spec.dsl.entities.SpecNonFungibleToken;
import com.hedera.services.bdd.suites.utils.contracts.precompile.TokenKeyType;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(SMART_CONTRACT)
@DisplayName("metadata tests")
@SuppressWarnings("java:S1192")
@HapiTestLifecycle
public class TokenMetadataTest {

    @Account(maxAutoAssociations = 10, tinybarBalance = ONE_MILLION_HBARS)
    static SpecAccount alice;

    @Contract(contract = "CreateTokenVTwo", creationGas = 1_000_000L)
    static SpecContract contractTarget;

    @FungibleToken(name = "fungibleToken", initialSupply = 1_000L, maxSupply = 1_200L)
    static SpecFungibleToken fungibleToken;

    @NonFungibleToken(
            numPreMints = 5,
            keys = {SUPPLY_KEY, PAUSE_KEY, ADMIN_KEY, METADATA_KEY})
    static SpecNonFungibleToken nft;

    @BeforeAll
    static void beforeAll(final TestLifecycle testLifecycle) {
        testLifecycle.doAdhoc(
                alice.authorizeContract(contractTarget)
                        .alsoAuthorizing(TokenKeyType.SUPPLY_KEY, TokenKeyType.METADATA_KEY),
                nft.authorizeContracts(contractTarget)
                        .alsoAuthorizing(TokenKeyType.SUPPLY_KEY, TokenKeyType.METADATA_KEY),
                fungibleToken.authorizeContracts(contractTarget));
    }

    @HapiTest
    public Stream<DynamicTest> testUpdateMetadata() {
        return Stream.of(nft, fungibleToken)
                .flatMap(token -> hapiTest(
                        contractTarget
                                .call("updateTokenMetadata", token, "randomMetaNew777")
                                .gas(1_000_000L)
                                .andAssert(txn -> txn.hasKnownStatus(SUCCESS)),
                        token.getInfo().andAssert(info -> info.hasMetadata("randomMetaNew777"))));
    }

    @HapiTest
    public Stream<DynamicTest> testUpdateTokenKeys() {
        return hapiTest(contractTarget
                .call("updateTokenKeys", nft, alice.getED25519KeyBytes())
                .gas(1_000_000L)
                .payingWith(alice)
                .andAssert(txn -> txn.hasKnownStatus(SUCCESS)));
    }

    @HapiTest
    final Stream<DynamicTest> createTokenV2HappyPath() {
        final AtomicReference<Address> newToken = new AtomicReference<>();
        return hapiTest(withOpContext((spec, opLog) -> {
            final var create = contractTarget
                    .call("createTokenWithMetadata")
                    .sending(2000 * ONE_HBAR)
                    .gas(1_000_000L)
                    .exposingResultTo(res -> newToken.set((Address) res[0]));
            allRunFor(spec, create);
            // TODO: Re-enable this once we have the view calls for token info with metadata
            //            final var getInfo = tokenInfoContract.call(
            //                    "getInformationForToken",
            //                    newToken.get())
            //                    .gas(100_000L)
            //                    .andAssert(txn ->
            // txn.hasKnownStatuses(CONTRACT_REVERT_EXECUTED).via("getInfo").logged());
            //            allRunFor(spec, getInfo);
        }));
    }

    @HapiTest
    final Stream<DynamicTest> createTokenHappyPath() {
        final AtomicReference<Address> newToken = new AtomicReference<>();
        return hapiTest(withOpContext((spec, opLog) -> {
            final var create = contractTarget
                    .call("createToken")
                    .sending(2000 * ONE_HBAR)
                    .gas(1_000_000L)
                    .exposingResultTo(res -> newToken.set((Address) res[0]));
            allRunFor(spec, create);
            // TODO: Re-enable this once we have the view calls for token info with metadata
            //            final var getInfo = tokenInfoContract.call(
            //                    "getInformationForToken",
            //                    newToken.get())
            //                    .gas(100_000L)
            //                    .andAssert(txn ->
            // txn.hasKnownStatuses(CONTRACT_REVERT_EXECUTED).via("getInfo").logged());
            //            allRunFor(spec, getInfo);
        }));
    }

    @HapiTest
    final Stream<DynamicTest> createTokenV2WithKeyHappyPath() {
        final AtomicReference<Address> newToken = new AtomicReference<>();
        return hapiTest(withOpContext((spec, opLog) -> {
            final var create = contractTarget
                    .call("createTokenWithMetadataAndKey")
                    .sending(2000 * ONE_HBAR)
                    .gas(1_000_000L)
                    .exposingResultTo(res -> newToken.set((Address) res[0]));
            allRunFor(spec, create);
            // TODO: Re-enable this once we have the view calls for token info with metadata
            //            final var getInfo = tokenInfoContract.call(
            //                    "getInformationForToken",
            //                    newToken.get())
            //                    .gas(100_000L)
            //                    .andAssert(txn ->
            // txn.hasKnownStatuses(CONTRACT_REVERT_EXECUTED).via("getInfo").logged());
            //            allRunFor(spec, getInfo);
        }));
    }

    @HapiTest
    final Stream<DynamicTest> createTokenV2WithCustomFeesHappyPath() {
        final AtomicReference<Address> newToken = new AtomicReference<>();
        return hapiTest(withOpContext((spec, opLog) -> {
            final var create = contractTarget
                    .call("createTokenWithMetadataAndCustomFees")
                    .sending(2000 * ONE_HBAR)
                    .gas(1_000_000L)
                    .exposingResultTo(res -> newToken.set((Address) res[0]));
            allRunFor(spec, create);
            // TODO: Re-enable this once we have the view calls for token info with metadata
            //            final var getInfo = tokenInfoContract.call(
            //                    "getInformationForToken",
            //                    newToken.get())
            //                    .gas(100_000L)
            //                    .andAssert(txn ->
            // txn.hasKnownStatuses(CONTRACT_REVERT_EXECUTED).via("getInfo").logged());
            //            allRunFor(spec, getInfo);
        }));
    }

    @HapiTest
    final Stream<DynamicTest> createTokenWithCustomFeesHappyPath() {
        final AtomicReference<Address> newToken = new AtomicReference<>();
        return hapiTest(withOpContext((spec, opLog) -> {
            final var create = contractTarget
                    .call("createTokenWithCustomFees")
                    .sending(2000 * ONE_HBAR)
                    .gas(1_000_000L)
                    .exposingResultTo(res -> newToken.set((Address) res[0]));
            allRunFor(spec, create);
            // TODO: Re-enable this once we have the view calls for token info with metadata
            //            final var getInfo = tokenInfoContract.call(
            //                    "getInformationForToken",
            //                    newToken.get())
            //                    .gas(100_000L)
            //                    .andAssert(txn ->
            // txn.hasKnownStatuses(CONTRACT_REVERT_EXECUTED).via("getInfo").logged());
            //            allRunFor(spec, getInfo);
        }));
    }

    @HapiTest
    final Stream<DynamicTest> createTokenV2WithKeyAndyCustomFeesHappyPath() {
        final AtomicReference<Address> newToken = new AtomicReference<>();
        return hapiTest(withOpContext((spec, opLog) -> {
            final var create = contractTarget
                    .call("createTokenWithMetadataAndKeyAndCustomFees")
                    .sending(2000 * ONE_HBAR)
                    .gas(1_000_000L)
                    .exposingResultTo(res -> newToken.set((Address) res[0]));
            allRunFor(spec, create);
            // TODO: Re-enable this once we have the view calls for token info with metadata
            //            final var getInfo = tokenInfoContract.call(
            //                    "getInformationForToken",
            //                    newToken.get())
            //                    .gas(100_000L)
            //                    .andAssert(txn ->
            // txn.hasKnownStatuses(CONTRACT_REVERT_EXECUTED).via("getInfo").logged());
            //            allRunFor(spec, getInfo);
        }));
    }

    @HapiTest
    final Stream<DynamicTest> createNftHappyPath() {
        final AtomicReference<Address> newToken = new AtomicReference<>();
        return hapiTest(withOpContext((spec, opLog) -> {
            final var create = contractTarget
                    .call("createNft")
                    .sending(2000 * ONE_HBAR)
                    .gas(1_000_000L)
                    .exposingResultTo(res -> newToken.set((Address) res[0]));
            allRunFor(spec, create);
            // TODO: Re-enable this once we have the view calls for token info with metadata
            //            final var getInfo = tokenInfoContract.call(
            //                    "getInformationForToken",
            //                    newToken.get())
            //                    .gas(100_000L)
            //                    .andAssert(txn ->
            // txn.hasKnownStatuses(CONTRACT_REVERT_EXECUTED).via("getInfo").logged());
            //            allRunFor(spec, getInfo);
        }));
    }

    @HapiTest
    final Stream<DynamicTest> createNftWithMetaHappyPath() {
        final AtomicReference<Address> newToken = new AtomicReference<>();
        return hapiTest(withOpContext((spec, opLog) -> {
            final var create = contractTarget
                    .call("createNftWithMetadata")
                    .sending(2000 * ONE_HBAR)
                    .gas(1_000_000L)
                    .exposingResultTo(res -> newToken.set((Address) res[0]));
            allRunFor(spec, create);
            // TODO: Re-enable this once we have the view calls for token info with metadata
            //            final var getInfo = tokenInfoContract.call(
            //                    "getInformationForToken",
            //                    newToken.get())
            //                    .gas(100_000L)
            //                    .andAssert(txn ->
            // txn.hasKnownStatuses(CONTRACT_REVERT_EXECUTED).via("getInfo").logged());
            //            allRunFor(spec, getInfo);
        }));
    }

    @HapiTest
    final Stream<DynamicTest> createNftWithMetaAndKeyHappyPath() {
        final AtomicReference<Address> newToken = new AtomicReference<>();
        return hapiTest(withOpContext((spec, opLog) -> {
            final var create = contractTarget
                    .call("createNftWithMetaAndKey")
                    .sending(2000 * ONE_HBAR)
                    .gas(5_000_000L)
                    .exposingResultTo(res -> newToken.set((Address) res[0]));
            allRunFor(spec, create);
            // TODO: Re-enable this once we have the view calls for token info with metadata
            //            final var getInfo = tokenInfoContract.call(
            //                    "getInformationForToken",
            //                    newToken.get())
            //                    .gas(100_000L)
            //                    .andAssert(txn ->
            // txn.hasKnownStatuses(CONTRACT_REVERT_EXECUTED).via("getInfo").logged());
            //            allRunFor(spec, getInfo);
        }));
    }

    @HapiTest
    final Stream<DynamicTest> createNftWithCustomFeesHappyPath() {
        final AtomicReference<Address> newToken = new AtomicReference<>();
        return hapiTest(withOpContext((spec, opLog) -> {
            final var create = contractTarget
                    .call("createNftWithCustomFees")
                    .sending(2000 * ONE_HBAR)
                    .gas(1_000_000L)
                    .exposingResultTo(res -> newToken.set((Address) res[0]));
            allRunFor(spec, create);
            // TODO: Re-enable this once we have the view calls for token info with metadata
            //            final var getInfo = tokenInfoContract.call(
            //                    "getInformationForToken",
            //                    newToken.get())
            //                    .gas(100_000L)
            //                    .andAssert(txn ->
            // txn.hasKnownStatuses(CONTRACT_REVERT_EXECUTED).via("getInfo").logged());
            //            allRunFor(spec, getInfo);
        }));
    }

    @HapiTest
    final Stream<DynamicTest> createNftWithMetaAndCustomFeesHappyPath() {
        final AtomicReference<Address> newToken = new AtomicReference<>();
        return hapiTest(withOpContext((spec, opLog) -> {
            final var create = contractTarget
                    .call("createNftWithMetadataAndCustomFees")
                    .sending(2000 * ONE_HBAR)
                    .gas(1_000_000L)
                    .exposingResultTo(res -> newToken.set((Address) res[0]));
            allRunFor(spec, create);
            // TODO: Re-enable this once we have the view calls for token info with metadata
            //            final var getInfo = tokenInfoContract.call(
            //                    "getInformationForToken",
            //                    newToken.get())
            //                    .gas(100_000L)
            //                    .andAssert(txn ->
            // txn.hasKnownStatuses(CONTRACT_REVERT_EXECUTED).via("getInfo").logged());
            //            allRunFor(spec, getInfo);
        }));
    }

    @HapiTest
    final Stream<DynamicTest> createNftWithMetaAndKeyAndCustomFeesHappyPath() {
        final AtomicReference<Address> newToken = new AtomicReference<>();
        return hapiTest(withOpContext((spec, opLog) -> {
            final var create = contractTarget
                    .call("createNftWithMetaAndKeyAndCustomFees")
                    .sending(2000 * ONE_HBAR)
                    .gas(1_000_000L)
                    .exposingResultTo(res -> newToken.set((Address) res[0]));
            allRunFor(spec, create);
            // TODO: Re-enable this once we have the view calls for token info with metadata
            //            final var getInfo = tokenInfoContract.call(
            //                    "getInformationForToken",
            //                    newToken.get())
            //                    .gas(100_000L)
            //                    .andAssert(txn ->
            // txn.hasKnownStatuses(CONTRACT_REVERT_EXECUTED).via("getInfo").logged());
            //            allRunFor(spec, getInfo);
        }));
    }
}
