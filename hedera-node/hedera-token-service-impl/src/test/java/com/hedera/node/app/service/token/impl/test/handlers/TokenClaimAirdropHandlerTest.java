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

package com.hedera.node.app.service.token.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.PendingAirdropId;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.service.token.records.CryptoTransferStreamBuilder;
import com.hedera.node.app.spi.workflows.HandleException;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

public class TokenClaimAirdropHandlerTest extends CryptoTransferHandlerTestBase {

    private final PendingAirdropId firstPendingAirdropId = PendingAirdropId.newBuilder()
            .senderId(spenderId)
            .receiverId(tokenReceiverNoAssociationId)
            .fungibleTokenType(fungibleTokenId)
            .build();
    private final PendingAirdropId secondPendingAirdropId = firstPendingAirdropId
            .copyBuilder()
            .fungibleTokenType(fungibleTokenIDB)
            .build();
    private final PendingAirdropId thirdPendingAirdropId = firstPendingAirdropId
            .copyBuilder()
            .fungibleTokenType(fungibleTokenIDC)
            .build();

    private void stateInitialize() {
        // setup all default states
        handlerTestBaseInternalSetUp(true);
        // setup airdrop states
        givenPendingFungibleTokenAirdrop(fungibleTokenId, spenderId, tokenReceiverNoAssociationId, 10L);
        givenPendingFungibleTokenAirdrop(fungibleTokenIDB, spenderId, tokenReceiverNoAssociationId, 10L);
        givenPendingFungibleTokenAirdrop(fungibleTokenIDC, spenderId, tokenReceiverNoAssociationId, 10L);

        // remove custom fees
        removeTokenCustomFee(fungibleTokenId);
        removeTokenCustomFee(fungibleTokenIDB);
        removeTokenCustomFee(fungibleTokenIDC);

        // rebuild stores with updated tokens changes
        refreshReadableStores();
        refreshWritableStores();

        // mock stores
        givenStoresAndConfig(handleContext);

        // associate spender with the tokens
        associateToken(spenderId, fungibleTokenId);
        associateToken(spenderId, fungibleTokenIDB);
        associateToken(spenderId, fungibleTokenIDC);

        // mock record builder
        given(handleContext.savepointStack()).willReturn(stack);
        given(stack.getBaseBuilder(CryptoTransferStreamBuilder.class)).willReturn(tokenAirdropRecordBuilder);

        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        given(expiryValidator.expirationStatus(any(), anyBoolean(), anyLong())).willReturn(OK);
    }

    @Test
    void pendingAirDropListMoreThanTenThrows() {
        var airdrops = new ArrayList<PendingAirdropId>();
        for (int i = 0; i < 11; i++) {
            airdrops.add(PendingAirdropId.DEFAULT);
        }
        givenClaimAirdrop(airdrops);

        final var msg = assertThrows(HandleException.class, () -> tokenClaimAirdropHandler.handle(handleContext));
        assertEquals(ResponseCodeEnum.MAX_PENDING_AIRDROP_ID_EXCEEDED, msg.getStatus());
    }

    @Test
    void claimFirstAirdrop() {
        stateInitialize();

        // claim first airdrop
        var airdrops = new ArrayList<PendingAirdropId>();
        airdrops.add(firstPendingAirdropId);
        givenClaimAirdrop(airdrops);

        tokenClaimAirdropHandler.handle(handleContext);

        // check if we clear the pending state
        assertThat(writableAirdropStore.sizeOfState()).isEqualTo(2);
        assertThat(writableAirdropStore.get(firstPendingAirdropId)).isNull();

        // check if we link properly the neighbour elements
        assertThat(writableAirdropStore.get(secondPendingAirdropId).nextAirdrop())
                .isNull();
        assertThat(writableAirdropStore.get(secondPendingAirdropId).previousAirdrop())
                .isEqualTo(thirdPendingAirdropId);
        assertThat(writableAirdropStore.get(thirdPendingAirdropId).nextAirdrop())
                .isEqualTo(secondPendingAirdropId);
        assertThat(writableAirdropStore.get(thirdPendingAirdropId).previousAirdrop())
                .isNull();

        // check if we have the proper transfer
        assertThat(writableTokenRelStore
                        .get(tokenReceiverNoAssociationId, fungibleTokenId)
                        .balance())
                .isEqualTo(10);

        // check sender's pending airdrops head id and count
        assertThat(writableAccountStore.get(spenderId).headPendingAirdropId()).isEqualTo(thirdPendingAirdropId);
    }

