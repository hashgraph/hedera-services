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

package com.hedera.services.bdd.suites.crypto.airdrops;

import static com.hedera.services.bdd.junit.TestTags.TOKEN;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithChild;
import static com.hedera.services.bdd.suites.HapiSuite.FALSE_VALUE;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.TRUE_VALUE;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.MULTI_KEY;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.SpecManager;
import com.hederahashgraph.api.proto.java.TokenType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@HapiTestLifecycle
@DisplayName("UnlimitedAssociations")
@Tag(TOKEN)
class UnlimitedAustoAssociationSuite {
    @BeforeAll
    static void beforeAll(@NonNull final SpecManager specManager) throws Throwable {
        // Multiple tests use the same contract, so we upload it once here
        specManager.setup(overriding("entities.unlimitedAutoAssociationsEnabled", TRUE_VALUE));
    }

    @AfterAll
    static void afterAll(@NonNull final SpecManager specManager) throws Throwable {
        specManager.teardown(overriding("entities.unlimitedAutoAssociationsEnabled", FALSE_VALUE));
    }

    //    @DisplayName("Auto-associate tokens will create a child record for association")
    //    @HapiTest
    //    final Stream<DynamicTest> autoAssociateTokensHappyPath(@AccountSpec(name = "firstUser") final SpecAccount
    // firstUser,
    //                                                           @AccountSpec(name = "secondUser") final SpecAccount
    // secondUser,
    //                                                           @FungibleTokenSpec(name = "tokenA",
    //                                                                   keys = {SUPPLY_KEY, PAUSE_KEY, ADMIN_KEY})
    //                                                               final SpecFungibleToken tokenA,
    //                                                           @NonFungibleTokenSpec(name = "tokenB",
    //                                                                   numPreMints = 2,
    //                                                                   keys = {SUPPLY_KEY, PAUSE_KEY, ADMIN_KEY})
    //                                                               final SpecNonFungibleToken tokenB) {
    //        final String transferToFU = "transferToFU";
    //        final String transferToSU = "transferToSU";
    //        return propertyPreservingHapiSpec("autoAssociateTokensHappyPath")
    //                .preserving("entities.unlimitedAutoAssociationsEnabled")
    //                .given()
    //                .when(
    //                        cryptoTransfer(moving(1, tokenA.name()).between(firstUser.name(), secondUser.name()))
    //                                .signedBy(firstUser.name())
    //                                .payingWith(firstUser.name())
    //                                .via(transferToFU),
    //                        getTxnRecord(transferToFU)
    //                                .andAllChildRecords()
    //                                .hasNewTokenAssociation(tokenA.name(), secondUser.name())
    //                                .logged(),
    //                        // Transfer NFT
    //                        cryptoTransfer(movingUnique(tokenB.name(), 1)
    //                                .between(firstUser.name(), secondUser.name()))
    //                                .signedBy(firstUser.name())
    //                                .payingWith(firstUser.name())
    //                                .via(transferToSU),
    //                        getTxnRecord(transferToSU)
    //                                .andAllChildRecords()
    //                                .hasNewTokenAssociation(tokenB.name(), secondUser.name())
    //                                .logged())
    //                .then();
    //    }
    @DisplayName("Auto-associate tokens will create a child record for association")
    @HapiTest
    final Stream<DynamicTest> autoAssociateTokensHappyPath() {
        final String tokenA = "tokenA";
        final String tokenB = "tokenB";
        final String firstUser = "firstUser";
        final String secondUser = "secondUser";
        final String transferFungible = "transferFungible";
        final String transferNonFungible = "transferNonFungible";
        return hapiTest(
                newKeyNamed(MULTI_KEY),
                cryptoCreate(firstUser).balance(ONE_HUNDRED_HBARS),
                cryptoCreate(secondUser).balance(ONE_HBAR).maxAutomaticTokenAssociations(10),
                tokenCreate(tokenA)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .initialSupply(Long.MAX_VALUE)
                        .treasury(firstUser),
                tokenCreate(tokenB)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .initialSupply(0L)
                        .supplyKey(MULTI_KEY)
                        .treasury(firstUser),
                mintToken(tokenB, List.of(ByteString.copyFromUtf8("metadata1"), ByteString.copyFromUtf8("metadata2"))),
                // Transfer fungible token
                cryptoTransfer(moving(1, tokenA).between(firstUser, secondUser))
                        .payingWith(firstUser)
                        .via(transferFungible),
                getTxnRecord(transferFungible)
                        .andAllChildRecords()
                        .hasChildRecordCount(1)
                        .hasNewTokenAssociation(tokenA, secondUser)
                        .logged(),
                // Transfer NFT
                cryptoTransfer(movingUnique(tokenB, 1).between(firstUser, secondUser))
                        .payingWith(firstUser)
                        .via(transferNonFungible),
                getTxnRecord(transferNonFungible)
                        .andAllChildRecords()
                        .hasChildRecordCount(1)
                        .hasNewTokenAssociation(tokenB, secondUser)
                        .logged(),
                // Total fee should include  a token association fee ($0.05) and CryptoTransfer fee ($0.001)
                validateChargedUsdWithChild(transferFungible, 0.05 + 0.001, 0.1),
                validateChargedUsdWithChild(transferNonFungible, 0.05 + 0.001, 0.1));
    }
}
