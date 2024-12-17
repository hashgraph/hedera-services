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
import com.hedera.hapi.node.state.token.AccountPendingAirdrop;
import com.hedera.node.app.service.token.impl.ReadableAirdropStoreImpl;
import com.hedera.node.app.service.token.impl.test.handlers.util.StateBuilderUtil;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableStates;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReadableAirdropStoreImplTest extends StateBuilderUtil {

    @Mock
    private ReadableStates readableStates;

    private ReadableKVState<PendingAirdropId, AccountPendingAirdrop> airdrops;

    private ReadableAirdropStoreImpl subject;

    @BeforeEach
    public void setUp() {
        given(readableStates.<PendingAirdropId, AccountPendingAirdrop>get(AIRDROPS))
                .willReturn(airdrops);
        subject = new ReadableAirdropStoreImpl(readableStates);
    }

    @Test
    void getsValueIfAirdropContainsFungibleToken() {
        var fungibleAirdrop = getFungibleAirdrop();
        var airdropValue = airdropWithValue(5);
        var accountAirdrop = accountAirdropWith(airdropValue);

        airdrops = emptyReadableAirdropStateBuilder()
                .value(fungibleAirdrop, accountAirdrop)
                .build();
        given(readableStates.<PendingAirdropId, AccountPendingAirdrop>get(AIRDROPS))
                .willReturn(airdrops);
        subject = new ReadableAirdropStoreImpl(readableStates);

        assertThat(subject.get(fungibleAirdrop)).isNotNull();
        assertThat(Objects.requireNonNull(subject.get(fungibleAirdrop)).pendingAirdropValue())
                .isEqualTo(airdropValue);
    }

    @Test
    void getsFungibleThatDoesNotExist() {
        var fungibleAirdrop = getFungibleAirdrop();

        airdrops = emptyReadableAirdropStateBuilder().build();
        given(readableStates.<PendingAirdropId, AccountPendingAirdrop>get(AIRDROPS))
                .willReturn(airdrops);
        subject = new ReadableAirdropStoreImpl(readableStates);

        assertThat(subject.get(fungibleAirdrop)).isNull();
        assertThat(subject.get(fungibleAirdrop)).isNull();
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void getsFungibleWithNullParam() {
        assertThatThrownBy(() -> subject.get(null)).isInstanceOf(NullPointerException.class);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testConstructorCallWithNull() {
        assertThatThrownBy(() -> subject = new ReadableAirdropStoreImpl(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testSizeOfState() {
        airdrops = emptyReadableAirdropStateBuilder()
                .value(getNonFungibleAirDrop(), accountAirdropWith(null))
                .build();
        given(readableStates.<PendingAirdropId, AccountPendingAirdrop>get(AIRDROPS))
                .willReturn(airdrops);
        subject = new ReadableAirdropStoreImpl(readableStates);
        assertThat(readableStates.get(StateBuilderUtil.AIRDROPS).size()).isEqualTo(subject.sizeOfState());
    }

    @Test
    void testExists() {
        var fungibleAirdrop = getFungibleAirdrop();
        var fungibleValue = airdropWithValue(10);
        var accountAirdrop = accountAirdropWith(fungibleValue);

        airdrops = emptyReadableAirdropStateBuilder()
                .value(fungibleAirdrop, accountAirdrop)
                .build();
        given(readableStates.<PendingAirdropId, AccountPendingAirdrop>get(AIRDROPS))
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
        given(readableStates.<PendingAirdropId, AccountPendingAirdrop>get(AIRDROPS))
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

    private PendingAirdropValue airdropWithValue(long value) {
        return PendingAirdropValue.newBuilder().amount(value).build();
    }

    private AccountPendingAirdrop accountAirdropWith(PendingAirdropValue pendingAirdropValue) {
        return AccountPendingAirdrop.newBuilder()
                .pendingAirdropValue(pendingAirdropValue)
                .build();
    }
}
