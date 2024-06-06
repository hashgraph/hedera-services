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

package com.hedera.node.app.service.token.impl.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.PendingAirdropId;
import com.hedera.hapi.node.base.PendingAirdropValue;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.node.app.service.token.impl.ReadableAirdropStoreImpl;
import com.hedera.node.app.service.token.impl.test.handlers.util.StateBuilderUtil;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableStates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReadableAirdropStoreImplTest extends StateBuilderUtil {

    @Mock
    private ReadableStates readableStates;

    private ReadableKVState<PendingAirdropId, PendingAirdropValue> airdrops;

    private ReadableAirdropStoreImpl subject;

    @BeforeEach
    public void setUp() {
        given(readableStates.<PendingAirdropId, PendingAirdropValue>get(AIRDROPS))
                .willReturn(airdrops);
        subject = new ReadableAirdropStoreImpl(readableStates);
    }

    @Test
    void getsNullIfAirdropContainsOnlyNFT() {
        var nftAirdrop = getNonFungibleAirDrop();
        assertThat(subject.getFungibleAirdropAmount(nftAirdrop)).isNull();
    }

    @Test
    void getsValueIfAirdropContainsFungibleToken() {
        var fungibleAirdrop = getFungibleAirdrop();
        var airdropValue = PendingAirdropValue.newBuilder().amount(5).build();

        airdrops = emptyReadableAirdropStateBuilder()
                .value(fungibleAirdrop, airdropValue)
                .build();
        given(readableStates.<PendingAirdropId, PendingAirdropValue>get(AIRDROPS))
                .willReturn(airdrops);
        subject = new ReadableAirdropStoreImpl(readableStates);

        assertThat(subject.getFungibleAirdropAmount(fungibleAirdrop)).isNotNull();
        assertThat(subject.getFungibleAirdropAmount(fungibleAirdrop)).isEqualTo(airdropValue);
    }

    @Test
    void getsFungibleThatDoesNotExist() {
        var fungibleAirdrop = getFungibleAirdrop();

        airdrops = emptyReadableAirdropStateBuilder().build();
        given(readableStates.<PendingAirdropId, PendingAirdropValue>get(AIRDROPS))
                .willReturn(airdrops);
        subject = new ReadableAirdropStoreImpl(readableStates);

        assertThat(subject.getFungibleAirdropAmount(fungibleAirdrop)).isNull();
        assertThat(subject.getFungibleAirdropAmount(fungibleAirdrop)).isNull();
    }

    @Test
    void getsFungibleWithNullParam() {
        assertThatThrownBy(() -> subject.getFungibleAirdropAmount(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testConstructorCallWithNull() {
        assertThatThrownBy(() -> subject = new ReadableAirdropStoreImpl(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testSizeOfState() {
        airdrops = emptyReadableAirdropStateBuilder()
                .value(getNonFungibleAirDrop(), PendingAirdropValue.newBuilder().build())
                .build();
        given(readableStates.<PendingAirdropId, PendingAirdropValue>get(AIRDROPS))
                .willReturn(airdrops);
        subject = new ReadableAirdropStoreImpl(readableStates);
        assertThat(readableStates.get(StateBuilderUtil.AIRDROPS).size()).isEqualTo(subject.sizeOfState());
    }

    @Test
    void testExists() {
        var fungibleAirdrop = getFungibleAirdrop();

        airdrops = emptyReadableAirdropStateBuilder()
                .value(
                        fungibleAirdrop,
                        PendingAirdropValue.newBuilder().amount(5).build())
                .build();
        given(readableStates.<PendingAirdropId, PendingAirdropValue>get(AIRDROPS))
                .willReturn(airdrops);
        subject = new ReadableAirdropStoreImpl(readableStates);

        final var store = new ReadableAirdropStoreImpl(readableStates);
        assertThat(readableStates.get(StateBuilderUtil.AIRDROPS).contains(fungibleAirdrop))
                .isEqualTo(store.exists(fungibleAirdrop));
    }

    @Test
    void testDoesNotExists() {
        var fungibleAirdrop = getFungibleAirdrop();

        airdrops = emptyReadableAirdropStateBuilder().build();
        given(readableStates.<PendingAirdropId, PendingAirdropValue>get(AIRDROPS))
                .willReturn(airdrops);
        subject = new ReadableAirdropStoreImpl(readableStates);

        final var store = new ReadableAirdropStoreImpl(readableStates);
        assertThat(readableStates.get(StateBuilderUtil.AIRDROPS).contains(fungibleAirdrop))
                .isEqualTo(store.exists(fungibleAirdrop));
    }

    private PendingAirdropId getNonFungibleAirDrop() {
        return PendingAirdropId.newBuilder()
                .nonFungibleToken(NftID.newBuilder()
                        .serialNumber(123_456)
                        .tokenId(TokenID.newBuilder()
                                .tokenNum(1)
                                .shardNum(2)
                                .realmNum(3)
                                .build())
                        .build())
                .build();
    }

    private PendingAirdropId getFungibleAirdrop() {
        return PendingAirdropId.newBuilder()
                .fungibleTokenType(
                        TokenID.newBuilder().realmNum(1).shardNum(2).tokenNum(3).build())
                .build();
    }
}