    @Test
    void claimSecondAirdrop() {
        stateInitialize();

        // claim second airdrop
        var airdrops = new ArrayList<PendingAirdropId>();
        airdrops.add(secondPendingAirdropId);
        givenClaimAirdrop(airdrops);

        tokenClaimAirdropHandler.handle(handleContext);

        // check if we clear the pending state
        assertThat(writableAirdropStore.sizeOfState()).isEqualTo(2);
        assertThat(writableAirdropStore.get(secondPendingAirdropId)).isNull();

        // check if we link properly the neighbour elements
        assertThat(writableAirdropStore.get(firstPendingAirdropId).nextAirdrop())
                .isNull();
        assertThat(writableAirdropStore.get(firstPendingAirdropId).previousAirdrop())
                .isEqualTo(thirdPendingAirdropId);
        assertThat(writableAirdropStore.get(thirdPendingAirdropId).nextAirdrop())
                .isEqualTo(firstPendingAirdropId);
        assertThat(writableAirdropStore.get(thirdPendingAirdropId).previousAirdrop())
                .isNull();

        // check if we have new association
        assertThat(writableTokenRelStore.get(tokenReceiverNoAssociationId, fungibleTokenIDB))
                .isNotNull();

        // check if we have the proper transfer
        assertThat(writableTokenRelStore
                        .get(tokenReceiverNoAssociationId, fungibleTokenIDB)
                        .balance())
                .isEqualTo(10);

        // check sender's pending airdrops head id and count
        assertThat(writableAccountStore.get(spenderId).headPendingAirdropId()).isEqualTo(thirdPendingAirdropId);
    }

    @Test
    void claimThirdAirdrop() {
        stateInitialize();

        // claim third airdrop
        var airdrops = new ArrayList<PendingAirdropId>();
        airdrops.add(thirdPendingAirdropId);
        givenClaimAirdrop(airdrops);

        tokenClaimAirdropHandler.handle(handleContext);

        // check if we clear the pending state
        assertThat(writableAirdropStore.sizeOfState()).isEqualTo(2);
        assertThat(writableAirdropStore.get(thirdPendingAirdropId)).isNull();

        // check if we link properly the neighbour elements
        assertThat(writableAirdropStore.get(secondPendingAirdropId).previousAirdrop())
                .isNull();
        assertThat(writableAirdropStore.get(secondPendingAirdropId).nextAirdrop())
                .isEqualTo(firstPendingAirdropId);
        assertThat(writableAirdropStore.get(firstPendingAirdropId).previousAirdrop())
                .isEqualTo(secondPendingAirdropId);
        assertThat(writableAirdropStore.get(firstPendingAirdropId).nextAirdrop())
                .isNull();

        // check if we have new association
        assertThat(writableTokenRelStore.get(tokenReceiverNoAssociationId, fungibleTokenIDC))
                .isNotNull();

        // check if we have the proper transfer
        assertThat(writableTokenRelStore
                        .get(tokenReceiverNoAssociationId, fungibleTokenIDC)
                        .balance())
                .isEqualTo(10);

        // check sender's pending airdrops head id and count
        assertThat(writableAccountStore.get(spenderId).headPendingAirdropId()).isEqualTo(secondPendingAirdropId);
    }

    @Test
    void claimMultipleAirdrops() {
        stateInitialize();

        // set up fourth additional pending airdrop
        var fourthPendingAirdropId = setUpFourthPendingAirdrop();

        // assert initial state
        assertThat(writableAccountStore.get(spenderId).headPendingAirdropId()).isEqualTo(fourthPendingAirdropId);
        assertThat(writableAccountStore.get(spenderId).numberPendingAirdrops()).isEqualTo(4);

        // claim second and third airdrop
        var airdrops = new ArrayList<PendingAirdropId>();
        airdrops.add(secondPendingAirdropId);
        airdrops.add(thirdPendingAirdropId);
        givenClaimAirdrop(airdrops);

        tokenClaimAirdropHandler.handle(handleContext);

        // check if we clear the pending state
        assertThat(writableAirdropStore.sizeOfState()).isEqualTo(2);
        assertThat(writableAirdropStore.get(secondPendingAirdropId)).isNull();
        assertThat(writableAirdropStore.get(thirdPendingAirdropId)).isNull();

        // check if we link properly the neighbour elements
        assertThat(writableAirdropStore.get(firstPendingAirdropId).previousAirdrop())
                .isEqualTo(fourthPendingAirdropId);
        assertThat(writableAirdropStore.get(firstPendingAirdropId).nextAirdrop())
                .isNull();
        assertThat(writableAirdropStore.get(fourthPendingAirdropId).previousAirdrop())
                .isNull();
        assertThat(writableAirdropStore.get(fourthPendingAirdropId).nextAirdrop())
                .isEqualTo(firstPendingAirdropId);

        // check if we have new association
        assertThat(writableTokenRelStore.get(tokenReceiverNoAssociationId, fungibleTokenIDB))
                .isNotNull();
        assertThat(writableTokenRelStore.get(tokenReceiverNoAssociationId, fungibleTokenIDC))
                .isNotNull();

        // check if we have the proper transfer
        assertThat(writableTokenRelStore
                        .get(tokenReceiverNoAssociationId, fungibleTokenIDB)
                        .balance())
                .isEqualTo(10);
        assertThat(writableTokenRelStore
                        .get(tokenReceiverNoAssociationId, fungibleTokenIDC)
                        .balance())
                .isEqualTo(10);

        // check sender's pending airdrops head id and count
        assertThat(writableAccountStore.get(spenderId).headPendingAirdropId()).isEqualTo(fourthPendingAirdropId);
        assertThat(writableAccountStore.get(spenderId).numberPendingAirdrops()).isEqualTo(2);
    }

