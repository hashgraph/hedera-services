/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.queries.token;

import static com.hedera.services.bdd.spec.queries.QueryUtils.answerCostHeader;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerHeader;
import static java.util.stream.Collectors.toCollection;

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.hip796.operations.TokenFeature;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenGetInfoQuery;
import com.hederahashgraph.api.proto.java.TokenKycStatus;
import com.hederahashgraph.api.proto.java.TokenPauseStatus;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.Transaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.LongConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

public class HapiGetTokenInfo extends HapiQueryOp<HapiGetTokenInfo> {
    private static final Logger LOG = LogManager.getLogger(HapiGetTokenInfo.class);

    private final String token;

    public HapiGetTokenInfo(String token) {
        this.token = token;
    }

    private boolean searchKeysGlobally;
    private Optional<TokenType> expectedTokenType = Optional.empty();
    private Optional<TokenSupplyType> expectedSupplyType = Optional.empty();
    private OptionalInt expectedDecimals = OptionalInt.empty();
    private OptionalLong expectedTotalSupply = OptionalLong.empty();

    @Nullable
    private LongConsumer totalSupplyAssertion = null;

    private OptionalLong expectedMaxSupply = OptionalLong.empty();
    private Optional<String> expectedMemo = Optional.empty();
    private Optional<String> expectedMetadata = Optional.empty();
    private Optional<String> expectedMetadataKey = Optional.empty();
    private Optional<String> expectedId = Optional.empty();
    private Optional<String> expectedSymbol = Optional.empty();
    private Optional<String> expectedName = Optional.empty();
    private Optional<String> expectedTreasury = Optional.empty();
    private Optional<String> expectedAdminKey = Optional.empty();
    private Optional<String> expectedKycKey = Optional.empty();
    private Optional<String> expectedFreezeKey = Optional.empty();
    private Optional<String> expectedSupplyKey = Optional.empty();
    private Optional<String> expectedWipeKey = Optional.empty();
    private Optional<String> expectedFeeScheduleKey = Optional.empty();
    private Optional<String> expectedPauseKey = Optional.empty();

    @Nullable
    private String expectedLockKey = null;

    @Nullable
    private String expectedPartitionKey = null;

    @Nullable
    private String expectedPartitionMoveKey = null;

    private Set<TokenFeature> rolesExpectedUnset = EnumSet.noneOf(TokenFeature.class);

    @SuppressWarnings("java:S1068")
    private Optional<Boolean> expectedDeletion = Optional.empty();

    private Optional<TokenPauseStatus> expectedPauseStatus = Optional.empty();

    @SuppressWarnings("java:S1068")
    private Optional<TokenKycStatus> expectedKycDefault = Optional.empty();

    @SuppressWarnings("java:S1068")
    private Optional<TokenFreezeStatus> expectedFreezeDefault = Optional.empty();

