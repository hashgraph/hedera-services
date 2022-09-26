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
package com.hedera.services.txns.token.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NewRelsTest {
    private static final int MAX_PER_ACCOUNT = 1_000;
    private static final Id treasuryId = new Id(0, 0, 777);
    private static final Id collectorId = new Id(0, 0, 888);

    @Mock private Token provisionalToken;
    @Mock private Account treasury;
    @Mock private Account collector;
    @Mock private FcCustomFee feeNoCollectorAssociationRequired;
    @Mock private FcCustomFee feeCollectorAssociationRequired;
    @Mock private FcCustomFee feeSameCollectorAssociationRequired;
    @Mock private TokenRelationship treasuryRel;
    @Mock private TokenRelationship collectorRel;
    @Mock private TypedTokenStore tokenStore;
    @Mock private GlobalDynamicProperties dynamicProperties;

    @Test
    void associatesAsExpected() {
        given(treasury.getId()).willReturn(treasuryId);
        given(collector.getId()).willReturn(collectorId);
        given(provisionalToken.getTreasury()).willReturn(treasury);
        given(treasury.associateWith(anyList(), any(), anyBoolean(), anyBoolean(), any()))
                .willReturn(List.of(treasuryRel, collectorRel));
        given(feeCollectorAssociationRequired.requiresCollectorAutoAssociation()).willReturn(true);
        given(feeCollectorAssociationRequired.getValidatedCollector()).willReturn(collector);
        given(feeSameCollectorAssociationRequired.requiresCollectorAutoAssociation())
                .willReturn(true);
        given(feeSameCollectorAssociationRequired.getValidatedCollector()).willReturn(collector);
        given(provisionalToken.getCustomFees())
                .willReturn(
                        List.of(
                                feeCollectorAssociationRequired,
                                feeNoCollectorAssociationRequired,
                                feeSameCollectorAssociationRequired));

        final var ans = NewRels.listFrom(provisionalToken, tokenStore, dynamicProperties);

        assertEquals(List.of(treasuryRel, collectorRel), ans);
        verify(treasury)
                .associateWith(
                        List.of(provisionalToken), tokenStore, false, true, dynamicProperties);
        verify(collector)
                .associateWith(
                        List.of(provisionalToken), tokenStore, false, true, dynamicProperties);
    }
}