    @Test
    void claimMultipleAirdrops2() {
        stateInitialize();
        // set up fourth additional pending airdrop
        var fourthPendingAirdropId = setUpFourthPendingAirdrop();

        // assert initial state
        assertThat(writableAccountStore.get(spenderId).headPendingAirdropId()).isEqualTo(fourthPendingAirdropId);
        assertThat(writableAccountStore.get(spenderId).numberPendingAirdrops()).isEqualTo(4);

        // claim second and third airdrop
        var airdrops = new ArrayList<PendingAirdropId>();
        airdrops.add(secondPendingAirdropId);
        airdrops.add(fourthPendingAirdropId);
        givenClaimAirdrop(airdrops);

        tokenClaimAirdropHandler.handle(handleContext);

        // check if we clear the pending state
        assertThat(writableAirdropStore.sizeOfState()).isEqualTo(2);
        assertThat(writableAirdropStore.get(secondPendingAirdropId)).isNull();
        assertThat(writableAirdropStore.get(fourthPendingAirdropId)).isNull();

        // check if we link properly the neighbour elements
        assertThat(writableAirdropStore.get(firstPendingAirdropId).previousAirdrop())
                .isEqualTo(thirdPendingAirdropId);
        assertThat(writableAirdropStore.get(firstPendingAirdropId).nextAirdrop())
                .isNull();
        assertThat(writableAirdropStore.get(thirdPendingAirdropId).previousAirdrop())
                .isNull();
        assertThat(writableAirdropStore.get(thirdPendingAirdropId).nextAirdrop())
                .isEqualTo(firstPendingAirdropId);

        // check if we have new association
        assertThat(writableTokenRelStore.get(tokenReceiverNoAssociationId, fungibleTokenIDB))
                .isNotNull();
        assertThat(writableTokenRelStore.get(tokenReceiverNoAssociationId, fungibleTokenIDD))
                .isNotNull();

        // check if we have the proper transfer
        assertThat(writableTokenRelStore
                        .get(tokenReceiverNoAssociationId, fungibleTokenIDB)
                        .balance())
                .isEqualTo(10);
        assertThat(writableTokenRelStore
                        .get(tokenReceiverNoAssociationId, fungibleTokenIDD)
                        .balance())
                .isEqualTo(10);

        // check sender's pending airdrops head id and count
        assertThat(writableAccountStore.get(spenderId).headPendingAirdropId()).isEqualTo(thirdPendingAirdropId);
        assertThat(writableAccountStore.get(spenderId).numberPendingAirdrops()).isEqualTo(2);
    }

    private PendingAirdropId setUpFourthPendingAirdrop() {
        var fourthPendingAirdrop = thirdPendingAirdropId
                .copyBuilder()
                .fungibleTokenType(fungibleTokenIDD)
                .build();
        givenPendingFungibleTokenAirdrop(fungibleTokenIDD, spenderId, tokenReceiverNoAssociationId, 10L);
        // rebuild stores with updated tokens changes
        refreshReadableStores();
        refreshWritableStores();
        // mock stores
        givenStoresAndConfig(handleContext);
        // associate spender with the tokens
        associateToken(spenderId, fungibleTokenId);
        associateToken(spenderId, fungibleTokenIDB);
        associateToken(spenderId, fungibleTokenIDC);
        associateToken(spenderId, fungibleTokenIDD);

        return fourthPendingAirdrop;
    }

    private void associateToken(AccountID accountId, TokenID tokenId) {
        writableTokenRelStore.put(TokenRelation.newBuilder()
                .tokenId(tokenId)
                .accountId(accountId)
                .balance(50)
                .kycGranted(true)
                .build());
    }
}
