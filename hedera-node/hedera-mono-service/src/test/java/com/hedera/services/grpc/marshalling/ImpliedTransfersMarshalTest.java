/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.grpc.marshalling;

import static com.hedera.services.grpc.marshalling.ImpliedTransfers.NO_ALIASES;
import static com.hedera.services.grpc.marshalling.ImpliedTransfers.NO_CUSTOM_FEE_META;
import static com.hedera.services.ledger.BalanceChange.changingFtUnits;
import static com.hedera.services.ledger.BalanceChange.changingHbar;
import static com.hedera.services.ledger.BalanceChange.changingNftOwnership;
import static com.hedera.services.ledger.BalanceChange.tokenAdjust;
import static com.hedera.test.utils.IdUtils.adjustFrom;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hedera.test.utils.IdUtils.nftXfer;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALIAS_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.ByteString;
import com.google.protobuf.UInt32Value;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.ledger.PureTransferSemanticChecks;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.customfees.CustomFeeSchedules;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.keys.KeyFactory;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransferList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImpliedTransfersMarshalTest {
    private final List<CustomFeeMeta> mockFinalMeta = List.of(CustomFeeMeta.MISSING_META);
    private final Map<ByteString, EntityNum> mockAliases =
            Map.of(ByteString.copyFromUtf8("A"), EntityNum.fromLong(1L));

    private CryptoTransferTransactionBody op;

    @Mock private FeeAssessor feeAssessor;
    @Mock private CustomFeeSchedules customFeeSchedules;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private PureTransferSemanticChecks xferChecks;
    @Mock private BalanceChangeManager.ChangeManagerFactory changeManagerFactory;
    @Mock private Function<CustomFeeSchedules, CustomSchedulesManager> customSchedulesFactory;
    @Mock private BalanceChangeManager changeManager;
    @Mock private CustomSchedulesManager schedulesManager;
    @Mock private AliasManager aliasManager;
    @Mock private AliasResolver aliasResolver;
    @Mock private Predicate<CryptoTransferTransactionBody> aliasCheck;

    private ImpliedTransfersMarshal subject;

    @BeforeEach
    void setUp() {
        subject =
                new ImpliedTransfersMarshal(
                        feeAssessor,
                        aliasManager,
                        customFeeSchedules,
                        () -> aliasResolver,
                        dynamicProperties,
                        xferChecks,
                        aliasCheck,
                        changeManagerFactory,
                        customSchedulesFactory);
    }

    @Test
    void equalsWorks() {
        final var token1 = EntityNum.fromLong(1010L);
        final var token2 = EntityNum.fromLong(1011L);
        final var account1 = asAccount("0.0.1001");
        final var account2 = asAccount("0.0.1002");
        ImpliedTransfersMarshal.TokenAndAccountID one =
                new ImpliedTransfersMarshal.TokenAndAccountID(token1, account1);
        ImpliedTransfersMarshal.TokenAndAccountID two =
                new ImpliedTransfersMarshal.TokenAndAccountID(token2, account1);
        ImpliedTransfersMarshal.TokenAndAccountID three =
                new ImpliedTransfersMarshal.TokenAndAccountID(token1, account2);
        assertEquals(one, one);
        assertNotEquals(one, two);
        assertNotEquals(two, three);
        assertNotEquals(one.hashCode(), three.hashCode());
        assertNotEquals(two.hashCode(), three.hashCode());
        assertNotEquals(one, token1);
    }

    @Test
    void rejectsPerceivedMissing() {
        setupHbarOnlyFixture();
        setupPropsWithAutoAndLazyCreation(true, false);

        given(aliasCheck.test(op)).willReturn(true);
        given(aliasResolver.resolve(op, aliasManager)).willReturn(op);
        given(aliasResolver.perceivedMissingAliases()).willReturn(1);
        given(aliasResolver.resolutions()).willReturn(mockAliases);

        final var expectedMeta =
                new ImpliedTransfersMeta(
                        propsWithAutoCreation, INVALID_ACCOUNT_ID, NO_CUSTOM_FEE_META, mockAliases);

        final var result = subject.unmarshalFromGrpc(op, payer);

        assertEquals(result.getMeta(), expectedMeta);
    }

    @Test
    void rejectsPerceivedInvalidAliasKey() {
        setupHbarOnlyFixture();
        setupPropsWithAutoAndLazyCreation(true, false);

        given(aliasCheck.test(op)).willReturn(true);
        given(aliasResolver.resolve(op, aliasManager)).willReturn(op);
        given(aliasResolver.perceivedInvalidCreations()).willReturn(1);
        given(aliasResolver.resolutions()).willReturn(mockAliases);

        final var expectedMeta =
                new ImpliedTransfersMeta(
                        propsWithAutoCreation, INVALID_ALIAS_KEY, NO_CUSTOM_FEE_META, mockAliases);

        final var result = subject.unmarshalFromGrpc(op, payer);

        assertEquals(result.getMeta(), expectedMeta);
    }

    @Test
    void rejectsIfAutoAndLazyCreationNotEnabled() {
        setupHbarOnlyFixture();
        setupPropsWithAutoAndLazyCreation(false, false);

        given(aliasCheck.test(op)).willReturn(true);
        given(aliasResolver.resolve(op, aliasManager)).willReturn(op);
        given(aliasResolver.perceivedAutoCreations()).willReturn(1);

        final var expectedMeta =
                new ImpliedTransfersMeta(
                        propsNoAutoCreation, NOT_SUPPORTED, NO_CUSTOM_FEE_META, NO_ALIASES);

        final var result = subject.unmarshalFromGrpc(op, payer);

        assertEquals(result.getMeta(), expectedMeta);
    }

    @Test
    void rejectsAutoCreationIfAutoCreationDisabledAndLazyCreationEnabled() {
        setupHbarOnlyFixture();
        setupPropsWithAutoAndLazyCreation(false, true);

        given(aliasCheck.test(op)).willReturn(true);
        given(aliasResolver.resolve(op, aliasManager)).willReturn(op);
        given(aliasResolver.perceivedAutoCreations()).willReturn(1);

        final var expectedMeta =
                new ImpliedTransfersMeta(
                        propsWithLazyCreation, NOT_SUPPORTED, NO_CUSTOM_FEE_META, NO_ALIASES);

        final var result = subject.unmarshalFromGrpc(op, payer);

        assertEquals(result.getMeta(), expectedMeta);
    }

    @Test
    void rejectsLazyCreationIfAutoAndLazyCreationDisabled() {
        setupHbarOnlyFixture();
        setupPropsWithAutoAndLazyCreation(false, false);

        given(aliasCheck.test(op)).willReturn(true);
        given(aliasResolver.resolve(op, aliasManager)).willReturn(op);
        given(aliasResolver.perceivedLazyCreations()).willReturn(1);

        final var expectedMeta =
                new ImpliedTransfersMeta(
                        propsNoAutoCreation, NOT_SUPPORTED, NO_CUSTOM_FEE_META, NO_ALIASES);

        final var result = subject.unmarshalFromGrpc(op, payer);

        assertEquals(result.getMeta(), expectedMeta);
    }

    @Test
    void startsWithChecks() {
        setupHbarOnlyFixture();
        setupPropsWithAutoAndLazyCreation(true, false);

        final var expectedMeta =
                new ImpliedTransfersMeta(
                        propsWithAutoCreation,
                        TRANSFER_LIST_SIZE_LIMIT_EXCEEDED,
                        Collections.emptyList(),
                        NO_ALIASES);

        givenValidity(TRANSFER_LIST_SIZE_LIMIT_EXCEEDED, propsWithAutoCreation);

        final var result = subject.unmarshalFromGrpc(op, payer);

        assertEquals(result.getMeta(), expectedMeta);
    }

    @Test
    void getsHbarWithOnlyAutoCreationEnabled() {
        setupHbarOnlyFixture();
        setupPropsWithAutoAndLazyCreation(true, false);
        given(aliasCheck.test(op)).willReturn(true);
        given(aliasResolver.resolve(op, aliasManager)).willReturn(op);
        given(aliasResolver.perceivedAutoCreations()).willReturn(1);
        given(aliasResolver.resolutions()).willReturn(mockAliases);

        final var expectedChanges = expNonFeeChanges(false);
        // and:
        final var expectedMeta =
                new ImpliedTransfersMeta(
                        propsWithAutoCreation, OK, Collections.emptyList(), mockAliases, 1);

        givenValidity(OK, propsWithAutoCreation);

        final var result = subject.unmarshalFromGrpc(op, payer);

        // then:
        assertEquals(expectedChanges, result.getAllBalanceChanges());
        assertEquals(result.getMeta(), expectedMeta);
        assertTrue(result.getAssessedCustomFees().isEmpty());
    }

    @Test
    void getsHbarWithOnlyLazyCreationEnabled() {
        setupHbarOnlyFixture();
        setupPropsWithAutoAndLazyCreation(false, true);
        given(aliasCheck.test(op)).willReturn(true);
        given(aliasResolver.resolve(op, aliasManager)).willReturn(op);
        given(aliasResolver.perceivedLazyCreations()).willReturn(1);
        given(aliasResolver.resolutions()).willReturn(mockAliases);

        final var expectedChanges = expNonFeeChanges(false);
        // and:
        final var expectedMeta =
                new ImpliedTransfersMeta(
                        propsWithLazyCreation, OK, Collections.emptyList(), mockAliases, 0, 1);

        givenValidity(OK, propsWithLazyCreation);

        final var result = subject.unmarshalFromGrpc(op, payer);

        // then:
        assertEquals(expectedChanges, result.getAllBalanceChanges());
        assertEquals(result.getMeta(), expectedMeta);
        assertTrue(result.getAssessedCustomFees().isEmpty());
    }

    @Test
    void getsHbarWithBothAutoAndLazyCreationEnabled() {
        setupHbarOnlyFixture();
        setupPropsWithAutoAndLazyCreation(true, true);
        given(aliasCheck.test(op)).willReturn(true);
        given(aliasResolver.resolve(op, aliasManager)).willReturn(op);
        given(aliasResolver.perceivedAutoCreations()).willReturn(1);
        given(aliasResolver.perceivedLazyCreations()).willReturn(1);
        given(aliasResolver.resolutions()).willReturn(mockAliases);

        final var expectedChanges = expNonFeeChanges(false);
        // and:
        final var expectedMeta =
                new ImpliedTransfersMeta(
                        propsWithAutoAndLazyCreation,
                        OK,
                        Collections.emptyList(),
                        mockAliases,
                        1,
                        1);

        givenValidity(OK, propsWithAutoAndLazyCreation);

        final var result = subject.unmarshalFromGrpc(op, payer);

        // then:
        assertEquals(expectedChanges, result.getAllBalanceChanges());
        assertEquals(result.getMeta(), expectedMeta);
        assertTrue(result.getAssessedCustomFees().isEmpty());
    }

    @Test
    void hasAliasInChanges() {
        Key aliasA = KeyFactory.getDefaultInstance().newEd25519();
        Key aliasB = KeyFactory.getDefaultInstance().newEd25519();
        AccountID a =
                AccountID.newBuilder()
                        .setShardNum(0)
                        .setRealmNum(0)
                        .setAccountNum(9_999L)
                        .setAlias(aliasA.toByteString())
                        .build();
        AccountID validAliasAccount =
                AccountID.newBuilder().setAlias(aliasB.toByteString()).build();
        setupPropsWithAutoAndLazyCreation(true, false);

        final var builder =
                CryptoTransferTransactionBody.newBuilder()
                        .setTransfers(
                                TransferList.newBuilder()
                                        .addAccountAmounts(adjustFrom(a, -100))
                                        .addAccountAmounts(adjustFrom(validAliasAccount, 100))
                                        .build());
        op = builder.build();

        final List<BalanceChange> expectedChanges = new ArrayList<>();
        expectedChanges.add(changingHbar(adjustFrom(a, -100), payer));
        expectedChanges.add(changingHbar(adjustFrom(validAliasAccount, +100), payer));

        final var expectedMeta =
                new ImpliedTransfersMeta(
                        propsWithAutoCreation, OK, Collections.emptyList(), NO_ALIASES);

        givenValidity(OK, propsWithAutoCreation);

        final var result = subject.unmarshalFromGrpc(op, payer);

        assertEquals(expectedChanges, result.getAllBalanceChanges());
        assertEquals(aliasA.toByteString(), result.getAllBalanceChanges().get(0).alias());
        assertEquals(aliasB.toByteString(), result.getAllBalanceChanges().get(1).alias());
        assertEquals(result.getMeta(), expectedMeta);
        assertTrue(result.getAssessedCustomFees().isEmpty());
    }

    @Test
    void aggregatesAccountAmountsAsExpected() {
        final var account1 = asAccount("0.0.1001");
        final var account2 = asAccount("0.0.1002");
        final var account3 = asAccount("0.0.1003");
        final var token1 = asToken("0.0.1010");
        final var aa1 =
                AccountAmount.newBuilder()
                        .setAccountID(account1)
                        .setAmount(-100)
                        .setIsApproval(true)
                        .build();
        final var aa2 =
                AccountAmount.newBuilder()
                        .setAccountID(account2)
                        .setAmount(100)
                        .setIsApproval(true)
                        .build();
        final var aa3 =
                AccountAmount.newBuilder()
                        .setAccountID(account1)
                        .setAmount(-100)
                        .setIsApproval(false)
                        .build();
        final var aa4 =
                AccountAmount.newBuilder()
                        .setAccountID(account2)
                        .setAmount(100)
                        .setIsApproval(false)
                        .build();
        final var aa5 =
                AccountAmount.newBuilder()
                        .setAccountID(account3)
                        .setAmount(-50)
                        .setIsApproval(true)
                        .build();
        final var aa6 =
                AccountAmount.newBuilder()
                        .setAccountID(account1)
                        .setAmount(50)
                        .setIsApproval(true)
                        .build();
        setupPropsWithAutoAndLazyCreation(true, false);

        final var builder =
                CryptoTransferTransactionBody.newBuilder()
                        .setTransfers(
                                TransferList.newBuilder()
                                        .addAllAccountAmounts(List.of(aa1, aa2, aa3, aa4, aa5, aa6))
                                        .build())
                        .addTokenTransfers(
                                TokenTransferList.newBuilder()
                                        .setToken(token1)
                                        .setExpectedDecimals(UInt32Value.of(1))
                                        .addAllTransfers(List.of(aa1, aa2, aa3, aa4, aa5, aa6))
                                        .build());
        op = builder.build();
        final var bc1 = changingHbar(aa1, payer);
        bc1.aggregateUnits(-50);
        final var bc2 = changingHbar(aa2, payer);
        bc2.aggregateUnits(+100);
        final var bc3 = changingHbar(aa5, payer);
        final var bc4 = changingFtUnits(Id.fromGrpcToken(token1), token1, aa1, payer);
        bc4.aggregateUnits(-50);
        final var bc5 = changingFtUnits(Id.fromGrpcToken(token1), token1, aa2, payer);
        bc5.aggregateUnits(+100);
        final var bc6 = changingFtUnits(Id.fromGrpcToken(token1), token1, aa5, payer);

        final List<BalanceChange> expectedChanges = new ArrayList<>();
        expectedChanges.add(bc1);
        expectedChanges.add(bc2);
        expectedChanges.add(bc3);
        expectedChanges.add(bc4);
        expectedChanges.add(bc5);
        expectedChanges.add(bc6);

        givenValidity(OK, propsWithAutoCreation);
        given(changeManagerFactory.from(any(), anyInt())).willReturn(changeManager);
        given(customSchedulesFactory.apply(customFeeSchedules)).willReturn(schedulesManager);

        final var result = subject.unmarshalFromGrpc(op, payer);

        assertEquals(
                expectedChanges.get(0).getAggregatedUnits(),
                result.getAllBalanceChanges().get(0).getAggregatedUnits());
        assertEquals(
                expectedChanges.get(1).getAggregatedUnits(),
                result.getAllBalanceChanges().get(1).getAggregatedUnits());
        assertEquals(
                expectedChanges.get(0).getAllowanceUnits(),
                result.getAllBalanceChanges().get(0).getAllowanceUnits());
        assertEquals(
                expectedChanges.get(1).getAllowanceUnits(),
                result.getAllBalanceChanges().get(1).getAllowanceUnits());
        assertEquals(
                expectedChanges.get(2).getAggregatedUnits(),
                result.getAllBalanceChanges().get(2).getAggregatedUnits());
        assertEquals(
                expectedChanges.get(3).getAggregatedUnits(),
                result.getAllBalanceChanges().get(3).getAggregatedUnits());
        assertEquals(
                expectedChanges.get(2).getAllowanceUnits(),
                result.getAllBalanceChanges().get(2).getAllowanceUnits());
        assertEquals(
                expectedChanges.get(3).getAllowanceUnits(),
                result.getAllBalanceChanges().get(3).getAllowanceUnits());
    }

    @Test
    void getsHappyPath() {
        // setup:
        setupFullFixture();
        setupPropsWithAutoAndLazyCreation(true, false);
        // and:
        final var nonFeeChanges = expNonFeeChanges(true);
        // and:
        final var expectedMeta =
                new ImpliedTransfersMeta(propsWithAutoCreation, OK, mockFinalMeta, NO_ALIASES);

        givenValidity(OK, propsWithAutoCreation);
        // and:
        given(changeManagerFactory.from(nonFeeChanges, 3)).willReturn(changeManager);
        given(customSchedulesFactory.apply(customFeeSchedules)).willReturn(schedulesManager);
        given(changeManager.nextAssessableChange())
                .willReturn(aTrigger)
                .willReturn(bTrigger)
                .willReturn(null);
        given(
                        feeAssessor.assess(
                                eq(aTrigger),
                                eq(schedulesManager),
                                eq(changeManager),
                                anyList(),
                                eq(propsWithAutoCreation)))
                .willReturn(OK);
        given(
                        feeAssessor.assess(
                                eq(bTrigger),
                                eq(schedulesManager),
                                eq(changeManager),
                                anyList(),
                                eq(propsWithAutoCreation)))
                .willReturn(OK);
        // and:
        given(schedulesManager.metaUsed()).willReturn(mockFinalMeta);

        // when:
        final var result = subject.unmarshalFromGrpc(op, payer);

        // then:
        assertEquals(expectedMeta, result.getMeta());

        // assert decimal changes
        assertTrue(result.getAllBalanceChanges().get(3).hasExpectedDecimals());
        assertEquals(2, result.getAllBalanceChanges().get(3).getExpectedDecimals());
        assertEquals(
                anotherId.getTokenNum(), result.getAllBalanceChanges().get(3).getToken().num());
        assertFalse(result.getAllBalanceChanges().get(4).hasExpectedDecimals());
        assertEquals(
                result.getAllBalanceChanges().get(3).getToken(),
                result.getAllBalanceChanges().get(4).getToken());
    }

    @Test
    void getsUnhappyPath() {
        // setup:
        setupFullFixture();
        setupPropsWithAutoAndLazyCreation(true, false);
        // and:
        final var nonFeeChanges = expNonFeeChanges(true);
        // and:
        final var expectedMeta =
                new ImpliedTransfersMeta(
                        propsWithAutoCreation,
                        CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH,
                        mockFinalMeta,
                        NO_ALIASES);

        givenValidity(OK, propsWithAutoCreation);
        // and:
        given(changeManagerFactory.from(nonFeeChanges, 3)).willReturn(changeManager);
        given(customSchedulesFactory.apply(customFeeSchedules)).willReturn(schedulesManager);
        given(changeManager.nextAssessableChange())
                .willReturn(aTrigger)
                .willReturn(bTrigger)
                .willReturn(null);
        given(
                        feeAssessor.assess(
                                eq(aTrigger),
                                eq(schedulesManager),
                                eq(changeManager),
                                anyList(),
                                eq(propsWithAutoCreation)))
                .willReturn(CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH);
        // and:
        given(schedulesManager.metaUsed()).willReturn(mockFinalMeta);

        // when:
        final var result = subject.unmarshalFromGrpc(op, payer);

        // then:
        assertEquals(expectedMeta, result.getMeta());
    }

    private void givenValidity(ResponseCodeEnum s, ImpliedTransfersMeta.ValidationProps props) {
        given(xferChecks.fullPureValidation(op.getTransfers(), op.getTokenTransfersList(), props))
                .willReturn(s);
    }

    private void setupPropsWithAutoAndLazyCreation(
            final boolean isAutoCreationEnabled, final boolean isLazyCreationEnabled) {
        given(dynamicProperties.maxTransferListSize()).willReturn(maxExplicitHbarAdjusts);
        given(dynamicProperties.maxTokenTransferListSize()).willReturn(maxExplicitTokenAdjusts);
        given(dynamicProperties.maxNftTransfersLen()).willReturn(maxExplicitOwnershipChanges);
        given(dynamicProperties.maxXferBalanceChanges()).willReturn(maxBalanceChanges);
        given(dynamicProperties.maxCustomFeeDepth()).willReturn(maxFeeNesting);
        given(dynamicProperties.areNftsEnabled()).willReturn(areNftsEnabled);
        given(dynamicProperties.isAutoCreationEnabled()).willReturn(isAutoCreationEnabled);
        given(dynamicProperties.isLazyCreationEnabled()).willReturn(isLazyCreationEnabled);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(areAllowancesEnabled);
    }

    private void setupFullFixture() {
        setupFixtureOp(true);
    }

    private void setupHbarOnlyFixture() {
        setupFixtureOp(false);
    }

    private void setupFixtureOp(boolean incTokens) {
        var hbarAdjusts =
                TransferList.newBuilder()
                        .addAccountAmounts(adjustFrom(a, -100))
                        .addAccountAmounts(adjustFrom(b, 50))
                        .addAccountAmounts(adjustFrom(c, 50))
                        .build();
        final var builder = CryptoTransferTransactionBody.newBuilder().setTransfers(hbarAdjusts);
        if (incTokens) {
            builder.addTokenTransfers(
                            TokenTransferList.newBuilder()
                                    .setToken(anotherId)
                                    .setExpectedDecimals(UInt32Value.of(2))
                                    .addAllTransfers(
                                            List.of(
                                                    adjustFrom(a, -50),
                                                    adjustFrom(b, 25),
                                                    adjustFrom(c, 25))))
                    .addTokenTransfers(
                            TokenTransferList.newBuilder()
                                    .setToken(anId)
                                    .addAllTransfers(
                                            List.of(adjustFrom(b, -100), adjustFrom(c, 100))))
                    .addTokenTransfers(
                            TokenTransferList.newBuilder()
                                    .setToken(yetAnotherId)
                                    .addAllNftTransfers(
                                            List.of(
                                                    nftXfer(a, b, serialNumberA),
                                                    nftXfer(a, b, serialNumberB))));
        }
        op = builder.build();
    }

    private List<BalanceChange> expNonFeeChanges(boolean incTokens) {
        final List<BalanceChange> ans = new ArrayList<>();
        ans.add(changingHbar(adjustFrom(aModel, -100), payer));
        ans.add(changingHbar(adjustFrom(bModel, +50), payer));
        ans.add(changingHbar(adjustFrom(cModel, +50), payer));
        if (incTokens) {
            final var adjustOne =
                    tokenAdjust(aAccount, Id.fromGrpcToken(anotherId), -50, payer, false);
            adjustOne.setExpectedDecimals(2);

            ans.add(adjustOne);
            ans.add(tokenAdjust(bAccount, Id.fromGrpcToken(anotherId), 25, payer, false));
            ans.add(tokenAdjust(cAccount, Id.fromGrpcToken(anotherId), 25, payer, false));
            ans.add(tokenAdjust(bAccount, Id.fromGrpcToken(anId), -100, payer, false));
            ans.add(tokenAdjust(cAccount, Id.fromGrpcToken(anId), 100, payer, false));
            ans.add(
                    changingNftOwnership(
                            Id.fromGrpcToken(yetAnotherId),
                            yetAnotherId,
                            nftXfer(a, b, serialNumberA),
                            payer));
            ans.add(
                    changingNftOwnership(
                            Id.fromGrpcToken(yetAnotherId),
                            yetAnotherId,
                            nftXfer(a, b, serialNumberB),
                            payer));
        }
        return ans;
    }

    private final int maxExplicitHbarAdjusts = 5;
    private final int maxExplicitTokenAdjusts = 50;
    private final int maxExplicitOwnershipChanges = 12;
    private final int maxFeeNesting = 1;
    private final int maxBalanceChanges = 20;
    private final boolean areNftsEnabled = true;
    private boolean areAllowancesEnabled = true;
    private final ImpliedTransfersMeta.ValidationProps propsWithAutoCreation =
            new ImpliedTransfersMeta.ValidationProps(
                    maxExplicitHbarAdjusts,
                    maxExplicitTokenAdjusts,
                    maxExplicitOwnershipChanges,
                    maxFeeNesting,
                    maxBalanceChanges,
                    areNftsEnabled,
                    true,
                    false,
                    areAllowancesEnabled);
    private final ImpliedTransfersMeta.ValidationProps propsNoAutoCreation =
            new ImpliedTransfersMeta.ValidationProps(
                    maxExplicitHbarAdjusts,
                    maxExplicitTokenAdjusts,
                    maxExplicitOwnershipChanges,
                    maxFeeNesting,
                    maxBalanceChanges,
                    areNftsEnabled,
                    false,
                    false,
                    areAllowancesEnabled);

    private final ImpliedTransfersMeta.ValidationProps propsWithAutoAndLazyCreation =
            new ImpliedTransfersMeta.ValidationProps(
                    maxExplicitHbarAdjusts,
                    maxExplicitTokenAdjusts,
                    maxExplicitOwnershipChanges,
                    maxFeeNesting,
                    maxBalanceChanges,
                    areNftsEnabled,
                    true,
                    true,
                    areAllowancesEnabled);

    private final ImpliedTransfersMeta.ValidationProps propsWithLazyCreation =
            new ImpliedTransfersMeta.ValidationProps(
                    maxExplicitHbarAdjusts,
                    maxExplicitTokenAdjusts,
                    maxExplicitOwnershipChanges,
                    maxFeeNesting,
                    maxBalanceChanges,
                    areNftsEnabled,
                    false,
                    true,
                    areAllowancesEnabled);

    private final AccountID aModel = asAccount("1.2.3");
    private final AccountID bModel = asAccount("2.3.4");
    private final AccountID cModel = asAccount("3.4.5");
    private final long serialNumberA = 12;
    private final long serialNumberB = 13;
    private final TokenID anId = asToken("0.0.75231");
    private final TokenID anotherId = asToken("0.0.75232");
    private final TokenID yetAnotherId = asToken("0.0.75233");
    private final Id aAccount = new Id(1, 2, 3);
    private final Id bAccount = new Id(2, 3, 4);
    private final Id cAccount = new Id(3, 4, 5);
    private final AccountID a = asAccount("1.2.3");
    private final AccountID b = asAccount("2.3.4");
    private final AccountID c = asAccount("3.4.5");
    private final AccountID payer = AccountID.newBuilder().setAccountNum(1_234L).build();

    private final BalanceChange aTrigger =
            BalanceChange.tokenAdjust(aAccount, Id.fromGrpcToken(anId), -1);
    private final BalanceChange bTrigger =
            BalanceChange.tokenAdjust(bAccount, Id.fromGrpcToken(anotherId), -2);
}
