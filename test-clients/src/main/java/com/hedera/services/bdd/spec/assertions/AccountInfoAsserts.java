/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.spec.assertions;

import static com.hedera.services.bdd.suites.HapiApiSuite.ONE_HBAR;
import static com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import org.junit.jupiter.api.Assertions;

public class AccountInfoAsserts extends BaseErroringAssertsProvider<AccountInfo> {

    public static final String BAD_ALIAS = "Bad Alias!";

    public static AccountInfoAsserts accountWith() {
        return new AccountInfoAsserts();
    }

    public AccountInfoAsserts noChangesFromSnapshot(final String snapshot) {
        registerProvider(
                (spec, o) -> {
                    final var expected = spec.registry().getAccountInfo(snapshot);
                    final var actual = (AccountInfo) o;
                    assertEquals(
                            expected, actual, "Changes occurred since snapshot '" + snapshot + "'");
                });
        return this;
    }

    public AccountInfoAsserts newAssociationsFromSnapshot(
            final String snapshot, final List<ExpectedTokenRel> newRels) {
        for (final var newRel : newRels) {
            registerProvider(
                    (spec, o) -> {
                        final var baseline = spec.registry().getAccountInfo(snapshot);
                        for (final var existingRel : baseline.getTokenRelationshipsList()) {
                            assertFalse(
                                    newRel.matches(spec, existingRel),
                                    "Expected no existing rel to match "
                                            + newRel
                                            + ", but "
                                            + existingRel
                                            + " did");
                        }

                        final var current = (AccountInfo) o;
                        var someMatches = false;
                        for (final var currentRel : current.getTokenRelationshipsList()) {
                            someMatches |= newRel.matches(spec, currentRel);
                        }
                        assertTrue(
                                someMatches,
                                "Expected some new rel to match " + newRel + ", but none did");
                    });
        }
        return this;
    }

    public AccountInfoAsserts accountId(String account) {
        registerProvider(
                (spec, o) ->
                        assertEquals(
                                spec.registry().getAccountID(account),
                                ((AccountInfo) o).getAccountID(),
                                "Bad account Id!"));
        return this;
    }

    public AccountInfoAsserts stakedAccountId(String idLiteral) {
        registerProvider(
                (spec, o) ->
                        assertEquals(
                                HapiPropertySource.asAccount(idLiteral),
                                ((AccountInfo) o).getStakingInfo().getStakedAccountId(),
                                "Bad stakedAccountId id!"));
        return this;
    }

    public AccountInfoAsserts noStakedAccountId() {
        registerProvider(
                (spec, o) ->
                        assertEquals(
                                AccountID.getDefaultInstance(),
                                ((AccountInfo) o).getStakingInfo().getStakedAccountId(),
                                "Bad stakedAccountId id!"));
        return this;
    }

    public AccountInfoAsserts someStakePeriodStart() {
        registerProvider(
                (spec, o) ->
                        assertNotEquals(
                                0,
                                ((AccountInfo) o)
                                        .getStakingInfo()
                                        .getStakePeriodStart()
                                        .getSeconds(),
                                "Wrong stakePeriodStart"));
        return this;
    }

    public AccountInfoAsserts noStakePeriodStart() {
        registerProvider(
                (spec, o) ->
                        assertEquals(
                                0,
                                ((AccountInfo) o)
                                        .getStakingInfo()
                                        .getStakePeriodStart()
                                        .getSeconds(),
                                "Wrong stakePeriodStart"));
        return this;
    }

    public AccountInfoAsserts noStakingNodeId() {
        registerProvider(
                (spec, o) ->
                        assertEquals(
                                0,
                                ((AccountInfo) o).getStakingInfo().getStakedNodeId(),
                                "Bad stakedNodeId id!"));
        return this;
    }

    public AccountInfoAsserts stakedNodeId(long idLiteral) {
        registerProvider(
                (spec, o) ->
                        assertEquals(
                                idLiteral,
                                ((AccountInfo) o).getStakingInfo().getStakedNodeId(),
                                "Bad stakedNodeId id!"));
        return this;
    }

    public AccountInfoAsserts isDeclinedReward(boolean isDeclined) {
        registerProvider(
                (spec, o) ->
                        assertEquals(
                                isDeclined,
                                ((AccountInfo) o).getStakingInfo().getDeclineReward(),
                                "Bad isDeclinedReward!"));
        return this;
    }

    public AccountInfoAsserts solidityId(String cid) {
        registerProvider(
                (spec, o) -> {
                    AccountID id = spec.registry().getAccountID(cid);
                    final var solidityId = HapiPropertySource.asHexedSolidityAddress(id);
                    assertEquals(
                            solidityId,
                            ((AccountInfo) o).getContractAccountID(),
                            "Bad Solidity contract Id!");
                });
        return this;
    }

