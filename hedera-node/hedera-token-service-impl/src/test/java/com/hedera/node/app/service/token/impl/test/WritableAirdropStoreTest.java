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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.PendingAirdropId;
import com.hedera.hapi.node.base.PendingAirdropValue;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.node.app.service.token.impl.WritableAirdropStore;
import com.hedera.node.app.service.token.impl.test.handlers.util.StateBuilderUtil;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.test.fixtures.state.MapWritableKVState;
import com.swirlds.state.spi.WritableStates;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WritableAirdropStoreTest extends StateBuilderUtil {

    private final Configuration configuration = HederaTestConfigBuilder.createConfig();

    @Mock
    private WritableStates writableStates;

    @Mock
    private StoreMetricsService storeMetricsService;

    private MapWritableKVState<PendingAirdropId, PendingAirdropValue> writableAirdropState;

    private WritableAirdropStore subject;

    @BeforeEach
    public void setUp() {
        writableAirdropState = emptyWritableAirdropStateBuilder().build();
        given(writableStates.<PendingAirdropId, PendingAirdropValue>get(AIRDROPS))
                .willReturn(writableAirdropState);
        subject = new WritableAirdropStore(writableStates, configuration, storeMetricsService);
    }

    @Test
    void putsAirdropsToState() {
        final var airdropId = getFungibleAirdrop();
        final var airdropValue = PendingAirdropValue.newBuilder().amount(20).build();

        assertThat(writableAirdropState.contains(airdropId)).isFalse();

        subject.put(airdropId, airdropValue);

        assertThat(writableAirdropState.contains(airdropId)).isTrue();
        final var tokenValue = writableAirdropState.get(airdropId);
        assertThat(airdropValue).isEqualTo(tokenValue);
    }

    @Test
    void putsUpdatesExistingAirdrop() {
        final var airdropId = getFungibleAirdrop();
        final var airdropValue = PendingAirdropValue.newBuilder().amount(30).build();
        writableAirdropState = emptyWritableAirdropStateBuilder()
                .value(airdropId, airdropValue)
                .build();
        given(writableStates.<PendingAirdropId, PendingAirdropValue>get(AIRDROPS))
                .willReturn(writableAirdropState);
        subject = new WritableAirdropStore(writableStates, configuration, storeMetricsService);

        final var newAirdropValue = PendingAirdropValue.newBuilder().amount(20).build();

        assertThat(writableAirdropState.contains(airdropId)).isTrue();

        subject.put(airdropId, newAirdropValue);

        assertThat(writableAirdropState.contains(airdropId)).isTrue();
        final var tokenValue =
                Objects.requireNonNull(writableAirdropState.get(airdropId)).amount();
        assertThat(tokenValue).isEqualTo(airdropValue.amount() + newAirdropValue.amount());
    }

    @Test
    void putsDoesNotUpdateNftIfExists() {
        final var nftId = getNonFungibleAirDrop();
        final var nftValue = PendingAirdropValue.newBuilder().build();
        var stateSpy = Mockito.spy(
                writableAirdropState = emptyWritableAirdropStateBuilder()
                        .value(nftId, nftValue)
                        .build());
        given(writableStates.<PendingAirdropId, PendingAirdropValue>get(AIRDROPS))
                .willReturn(writableAirdropState);
        subject = new WritableAirdropStore(writableStates, configuration, storeMetricsService);

        assertThat(writableAirdropState.contains(nftId)).isTrue();

        subject.put(nftId, nftValue);

        assertThat(writableAirdropState.contains(nftId)).isTrue();
        final var tokenValue =
                Objects.requireNonNull(writableAirdropState.get(nftId)).amount();
        assertThat(tokenValue).isEqualTo(0L);
        verify(stateSpy, times(0)).put(any(), any());
    }

    @Test
    void removesAirdropById() {
        final var fungibleAirdropToRemove = getFungibleAirdrop();
        final var fungibleAirdropValue = airdropWithValue(15);
        final var nftToRemove = getNonFungibleAirDrop();
        writableAirdropState = emptyWritableAirdropStateBuilder()
                .value(fungibleAirdropToRemove, fungibleAirdropValue)
                .value(nftToRemove, emptyAirdropValue())
                .build();

        assertThat(writableAirdropState.contains(fungibleAirdropToRemove)).isTrue();
        assertThat(writableAirdropState.contains(nftToRemove)).isTrue();

        given(writableStates.<PendingAirdropId, PendingAirdropValue>get(AIRDROPS))
                .willReturn(writableAirdropState);
        subject = new WritableAirdropStore(writableStates, configuration, storeMetricsService);

        assertThat(subject.exists(fungibleAirdropToRemove)).isTrue();
        assertThat(subject.exists(nftToRemove)).isTrue();
        subject.remove(fungibleAirdropToRemove);
        subject.remove(nftToRemove);
        assertThat(subject.exists(fungibleAirdropToRemove)).isFalse();
        assertThat(subject.exists(nftToRemove)).isFalse();
    }

    @Test
    void getForModifyReturnsImmutableAirDrop() {
        final var airdropId = getFungibleAirdrop();
        final var airdropValue = airdropWithValue(255);
        final var nftAirdropId = getNonFungibleAirDrop();
        final var nftValue = emptyAirdropValue();

        subject.put(airdropId, airdropValue);
        subject.put(nftAirdropId, nftValue);

        final var readAirdrop = subject.getForModify(airdropId);
        assertThat(readAirdrop).isNotNull();
        assertThat(airdropValue).isEqualTo(readAirdrop);

        final var readNft = subject.getForModify(nftAirdropId);
        assertThat(nftAirdropId).isNotNull();
        assertThat(readNft).isNull();
    }

    @Test
    void getForModifyNonExisting() {
        final var nonExistingAirdropId = getFungibleAirdrop();
        final var nonExistingNftAirdropId = getNonFungibleAirDrop();

        assertThat(subject.exists(nonExistingAirdropId)).isFalse();
        assertThat(subject.exists(nonExistingNftAirdropId)).isFalse();

        final var readAirdrop = subject.getForModify(nonExistingAirdropId);
        final var readNft = subject.getForModify(nonExistingNftAirdropId);

        assertThat(readAirdrop).isNull();
        assertThat(readNft).isNull();
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testConstructorCallWithNull() {
        assertThatThrownBy(() -> subject = new WritableAirdropStore(null, configuration, storeMetricsService))
                .isInstanceOf(NullPointerException.class);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testGetWithNullParam() {
        assertThatThrownBy(() -> subject.getForModify(null)).isInstanceOf(NullPointerException.class);
    }

    private PendingAirdropId getNonFungibleAirDrop() {
        return PendingAirdropId.newBuilder()
                .nonFungibleToken(NftID.newBuilder()
                        .serialNumber(123456)
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

    private PendingAirdropValue emptyAirdropValue() {
        return PendingAirdropValue.newBuilder().build();
    }
}
