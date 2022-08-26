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
package com.hedera.services.state.expiry.removal;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.NftAdjustments;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class CryptoGcOutcomeTest {
    @Test
    void recognizesExternalizationNeeds() {
        final var doesnt =
                new CryptoGcOutcome(
                        FungibleTreasuryReturns.UNFINISHED_NOOP_FUNGIBLE_RETURNS,
                        NonFungibleTreasuryReturns.FINISHED_NOOP_NON_FUNGIBLE_RETURNS,
                        false);
        final var does =
                new CryptoGcOutcome(
                        FungibleTreasuryReturns.UNFINISHED_NOOP_FUNGIBLE_RETURNS,
                        new NonFungibleTreasuryReturns(
                                List.of(EntityId.fromIdentityCode(1234)), List.of(), false),
                        false);
        final var alsoDoes =
                new CryptoGcOutcome(
                        new FungibleTreasuryReturns(
                                List.of(EntityId.fromIdentityCode(1234)), List.of(), false),
                        NonFungibleTreasuryReturns.FINISHED_NOOP_NON_FUNGIBLE_RETURNS,
                        false);
        final var doesToo =
                new CryptoGcOutcome(
                        FungibleTreasuryReturns.UNFINISHED_NOOP_FUNGIBLE_RETURNS,
                        NonFungibleTreasuryReturns.FINISHED_NOOP_NON_FUNGIBLE_RETURNS,
                        true);

        assertTrue(does.needsExternalizing());
        assertTrue(alsoDoes.needsExternalizing());
        assertTrue(doesToo.needsExternalizing());
        assertFalse(doesnt.needsExternalizing());
    }

    @Test
    void canAccumulateReturnsFromBoth() {
        final var adjust = new CurrencyAdjustments();
        final var exchange = new NftAdjustments();
        final var outcome =
                new CryptoGcOutcome(
                        new FungibleTreasuryReturns(
                                new ArrayList<>(List.of(EntityId.fromIdentityCode(1234))),
                                new ArrayList<>(List.of(adjust)),
                                false),
                        new NonFungibleTreasuryReturns(
                                List.of(EntityId.fromIdentityCode(2345)), List.of(exchange), false),
                        false);

        assertEquals(
                List.of(EntityId.fromIdentityCode(1234), EntityId.fromIdentityCode(2345)),
                outcome.allReturnedTokens());
        final List<CurrencyAdjustments> parallelAdjusts = new ArrayList<>();
        parallelAdjusts.add(adjust);
        parallelAdjusts.add(null);
        final List<NftAdjustments> parallelExchanges = new ArrayList<>();
        parallelExchanges.add(null);
        parallelExchanges.add(exchange);
        assertEquals(parallelAdjusts, outcome.parallelAdjustments());
        assertEquals(parallelExchanges, outcome.parallelExchanges());
    }

    @Test
    void canAccumulateReturnsFromOne() {
        final var exchange = new NftAdjustments();
        final var outcome =
                new CryptoGcOutcome(
                        FungibleTreasuryReturns.UNFINISHED_NOOP_FUNGIBLE_RETURNS,
                        new NonFungibleTreasuryReturns(
                                new ArrayList<>(List.of(EntityId.fromIdentityCode(2345))),
                                new ArrayList<>(List.of(exchange)),
                                false),
                        false);

        assertEquals(List.of(EntityId.fromIdentityCode(2345)), outcome.allReturnedTokens());
        final List<CurrencyAdjustments> parallelAdjusts = new ArrayList<>();
        parallelAdjusts.add(null);
        final List<NftAdjustments> parallelExchanges = new ArrayList<>();
        parallelExchanges.add(exchange);
        assertEquals(parallelAdjusts, outcome.parallelAdjustments());
        assertEquals(parallelExchanges, outcome.parallelExchanges());
    }

    @Test
    void canAccumulateReturnsFromNeither() {
        final var outcome =
                new CryptoGcOutcome(
                        FungibleTreasuryReturns.UNFINISHED_NOOP_FUNGIBLE_RETURNS,
                        NonFungibleTreasuryReturns.FINISHED_NOOP_NON_FUNGIBLE_RETURNS,
                        false);

        assertEquals(Collections.emptyList(), outcome.allReturnedTokens());
        assertEquals(Collections.emptyList(), outcome.parallelAdjustments());
        assertEquals(Collections.emptyList(), outcome.parallelExchanges());
    }
}
