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

package com.hedera.services.bdd.suites.hip904;

import static com.hedera.services.bdd.junit.TestTags.CRYPTO;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAirdrop;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCancelAirdrop;
import static com.hedera.services.bdd.spec.transactions.token.HapiTokenCancelAirdrop.pendingAirdrop;
import static com.hedera.services.bdd.spec.transactions.token.HapiTokenCancelAirdrop.pendingNFTAirdrop;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.OrderedInIsolation;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;

@Tag(CRYPTO)
@HapiTestLifecycle
@DisplayName("Token airdrop")
// Adding ordering because even though we are cleaning the created airdrops after each test when we run the tests
// simultaneously we run into race conditions and the tests became flaky.
@OrderedInIsolation
public class TokenAirdropWithOverriddenMaxAllowedPendingAirdropsTest extends TokenAirdropBase {

    @BeforeAll
    static void beforeAll(@NonNull final TestLifecycle lifecycle) {
        lifecycle.overrideInClass(Map.of(
                "tokens.airdrops.enabled", "true",
                "tokens.airdrops.cancel.enabled", "true",
                "tokens.maxAllowedPendingAirdrops", "1",
                "entities.unlimitedAutoAssociationsEnabled", "true"));
        lifecycle.doAdhoc(setUpTokensAndAllReceivers());
    }

    @DisplayName("with two transactions that will result in two airdrops and exceed the limit - should throw")
    @HapiTest
    @Order(1)
    final Stream<DynamicTest> withTwoTransactionsExceedingTheLimit() {
        return hapiTest(
                tokenAirdrop(moving(10, FUNGIBLE_TOKEN).between(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS))
                        .signedByPayerAnd(OWNER),
                tokenAirdrop(movingUnique(NON_FUNGIBLE_TOKEN, 1L).between(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS))
                        .signedByPayerAnd(OWNER)
                        .hasKnownStatus(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED),

                // Clear the airdrop at the end of the test
                tokenCancelAirdrop(pendingAirdrop(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS, FUNGIBLE_TOKEN)));
    }

    @DisplayName("with a single transaction that will result in two airdrops and exceed the limit - should throw")
    @HapiTest
    @Order(2)
    final Stream<DynamicTest> withASingleTransactionsExceedingTheLimit() {
        return hapiTest(tokenAirdrop(
                        moving(10, FUNGIBLE_TOKEN).between(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS),
                        movingUnique(NON_FUNGIBLE_TOKEN, 1L).between(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS))
                .signedByPayerAnd(OWNER)
                .hasKnownStatus(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED));
    }

    @DisplayName("with two airdrop transactions. One will result in CryptoTransfer the other in airdrop - SUCCESS")
    @HapiTest
    @Order(3)
    final Stream<DynamicTest> twoTransactionsOneCryptoTransferAndOneAirdrop() {
        return hapiTest(
                // The first airdrop results in a CryptoTransfer and we don't use the state at all
                tokenAirdrop(moving(10, FUNGIBLE_TOKEN).between(OWNER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS))
                        .signedByPayerAnd(OWNER),
                // This is an airdrop but the total airdrop count is equal to the store limit
                tokenAirdrop(movingUnique(NON_FUNGIBLE_TOKEN, 1L).between(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS))
                        .signedByPayerAnd(OWNER),

                // Clear the airdrop at the end of the test
                tokenCancelAirdrop(
                        pendingNFTAirdrop(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS, NON_FUNGIBLE_TOKEN, 1L)));
    }

    /**
     * This is the downside of the rough airdrop count estimation. We don't check if an airdrop will result in
     * CryptoTransfer - we count it towards the airdrop count.
     * For more details, see {@link com.hedera.node.app.service.token.impl.validators.TokenAirdropValidator#roughAirdropCount(java.util.List)}.
     */
    @DisplayName("with one airdrop transactions. One will result in CryptoTransfer the other in airdrop - should throw")
    @HapiTest
    @Order(4)
    final Stream<DynamicTest> oneTransactionOneCryptoTransferAndOneAirdrop() {
        return hapiTest(
                // The first airdrop will be a CryptoTransfer, but we count it as an airdrop
                tokenAirdrop(
                                moving(10, FUNGIBLE_TOKEN).between(OWNER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS),
                                movingUnique(NON_FUNGIBLE_TOKEN, 1L).between(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS))
                        .signedByPayerAnd(OWNER)
                        .hasKnownStatus(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED));
    }
}
