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

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.tokenChange;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.ByteString;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcAssessedCustomFee;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.customfees.CustomFeeSchedules;
import com.hedera.services.utils.EntityNum;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImpliedTransfersTest {
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private AliasManager aliasManager;
    @Mock private CustomFeeSchedules customFeeSchedules;
    @Mock private CustomFeeSchedules newCustomFeeSchedules;

    @Test
    void impliedXfersObjectContractSanityChecks() {
        // given:
        final var twoChanges = List.of(tokenChange(new Id(1, 2, 3), asAccount("4.5.6"), 7));
        final var oneImpliedXfers = ImpliedTransfers.invalid(props, TOKEN_WAS_DELETED);
        final var twoImpliedXfers =
                ImpliedTransfers.valid(props, twoChanges, entityCustomFees, assessedCustomFees);
        // and:
        final var oneRepr =
                "ImpliedTransfers{meta=ImpliedTransfersMeta{code=TOKEN_WAS_DELETED,"
                    + " maxExplicitHbarAdjusts=5, maxExplicitTokenAdjusts=50,"
                    + " maxExplicitOwnershipChanges=12, maxNestedCustomFees=1,"
                    + " maxXferBalanceChanges=20, areNftsEnabled=true, isAutoCreationEnabled=true,"
                    + " tokenFeeSchedules=[], areAllowancesEnabled=true}, changes=[],"
                    + " tokenFeeSchedules=[], assessedCustomFees=[], resolvedAliases={},"
                    + " numAutoCreations=0}";
        final var twoRepr =
                "ImpliedTransfers{meta=ImpliedTransfersMeta{code=OK, maxExplicitHbarAdjusts=5,"
                    + " maxExplicitTokenAdjusts=50, maxExplicitOwnershipChanges=12,"
                    + " maxNestedCustomFees=1, maxXferBalanceChanges=20, areNftsEnabled=true,"
                    + " isAutoCreationEnabled=true,"
                    + " tokenFeeSchedules=[CustomFeeMeta[tokenId=0.0.123, treasuryId=2.3.4,"
                    + " customFees=[]]], areAllowancesEnabled=true},"
                    + " changes=[BalanceChange{token=1.2.3, account=4.5.6, alias=, units=7,"
                    + " expectedDecimals=-1}], tokenFeeSchedules=[CustomFeeMeta[tokenId=0.0.123,"
                    + " treasuryId=2.3.4, customFees=[]]],"
                    + " assessedCustomFees=[FcAssessedCustomFee{token=EntityId{shard=0, realm=0,"
                    + " num=123}, account=EntityId{shard=0, realm=0, num=124}, units=123, effective"
                    + " payer accounts=[123]}], resolvedAliases={}, numAutoCreations=0}";

        // expect:
        assertNotEquals(oneImpliedXfers, twoImpliedXfers);
        assertNotEquals(oneImpliedXfers.hashCode(), twoImpliedXfers.hashCode());
        // and:
        assertEquals(oneRepr, oneImpliedXfers.toString());
        assertEquals(twoRepr, twoImpliedXfers.toString());
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
    }

    private final int maxExplicitHbarAdjusts = 5;
    private final int maxExplicitTokenAdjusts = 50;
    private final int maxExplicitOwnershipChanges = 12;
    private final int maxFeeNesting = 1;
    private final int maxBalanceChanges = 20;
    private final boolean areNftsEnabled = true;
    private final boolean autoCreationEnabled = true;
    private final boolean areAllowancesEnabled = true;
    private final ImpliedTransfersMeta.ValidationProps props =
            new ImpliedTransfersMeta.ValidationProps(
                    maxExplicitHbarAdjusts,
                    maxExplicitTokenAdjusts,
                    maxExplicitOwnershipChanges,
                    maxFeeNesting,
                    maxBalanceChanges,
                    areNftsEnabled,
                    autoCreationEnabled,
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
    private final CustomFeeMeta newCustomFeeMeta =
            new CustomFeeMeta(
                    someId,
                    someTreasuryId,
                    List.of(FcCustomFee.fixedFee(10L, customFeeToken, customFeeCollector, false)));
    private final List<FcAssessedCustomFee> assessedCustomFees =
            List.of(
                    new FcAssessedCustomFee(
                            customFeeCollector, customFeeToken, 123L, new long[] {123L}));
}