    public AccountInfoAsserts hasDefaultKey() {
        registerProvider(
                (spec, o) ->
                        assertEquals(
                                ((AccountInfo) o).getKey(),
                                com.hederahashgraph.api.proto.java.Key.getDefaultInstance(),
                                "Has non-default key!"));
        return this;
    }

    public AccountInfoAsserts key(String key) {
        registerProvider(
                (spec, o) ->
                        assertEquals(
                                spec.registry().getKey(key),
                                ((AccountInfo) o).getKey(),
                                "Bad key!"));
        return this;
    }

    public AccountInfoAsserts key(Key key) {
        registerProvider((spec, o) -> assertEquals(key, ((AccountInfo) o).getKey(), "Bad key!"));
        return this;
    }

    public AccountInfoAsserts receiverSigReq(Boolean isReq) {
        registerProvider(
                (spec, o) ->
                        assertEquals(
                                isReq,
                                ((AccountInfo) o).getReceiverSigRequired(),
                                "Bad receiver sig requirement!"));
        return this;
    }

    public AccountInfoAsserts balance(long amount) {
        registerProvider(
                (spec, o) -> assertEquals(amount, ((AccountInfo) o).getBalance(), "Bad balance!"));
        return this;
    }

    public AccountInfoAsserts expectedBalanceWithChargedUsd(
            final long amount,
            final double expectedUsdToSubtract,
            final double allowedPercentDiff) {
        registerProvider(
                (spec, o) -> {
                    final var rates = spec.ratesProvider().rates();
                    var expectedTinyBarsToSubtract =
                            expectedUsdToSubtract
                                    * 100
                                    * rates.getHbarEquiv()
                                    / rates.getCentEquiv()
                                    * ONE_HBAR;
                    final var newAmount = ((AccountInfo) o).getBalance();
                    final var actualSubtractedTinybars = amount - newAmount;
                    final var errorMsgIfOutsideTolerance =
                            "Expected to deduct "
                                    + (long) expectedTinyBarsToSubtract
                                    + " tinybar to equal ≈ $"
                                    + expectedUsdToSubtract
                                    + " at a "
                                    + rates.getHbarEquiv()
                                    + "ℏ <-> "
                                    + rates.getCentEquiv()
                                    + "¢ exchange rate (actually deducted "
                                    + (amount - newAmount)
                                    + " tinybars)";
                    assertEquals(
                            expectedTinyBarsToSubtract,
                            actualSubtractedTinybars,
                            (allowedPercentDiff / 100.0) * expectedTinyBarsToSubtract,
                            errorMsgIfOutsideTolerance);
                });
        return this;
    }

    public static void assertTinybarAmountIsApproxUsd(
            final HapiApiSpec spec,
            final double expectedFractionalUsd,
            final long actualTinybars,
            final double allowedPercentDiff) {
        final var expectedTinybars =
                expectedFractionalUsd
                        * 100
                        * spec.ratesProvider().rates().getHbarEquiv()
                        / spec.ratesProvider().rates().getCentEquiv()
                        * ONE_HBAR;
        final var allowedDiff = (allowedPercentDiff / 100.0) * expectedTinybars;
        assertEquals(expectedTinybars, actualTinybars, allowedDiff, "Wrong balance");
    }

    public AccountInfoAsserts hasAlias() {
        registerProvider(
                (spec, o) -> assertFalse(((AccountInfo) o).getAlias().isEmpty(), "Has no Alias!"));
        return this;
    }

    public AccountInfoAsserts alias(String alias) {
        registerProvider(
                (spec, o) ->
                        assertEquals(
                                spec.registry().getKey(alias).toByteString(),
                                ((AccountInfo) o).getAlias(),
                                BAD_ALIAS));
        return this;
    }

    public AccountInfoAsserts evmAddressAlias(ByteString evmAddress) {
        registerProvider(
                (spec, o) -> assertEquals(evmAddress, ((AccountInfo) o).getAlias(), BAD_ALIAS));
        return this;
    }

    public AccountInfoAsserts noAlias() {
        registerProvider(
                (spec, o) -> assertTrue(((AccountInfo) o).getAlias().isEmpty(), BAD_ALIAS));
        return this;
    }

    public AccountInfoAsserts balanceLessThan(long amount) {
        registerProvider(
                (spec, o) -> {
                    long actual = ((AccountInfo) o).getBalance();
                    String errorMessage =
                            String.format("Bad balance! %s is not less than %s", actual, amount);
                    assertTrue(actual < amount, errorMessage);
                });
        return this;
    }

    public AccountInfoAsserts memo(String memo) {
        registerProvider((spec, o) -> assertEquals(memo, ((AccountInfo) o).getMemo(), "Bad memo!"));
        return this;
    }

