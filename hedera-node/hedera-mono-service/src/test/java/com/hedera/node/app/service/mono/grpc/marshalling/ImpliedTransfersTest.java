/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.grpc.marshalling;

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.tokenChange;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.ledger.BalanceChange;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.FcCustomFee;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.txns.customfees.CustomFeeSchedules;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImpliedTransfersTest {
    @Mock
    private GlobalDynamicProperties dynamicProperties;

    @Mock
    private AliasManager aliasManager;

    @Mock
    private CustomFeeSchedules customFeeSchedules;

    @Mock
    private CustomFeeSchedules newCustomFeeSchedules;

    @Test
    void detectsMissingCustomFees() {
        final var twoChanges = List.of(tokenChange(new Id(1, 2, 3), asAccount("4.5.6"), 7));
        final var noCustomFees = ImpliedTransfers.invalid(props, TOKEN_WAS_DELETED);
        assertFalse(noCustomFees.hasAssessedCustomFees());
        final var nullCustomFees = ImpliedTransfers.valid(props, twoChanges, entityCustomFees, null);
        assertFalse(nullCustomFees.hasAssessedCustomFees());
        assertSame(Collections.emptyList(), nullCustomFees.getAssessedCustomFeeWrappers());
    }

    @Test
    void impliedXfersObjectContractSanityChecks() {
        // given:
        final var twoChanges = List.of(tokenChange(new Id(1, 2, 3), asAccount("4.5.6"), 7));
        final var oneImpliedXfers = ImpliedTransfers.invalid(props, TOKEN_WAS_DELETED);
        assertFalse(oneImpliedXfers.hasAssessedCustomFees());
        final var twoImpliedXfers = ImpliedTransfers.valid(props, twoChanges, entityCustomFees, assessedCustomFees);
        // and:
        final var oneRepr = "ImpliedTransfers{meta=ImpliedTransfersMeta{code=TOKEN_WAS_DELETED,"
                + " maxExplicitHbarAdjusts=5, maxExplicitTokenAdjusts=50,"
                + " maxExplicitOwnershipChanges=12, maxNestedCustomFees=1,"
                + " maxXferBalanceChanges=20, areNftsEnabled=true, isAutoCreationEnabled=true,"
                + " isLazyCreationEnabled=true, tokenFeeSchedules=[],"
                + " areAllowancesEnabled=true}, changes=[], tokenFeeSchedules=[],"
                + " assessedCustomFees=[], resolvedAliases={}, numAutoCreations=0,"
                + " numLazyCreations=0}";
        final var twoRepr = "ImpliedTransfers{meta=ImpliedTransfersMeta{code=OK, maxExplicitHbarAdjusts=5,"
                + " maxExplicitTokenAdjusts=50, maxExplicitOwnershipChanges=12,"
                + " maxNestedCustomFees=1, maxXferBalanceChanges=20, areNftsEnabled=true,"
                + " isAutoCreationEnabled=true, isLazyCreationEnabled=true,"
                + " tokenFeeSchedules=[CustomFeeMeta[tokenId=0.0.123, treasuryId=2.3.4,"
                + " customFees=[]]], areAllowancesEnabled=true},"
                + " changes=[BalanceChange{token=1.2.3, account=4.5.6, alias=, units=7,"
                + " expectedDecimals=-1}], tokenFeeSchedules=[CustomFeeMeta[tokenId=0.0.123,"
                + " treasuryId=2.3.4, customFees=[]]],"
                + " assessedCustomFees=[AssessedCustomFeeWrapper{token=EntityId{shard=0,"
                + " realm=0, num=123}, account=EntityId{shard=0, realm=0, num=124}, units=123,"
                + " effective payer accounts=["
                + effPayerNum
                + "]}],"
                + " resolvedAliases={}, numAutoCreations=0, numLazyCreations=0}";

        // expect:
        assertNotEquals(oneImpliedXfers, twoImpliedXfers);
        assertNotEquals(oneImpliedXfers.hashCode(), twoImpliedXfers.hashCode());
        // and:
        assertEquals(oneRepr, oneImpliedXfers.toString());
        assertEquals(twoRepr, twoImpliedXfers.toString());
    }

    @Test
    void assessedCustomFeesAreCorrect() {
        // given
        final var tokenId = new Id(0, 0, 2024);
        final var tokenID = IdUtils.asToken("0.0.2024");
        final var payerNum = 502;
        final var payerID = AccountID.newBuilder().setAccountNum(payerNum).build();
        // a balance change to an alias
        final var aliasAccountId = AccountID.newBuilder()
                .setAlias(ByteString.copyFrom("alias".getBytes()))
                .build();
        final var aa = AccountAmount.newBuilder()
                .setAccountID(aliasAccountId)
                .setAmount(20)
                .build();
        final var balanceChange = BalanceChange.changingFtUnits(tokenId, tokenID, aa, payerID);
        // the balance change triggered an auto-creation and the alias was replaced with the new ID
        final long newAccountNum = 12345L;
        final var newAccountID =
                AccountID.newBuilder().setAccountNum(newAccountNum).build();
        balanceChange.replaceNonEmptyAliasWith(EntityNum.fromAccountId(newAccountID));
        // and another balance change to a shard.real.num accountID
        final long secondAccountNum = 1L;
        final var numAccountId =
                AccountID.newBuilder().setAccountNum(secondAccountNum).build();
        final var aa2 = AccountAmount.newBuilder()
                .setAccountID(numAccountId)
                .setAmount(-20)
                .build();
        final var balanceChange2 = BalanceChange.changingFtUnits(tokenId, tokenID, aa2, payerID);

        final var assessedCustomFeeWrapper = new AssessedCustomFeeWrapper(
                EntityId.fromNum(payerNum), 40, new AccountID[] {aliasAccountId, numAccountId});
        final var impliedTransfers = ImpliedTransfers.valid(
                props, List.of(balanceChange, balanceChange2), null, List.of(assessedCustomFeeWrapper));

        // when
        assertTrue(impliedTransfers.hasAssessedCustomFees());
        final var customFeeWrappers = impliedTransfers.getAssessedCustomFeeWrappers();
        final var customFees = impliedTransfers.getUnaliasedAssessedCustomFees();
        assertEquals(customFees.size(), customFeeWrappers.size());

        // then
        assertEquals(1, customFees.size());
        final var fcAssessedCustomFee = customFees.get(0);
        assertArrayEquals(new long[] {newAccountNum, secondAccountNum}, fcAssessedCustomFee.effPayerAccountNums());
    }

    @Test
    void metaRecognizesIdenticalConditions() {
        final var meta = new ImpliedTransfersMeta(props, OK, entityCustomFees, resolvedAliases);

        given(aliasManager.lookupIdBy(anAlias)).willReturn(aNum);
        given(dynamicProperties.maxTransferListSize()).willReturn(maxExplicitHbarAdjusts);
        given(dynamicProperties.maxTokenTransferListSize()).willReturn(maxExplicitTokenAdjusts);
        given(dynamicProperties.maxNftTransfersLen()).willReturn(maxExplicitOwnershipChanges);
        given(dynamicProperties.maxXferBalanceChanges()).willReturn(maxBalanceChanges);
        given(dynamicProperties.maxCustomFeeDepth()).willReturn(maxFeeNesting);
        given(dynamicProperties.areNftsEnabled()).willReturn(areNftsEnabled);
        given(dynamicProperties.isAutoCreationEnabled()).willReturn(autoCreationEnabled);
        given(dynamicProperties.isLazyCreationEnabled()).willReturn(lazyCreationEnabled);
        given(customFeeSchedules.lookupMetaFor(any())).willReturn(entityCustomFees.get(0));
        given(dynamicProperties.areAllowancesEnabled()).willReturn(areAllowancesEnabled);

        // expect:
        assertTrue(meta.wasDerivedFrom(dynamicProperties, customFeeSchedules, aliasManager));

        // and:
        given(aliasManager.lookupIdBy(anAlias)).willReturn(bNum);
        assertFalse(meta.wasDerivedFrom(dynamicProperties, newCustomFeeSchedules, aliasManager));

        // and:
        given(aliasManager.lookupIdBy(anAlias)).willReturn(aNum);
        given(newCustomFeeSchedules.lookupMetaFor(any())).willReturn(newCustomFeeMeta);
        assertFalse(meta.wasDerivedFrom(dynamicProperties, newCustomFeeSchedules, aliasManager));

        // and:
        given(dynamicProperties.maxTransferListSize()).willReturn(maxExplicitHbarAdjusts - 1);
        assertFalse(meta.wasDerivedFrom(dynamicProperties, customFeeSchedules, aliasManager));

        // and:
        given(dynamicProperties.maxTransferListSize()).willReturn(maxExplicitHbarAdjusts);
        given(dynamicProperties.maxTokenTransferListSize()).willReturn(maxExplicitTokenAdjusts + 1);
        assertFalse(meta.wasDerivedFrom(dynamicProperties, customFeeSchedules, aliasManager));

        // and:
        given(dynamicProperties.maxTokenTransferListSize()).willReturn(maxExplicitTokenAdjusts);
        given(dynamicProperties.maxNftTransfersLen()).willReturn(maxExplicitOwnershipChanges - 1);
        assertFalse(meta.wasDerivedFrom(dynamicProperties, customFeeSchedules, aliasManager));

        // and:
        given(dynamicProperties.maxNftTransfersLen()).willReturn(maxExplicitOwnershipChanges);
        given(dynamicProperties.maxXferBalanceChanges()).willReturn(maxBalanceChanges - 1);
        assertFalse(meta.wasDerivedFrom(dynamicProperties, customFeeSchedules, aliasManager));

        // and:
        given(dynamicProperties.maxXferBalanceChanges()).willReturn(maxBalanceChanges);
        given(dynamicProperties.maxCustomFeeDepth()).willReturn(maxFeeNesting + 1);
        assertFalse(meta.wasDerivedFrom(dynamicProperties, customFeeSchedules, aliasManager));

        // and:
        given(dynamicProperties.maxCustomFeeDepth()).willReturn(maxFeeNesting);
        given(dynamicProperties.areNftsEnabled()).willReturn(!areNftsEnabled);
        assertFalse(meta.wasDerivedFrom(dynamicProperties, customFeeSchedules, aliasManager));

        // and:
        given(dynamicProperties.areNftsEnabled()).willReturn(areNftsEnabled);
        given(dynamicProperties.isAutoCreationEnabled()).willReturn(!autoCreationEnabled);
        assertFalse(meta.wasDerivedFrom(dynamicProperties, customFeeSchedules, aliasManager));

        given(dynamicProperties.areAllowancesEnabled()).willReturn(areAllowancesEnabled);
        given(dynamicProperties.isAutoCreationEnabled()).willReturn(!autoCreationEnabled);
        assertFalse(meta.wasDerivedFrom(dynamicProperties, customFeeSchedules, aliasManager));

        given(dynamicProperties.areAllowancesEnabled()).willReturn(!areAllowancesEnabled);
        given(dynamicProperties.isAutoCreationEnabled()).willReturn(autoCreationEnabled);
        assertFalse(meta.wasDerivedFrom(dynamicProperties, customFeeSchedules, aliasManager));

        given(dynamicProperties.isLazyCreationEnabled()).willReturn(!lazyCreationEnabled);
        assertFalse(meta.wasDerivedFrom(dynamicProperties, customFeeSchedules, aliasManager));
    }

    private final int maxExplicitHbarAdjusts = 5;
    private final int maxExplicitTokenAdjusts = 50;
    private final int maxExplicitOwnershipChanges = 12;
    private final int maxFeeNesting = 1;
    private final int maxBalanceChanges = 20;
    private final boolean areNftsEnabled = true;
    private final boolean autoCreationEnabled = true;
    private final boolean lazyCreationEnabled = true;
    private final boolean areAllowancesEnabled = true;
    private final ImpliedTransfersMeta.ValidationProps props = new ImpliedTransfersMeta.ValidationProps(
            maxExplicitHbarAdjusts,
            maxExplicitTokenAdjusts,
            maxExplicitOwnershipChanges,
            maxFeeNesting,
            maxBalanceChanges,
            areNftsEnabled,
            autoCreationEnabled,
            lazyCreationEnabled,
            areAllowancesEnabled);
    private final EntityId customFeeToken = new EntityId(0, 0, 123);
    private final EntityId customFeeCollector = new EntityId(0, 0, 124);
    private final Id someId = new Id(1, 2, 3);
    private final Id someTreasuryId = new Id(2, 3, 4);
    private final List<CustomFeeMeta> entityCustomFees =
            List.of(new CustomFeeMeta(customFeeToken.asId(), someTreasuryId, new ArrayList<>()));
    private static final ByteString anAlias = ByteString.copyFromUtf8("abcdefg");
    private static final EntityNum aNum = EntityNum.fromLong(1_234L);
    private static final EntityNum bNum = EntityNum.fromLong(5_4321L);
    private final Map<ByteString, EntityNum> resolvedAliases = Map.of(anAlias, aNum);
    private final CustomFeeMeta newCustomFeeMeta = new CustomFeeMeta(
            someId, someTreasuryId, List.of(FcCustomFee.fixedFee(10L, customFeeToken, customFeeCollector, false)));
    final AccountID effPayerNum = AccountID.newBuilder().setAccountNum(123L).build();
    private final List<AssessedCustomFeeWrapper> assessedCustomFees = List.of(
            new AssessedCustomFeeWrapper(customFeeCollector, customFeeToken, 123L, new AccountID[] {effPayerNum}));
}
