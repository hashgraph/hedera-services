/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.state.common.UniqueTokenId;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.node.app.service.token.impl.WritableNftStore;
import com.hedera.node.app.service.token.impl.handlers.BaseTokenHandler;
import com.hedera.node.app.service.token.impl.test.handlers.util.CryptoTokenHandlerTestBase;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WritableNftStoreTest extends CryptoTokenHandlerTestBase {

    @BeforeEach
    public void setUp() {
        writableNftState = emptyWritableNftStateBuilder().build();
        given(writableStates.<UniqueTokenId, Nft>get(NFTS)).willReturn(writableNftState);
        writableNftStore = new WritableNftStore(writableStates);
    }

    @Test
    void throwsIfNullValuesAsArgs() {
        assertThrows(NullPointerException.class, () -> new WritableNftStore(null));
        assertThrows(NullPointerException.class, () -> writableNftStore.put(null));
        assertThrows(NullPointerException.class, () -> writableNftStore.get(null));
    }

    @Test
    void constructorCreatesTokenState() {
        final var store = new WritableNftStore(writableStates);
        assertNotNull(store);
    }

    @Test
    void getReturnsImmutableToken() {
        final var id =
                UniqueTokenId.newBuilder().tokenTypeNumber(1).serialNumber(1).build();
        final var nft = givenNft(id);
        writableNftStore.put(nft);

        final var readToken = writableNftStore.get(id);

        assertEquals(nft, readToken);
    }

    @Test
    void getForModifyReturnsImmutableToken() {
        final var id =
                UniqueTokenId.newBuilder().tokenTypeNumber(1).serialNumber(1).build();
        final var nft = givenNft(id);

        writableNftStore.put(nft);

        final var readToken = writableNftStore.getForModify(id);

        assertThat(readToken).isNotNull();
        assertEquals(nft, readToken);
    }

    @Test
    void putsTokenChangesToStateInModifications() {
        final var id =
                UniqueTokenId.newBuilder().tokenTypeNumber(1).serialNumber(1).build();
        final var nft = givenNft(id);

        assertFalse(writableNftState.contains(id));

        // put, keeps the token in the modifications
        writableNftStore.put(nft);

        assertTrue(writableNftState.contains(id));
        final var writtenToken = writableNftState.get(id);
        assertEquals(nft, writtenToken);
    }

    @Test
    void getsSizeOfState() {
        final var id =
                UniqueTokenId.newBuilder().tokenTypeNumber(1).serialNumber(1).build();
        final var nft = givenNft(id);

        assertEquals(0, writableNftStore.sizeOfState());
        assertEquals(Collections.EMPTY_SET, writableNftStore.modifiedNfts());
        writableNftStore.put(nft);

        assertEquals(1, writableNftStore.sizeOfState());
        assertEquals(Set.of(id), writableNftStore.modifiedNfts());
    }

    @Test
    void removesByUniqueTokenId() {
        // Set up the NFT state with an existing NFT
        final var nftToRemove =
                UniqueTokenId.newBuilder().tokenTypeNumber(1).serialNumber(1).build();
        writableNftState = emptyWritableNftStateBuilder()
                .value(
                        nftToRemove,
                        Nft.newBuilder().id(nftToRemove).ownerNumber(12345).build())
                .build();
        assertTrue(writableNftState.contains(nftToRemove));
        given(writableStates.<UniqueTokenId, Nft>get(NFTS)).willReturn(writableNftState);
        writableNftStore = new WritableNftStore(writableStates);
        assertNotNull(writableNftStore.get(nftToRemove));

        writableNftStore.remove(nftToRemove);

        // Assert the NFT is removed
        assertNull(writableNftStore.get(nftToRemove));
    }

    @Test
    void removesByTokenIdAndSerialNum() {
        // Set up the NFT state with an existing NFT
        final var nftToRemove =
                UniqueTokenId.newBuilder().tokenTypeNumber(1).serialNumber(1).build();
        writableNftState = emptyWritableNftStateBuilder()
                .value(
                        nftToRemove,
                        Nft.newBuilder().id(nftToRemove).ownerNumber(12345).build())
                .build();
        assertTrue(writableNftState.contains(nftToRemove));
        given(writableStates.<UniqueTokenId, Nft>get(NFTS)).willReturn(writableNftState);
        writableNftStore = new WritableNftStore(writableStates);
        assertNotNull(writableNftStore.get(nftToRemove));

        writableNftStore.remove(BaseTokenHandler.asToken(nftToRemove.tokenTypeNumber()), nftToRemove.serialNumber());

        // Assert the NFT is removed
        assertNull(writableNftStore.get(nftToRemove));
    }
}