    private Optional<String> expectedAutoRenewAccount = Optional.empty();
    private OptionalLong expectedAutoRenewPeriod = OptionalLong.empty();
    private Optional<Boolean> expectedExpiry = Optional.empty();
    private List<BiConsumer<HapiSpec, List<CustomFee>>> expectedFees = new ArrayList<>();

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.TokenGetInfo;
    }

    @Override
    protected HapiGetTokenInfo self() {
        return this;
    }

    public HapiGetTokenInfo hasTokenType(TokenType t) {
        expectedTokenType = Optional.of(t);
        return this;
    }

    public HapiGetTokenInfo hasSupplyType(TokenSupplyType t) {
        expectedSupplyType = Optional.of(t);
        return this;
    }

    public HapiGetTokenInfo hasFreezeDefault(TokenFreezeStatus s) {
        expectedFreezeDefault = Optional.of(s);
        return this;
    }

    public HapiGetTokenInfo hasKycDefault(TokenKycStatus s) {
        expectedKycDefault = Optional.of(s);
        return this;
    }

    public HapiGetTokenInfo hasDecimals(int d) {
        expectedDecimals = OptionalInt.of(d);
        return this;
    }

    public HapiGetTokenInfo hasMaxSupply(long amount) {
        expectedMaxSupply = OptionalLong.of(amount);
        return this;
    }

    public HapiGetTokenInfo hasTotalSupply(long amount) {
        expectedTotalSupply = OptionalLong.of(amount);
        return this;
    }

    public HapiGetTokenInfo hasTotalSupplySatisfying(@NonNull final LongConsumer assertion) {
        totalSupplyAssertion = Objects.requireNonNull(assertion);
        return this;
    }

    public HapiGetTokenInfo hasRegisteredId(String token) {
        expectedId = Optional.of(token);
        return this;
    }

    public HapiGetTokenInfo hasEntityMemo(String memo) {
        expectedMemo = Optional.of(memo);
        return this;
    }

    public HapiGetTokenInfo hasMetadata(String metadata) {
        expectedMetadata = Optional.of(metadata);
        return this;
    }

    public HapiGetTokenInfo hasMetadataKey(String name) {
        expectedMetadataKey = Optional.of(name);
        return this;
    }

    public HapiGetTokenInfo hasAutoRenewPeriod(Long renewPeriod) {
        expectedAutoRenewPeriod = OptionalLong.of(renewPeriod);
        return this;
    }

    public HapiGetTokenInfo hasAutoRenewAccount(String account) {
        expectedAutoRenewAccount = Optional.of(account);
        return this;
    }

    public HapiGetTokenInfo hasValidExpiry() {
        expectedExpiry = Optional.of(true);
        return this;
    }

    public HapiGetTokenInfo hasSymbol(String token) {
        expectedSymbol = Optional.of(token);
        return this;
    }

    public HapiGetTokenInfo hasName(String name) {
        expectedName = Optional.of(name);
        return this;
    }

    public HapiGetTokenInfo hasTreasury(String name) {
        expectedTreasury = Optional.of(name);
        return this;
    }

    public HapiGetTokenInfo hasFreezeKey(String name) {
        expectedFreezeKey = Optional.of(name);
        return this;
    }

    public HapiGetTokenInfo hasAdminKey(String name) {
        expectedAdminKey = Optional.of(name);
        return this;
    }

    public HapiGetTokenInfo hasPauseKey(String name) {
        expectedPauseKey = Optional.of(name);
        return this;
    }

    public HapiGetTokenInfo hasLockKey(@NonNull final String name) {
        expectedLockKey = Objects.requireNonNull(name);
        return this;
    }

    public HapiGetTokenInfo hasPartitionKey(@NonNull final String name) {
        expectedPartitionKey = Objects.requireNonNull(name);
        return this;
    }

    public HapiGetTokenInfo hasPartitionMoveKey(@NonNull final String name) {
        expectedPartitionMoveKey = Objects.requireNonNull(name);
        return this;
    }

    public HapiGetTokenInfo hasNoneOfRoles(@NonNull final TokenFeature... unsetRoles) {
        this.rolesExpectedUnset = unsetRoles.length == 0
                ? EnumSet.noneOf(TokenFeature.class)
                : Arrays.stream(unsetRoles).collect(toCollection(() -> EnumSet.noneOf(TokenFeature.class)));
        return this;
    }

    public HapiGetTokenInfo hasPauseStatus(TokenPauseStatus status) {
        expectedPauseStatus = Optional.of(status);
        return this;
    }

    public HapiGetTokenInfo hasKycKey(String name) {
        expectedKycKey = Optional.of(name);
        return this;
    }

    public HapiGetTokenInfo hasSupplyKey(String name) {
        expectedSupplyKey = Optional.of(name);
        return this;
    }

    public HapiGetTokenInfo hasWipeKey(String name) {
        expectedWipeKey = Optional.of(name);
        return this;
    }

    public HapiGetTokenInfo hasFeeScheduleKey(String name) {
        expectedFeeScheduleKey = Optional.of(name);
        return this;
    }

    public HapiGetTokenInfo isDeleted() {
        expectedDeletion = Optional.of(Boolean.TRUE);
        return this;
    }

    public HapiGetTokenInfo isNotDeleted() {
        expectedDeletion = Optional.of(Boolean.FALSE);
        return this;
    }

    public HapiGetTokenInfo hasCustom(BiConsumer<HapiSpec, List<CustomFee>> feeAssertion) {
        expectedFees.add(feeAssertion);
        return this;
    }

    public HapiGetTokenInfo searchKeysGlobally() {
        searchKeysGlobally = true;
        return this;
    }

    @Override
    @SuppressWarnings("java:S5960")
    protected void assertExpectationsGiven(HapiSpec spec) {
        var actualInfo = response.getTokenGetInfo().getTokenInfo();

        expectedTokenType.ifPresent(
                tokenType -> Assertions.assertEquals(tokenType, actualInfo.getTokenType(), "Wrong token type!"));

        expectedSupplyType.ifPresent(
                supplyType -> Assertions.assertEquals(supplyType, actualInfo.getSupplyType(), "Wrong supply type!"));

        if (expectedSymbol.isPresent()) {
            Assertions.assertEquals(expectedSymbol.get(), actualInfo.getSymbol(), "Wrong symbol!");
        }

        if (expectedName.isPresent()) {
            Assertions.assertEquals(expectedName.get(), actualInfo.getName(), "Wrong name!");
        }

        if (expectedAutoRenewAccount.isPresent()) {
            var id = TxnUtils.asId(expectedAutoRenewAccount.get(), spec);
            Assertions.assertEquals(id, actualInfo.getAutoRenewAccount(), "Wrong auto renew account!");
        }

        if (expectedAutoRenewPeriod.isPresent()) {
            Assertions.assertEquals(
                    expectedAutoRenewPeriod.getAsLong(),
                    actualInfo.getAutoRenewPeriod().getSeconds(),
                    "Wrong auto renew period!");
        }

        if (expectedMaxSupply.isPresent()) {
            Assertions.assertEquals(expectedMaxSupply.getAsLong(), actualInfo.getMaxSupply(), "Wrong max supply!");
        }

        if (expectedTotalSupply.isPresent()) {
            Assertions.assertEquals(
                    expectedTotalSupply.getAsLong(), actualInfo.getTotalSupply(), "Wrong total supply!");
        }
        if (totalSupplyAssertion != null) {
            totalSupplyAssertion.accept(actualInfo.getTotalSupply());
        }

        if (expectedDecimals.isPresent()) {
            Assertions.assertEquals(expectedDecimals.getAsInt(), actualInfo.getDecimals(), "Wrong decimals!");
        }

        if (expectedTreasury.isPresent()) {
            var id = TxnUtils.asId(expectedTreasury.get(), spec);
            Assertions.assertEquals(id, actualInfo.getTreasury(), "Wrong treasury account!");
        }

        expectedPauseStatus.ifPresent(
                status -> Assertions.assertEquals(status, actualInfo.getPauseStatus(), "wrong Pause status"));

        final var actualFees = actualInfo.getCustomFeesList();
        for (var expectedFee : expectedFees) {
            expectedFee.accept(spec, actualFees);
        }

        expectedMemo.ifPresent(s -> Assertions.assertEquals(s, actualInfo.getMemo(), "Wrong memo!"));
        expectedMetadata.ifPresent(
                s -> Assertions.assertEquals(s, actualInfo.getMetadata().toStringUtf8(), "Wrong metadata!"));

        var registry = spec.registry();
        assertFor(actualInfo.getTokenId(), expectedId, (n, r) -> r.getTokenID(n), "Wrong token id!", registry);
        assertFor(
                actualInfo.getExpiry(),
                expectedExpiry,
                (n, r) -> Timestamp.newBuilder().setSeconds(r.getExpiry(token)).build(),
                "Wrong token expiry!",
                registry);
        assertFor(
                actualInfo.getFreezeKey(),
                expectedFreezeKey,
                (n, r) -> searchKeysGlobally ? r.getKey(n) : r.getFreezeKey(n),
                "Wrong token freeze key!",
                registry);
        assertFor(
                actualInfo.getAdminKey(),
                expectedAdminKey,
                (n, r) -> searchKeysGlobally ? r.getKey(n) : r.getAdminKey(n),
                "Wrong token admin key!",
                registry);

        assertFor(
                actualInfo.getWipeKey(),
                expectedWipeKey,
                (n, r) -> searchKeysGlobally ? r.getKey(n) : r.getWipeKey(n),
                "Wrong token wipe key!",
                registry);

        assertFor(
                actualInfo.getKycKey(),
                expectedKycKey,
                (n, r) -> searchKeysGlobally ? r.getKey(n) : r.getKycKey(n),
                "Wrong token KYC key!",
                registry);

        assertFor(
                actualInfo.getSupplyKey(),
                expectedSupplyKey,
                (n, r) -> searchKeysGlobally ? r.getKey(n) : r.getSupplyKey(n),
                "Wrong token supply key!",
                registry);

        assertFor(
                actualInfo.getFeeScheduleKey(),
                expectedFeeScheduleKey,
                (n, r) -> searchKeysGlobally ? r.getKey(n) : r.getFeeScheduleKey(n),
                "Wrong token fee schedule key!",
                registry);

        assertFor(
                actualInfo.getPauseKey(),
                expectedPauseKey,
                (n, r) -> searchKeysGlobally ? r.getKey(n) : r.getPauseKey(n),
                "Wrong token pause key!",
                registry);

        assertFor(
                actualInfo.getMetadataKey(),
                expectedMetadataKey,
                (n, r) -> searchKeysGlobally ? r.getKey(n) : r.getMetadataKey(n),
                "Wrong token metadata key!",
                registry);

        expectedLedgerId.ifPresent(id -> Assertions.assertEquals(id, actualInfo.getLedgerId()));
    }

    private <T, R> void assertFor(
            R actual,
            Optional<T> possible,
            BiFunction<T, HapiSpecRegistry, R> expectedFn,
            String error,
            HapiSpecRegistry registry) {
        if (possible.isPresent()) {
            var expected = expectedFn.apply(possible.get(), registry);
            Assertions.assertEquals(expected, actual, error);
        }
    }

    @Override
    protected void submitWith(HapiSpec spec, Transaction payment) {
        Query query = getTokenInfoQuery(spec, payment, false);
        response = spec.clients().getTokenSvcStub(targetNodeFor(spec), useTls).getTokenInfo(query);
        if (verboseLoggingOn) {
            LOG.info("Info for '{}': {}", () -> token, response.getTokenGetInfo()::getTokenInfo);
        }
    }

    @Override
    protected long lookupCostWith(HapiSpec spec, Transaction payment) throws Throwable {
        Query query = getTokenInfoQuery(spec, payment, true);
        Response response =
                spec.clients().getTokenSvcStub(targetNodeFor(spec), useTls).getTokenInfo(query);
        return costFrom(response);
    }

    private Query getTokenInfoQuery(HapiSpec spec, Transaction payment, boolean costOnly) {
        var id = TxnUtils.asTokenId(token, spec);
        TokenGetInfoQuery getTokenQuery = TokenGetInfoQuery.newBuilder()
                .setHeader(costOnly ? answerCostHeader(payment) : answerHeader(payment))
                .setToken(id)
                .build();
        return Query.newBuilder().setTokenGetInfo(getTokenQuery).build();
    }

    @Override
    protected boolean needsPayment() {
        return true;
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        return MoreObjects.toStringHelper(this).add("token", token);
    }
}