    public AccountInfoAsserts balance(
            Function<HapiApiSpec, Function<Long, Optional<String>>> dynamicCondition) {
        registerProvider(
                (spec, o) -> {
                    Function<Long, Optional<String>> expectation = dynamicCondition.apply(spec);
                    long actual = ((AccountInfo) o).getBalance();
                    Optional<String> failure = expectation.apply(actual);
                    if (failure.isPresent()) {
                        Assertions.fail("Bad balance! :: " + failure.get());
                    }
                });
        return this;
    }

    public static Function<HapiApiSpec, Function<Long, Optional<String>>> changeFromSnapshot(
            String snapshot, ToLongFunction<HapiApiSpec> expDeltaFn) {
        return approxChangeFromSnapshot(snapshot, expDeltaFn, 0L);
    }

    public static Function<HapiApiSpec, Function<Long, Optional<String>>> changeFromSnapshot(
            String snapshot, long expDelta) {
        return approxChangeFromSnapshot(snapshot, expDelta, 0L);
    }

    public static Function<HapiApiSpec, Function<Long, Optional<String>>> approxChangeFromSnapshot(
            String snapshot, long expDelta, long epsilon) {
        return approxChangeFromSnapshot(snapshot, ignore -> expDelta, epsilon);
    }

    public static Function<HapiApiSpec, Function<Long, Optional<String>>> approxChangeFromSnapshot(
            String snapshot, ToLongFunction<HapiApiSpec> expDeltaFn, long epsilon) {
        return spec ->
                actual -> {
                    long expDelta = expDeltaFn.applyAsLong(spec);
                    long actualDelta = actual - spec.registry().getBalanceSnapshot(snapshot);
                    if (Math.abs(actualDelta - expDelta) <= epsilon) {
                        return Optional.empty();
                    } else {
                        return Optional.of(
                                String.format(
                                        "Expected balance change from '%s' to be <%d +/- %d>, was"
                                                + " <%d>!",
                                        snapshot, expDelta, epsilon, actualDelta));
                    }
                };
    }

    public AccountInfoAsserts sendThreshold(long amount) {
        registerProvider(
                (spec, o) ->
                        assertEquals(
                                amount,
                                ((AccountInfo) o).getGenerateSendRecordThreshold(),
                                "Bad send threshold!"));
        return this;
    }

    public AccountInfoAsserts receiveThreshold(long amount) {
        registerProvider(
                (spec, o) ->
                        assertEquals(
                                amount,
                                ((AccountInfo) o).getGenerateReceiveRecordThreshold(),
                                "Bad receive threshold!"));
        return this;
    }

    public AccountInfoAsserts expiry(long approxTime, long epsilon) {
        registerProvider(
                (spec, o) -> {
                    long expiry = ((AccountInfo) o).getExpirationTime().getSeconds();
                    assertTrue(
                            Math.abs(approxTime - expiry) <= epsilon,
                            String.format(
                                    "Expiry %d not in [%d, %d]!",
                                    expiry, approxTime - epsilon, approxTime + epsilon));
                });
        return this;
    }

    public AccountInfoAsserts expiry(String registryEntry, long delta) {
        registerProvider(
                (spec, o) -> {
                    long expiry = ((AccountInfo) o).getExpirationTime().getSeconds();
                    long expected =
                            spec.registry()
                                            .getAccountInfo(registryEntry)
                                            .getExpirationTime()
                                            .getSeconds()
                                    + delta;
                    assertEquals(expected, expiry, "Bad expiry!");
                });
        return this;
    }

    public AccountInfoAsserts autoRenew(long period) {
        registerProvider(
                (spec, o) ->
                        assertEquals(
                                period,
                                ((AccountInfo) o).getAutoRenewPeriod().getSeconds(),
                                "Bad auto-renew period!"));
        return this;
    }

    public AccountInfoAsserts nonce(long nonce) {
        registerProvider(
                (spec, o) ->
                        assertEquals(nonce, ((AccountInfo) o).getEthereumNonce(), "Bad nonce!"));
        return this;
    }

    public AccountInfoAsserts pendingRewards(long reward) {
        registerProvider(
                (spec, o) ->
                        assertEquals(
                                reward,
                                ((AccountInfo) o).getStakingInfo().getPendingReward(),
                                "Bad pending rewards!"));
        return this;
    }

    public AccountInfoAsserts maxAutoAssociations(int num) {
        registerProvider(
                (spec, o) ->
                        assertEquals(
                                num,
                                ((AccountInfo) o).getMaxAutomaticTokenAssociations(),
                                "Bad maxAutomaticTokenAssociations!"));
        return this;
    }

    public AccountInfoAsserts ownedNfts(int num) {
        registerProvider(
                (spec, o) -> assertEquals(num, ((AccountInfo) o).getOwnedNfts(), "Bad ownedNfts!"));
        return this;
    }
}
