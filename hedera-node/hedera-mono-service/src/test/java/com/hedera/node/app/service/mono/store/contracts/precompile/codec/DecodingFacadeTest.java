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
package com.hedera.node.app.service.mono.store.contracts.precompile.codec;

import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Tuple;
import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.TransferPrecompile;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DecodingFacadeTest {
    private static final int ADDRESS_SKIP_BYTES_LENGTH = 12;
    private final Address address =
            Address.wrap(Address.toChecksumAddress("0xabababababababababababababababababababab"));
    private final byte[] addressBytes;
    private final byte[] mirrorAddressBytes = new byte[20];
    private final byte[] leftPaddedAddress = new byte[32];

    {
        addressBytes = Arrays.copyOfRange(address.value().toByteArray(), 1, 21);
        System.arraycopy(addressBytes, 0, leftPaddedAddress, ADDRESS_SKIP_BYTES_LENGTH, 20);
        System.arraycopy(Longs.toByteArray(Long.MAX_VALUE), 0, mirrorAddressBytes, 12, 8);
    }

    @Mock private UnaryOperator<byte[]> aliasResolver;
    @Mock private Predicate<AccountID> exists;

    @Test
    void nonExistentMirrorAddressStillResultsInHollowCreationAttempt() {
        given(aliasResolver.apply(any())).willReturn(mirrorAddressBytes);
        final var convertedId =
                convertLeftPaddedAddressToAccountId(leftPaddedAddress, aliasResolver, exists);
        assertTrue(convertedId.hasAlias());
        assertArrayEquals(mirrorAddressBytes, convertedId.getAlias().toByteArray());
    }

    @Test
    void fungibleTransfersDontRegenerateAliasIfAlreadyPresent() {
        final var tuples = new Tuple[] {Tuple.of(leftPaddedAddress, 1L)};
        final var tokenType = TokenID.newBuilder().setTokenNum(1234).build();
        given(aliasResolver.apply(any())).willReturn(addressBytes);

        final var transfers =
                DecodingFacade.bindFungibleTransfersFrom(tokenType, tuples, aliasResolver, exists);

        final var transfer = transfers.get(0);
        final var receiver = transfer.receiver();
        assertTrue(receiver.hasAlias());
        assertArrayEquals(addressBytes, receiver.getAlias().toByteArray());
    }

    @Test
    void addingSignedAdjustmentsDoesntRegenerateAliasIfAlreadyPresent() {
        final List<SyntheticTxnFactory.FungibleTokenTransfer> fungibleTransfers = new ArrayList<>();
        final var tokenType = TokenID.newBuilder().setTokenNum(1234).build();
        final List<AccountID> accountIds = new ArrayList<>();
        accountIds.add(AccountID.newBuilder().setAlias(ByteString.copyFrom(addressBytes)).build());
        final long[] amounts = new long[] {1L};

        TransferPrecompile.addSignedAdjustments(
                fungibleTransfers, accountIds, exists, tokenType, amounts);

        final var transfer = fungibleTransfers.get(0);
        final var receiver = transfer.receiver();
        assertTrue(receiver.hasAlias());
        assertArrayEquals(addressBytes, receiver.getAlias().toByteArray());
    }

    @Test
    void addingNftExchangesDoesntRegenerateAliasIfAlreadyPresent() {
        final List<SyntheticTxnFactory.NftExchange> nftExchanges = new ArrayList<>();
        final var tokenType = TokenID.newBuilder().setTokenNum(1234).build();
        final List<AccountID> senders = new ArrayList<>();
        final List<AccountID> receivers = new ArrayList<>();
        senders.add(AccountID.newBuilder().setAccountNum(12345).build());
        receivers.add(AccountID.newBuilder().setAlias(ByteString.copyFrom(addressBytes)).build());
        final long[] serialNos = new long[] {1L};

        TransferPrecompile.addNftExchanges(
                nftExchanges, senders, receivers, serialNos, tokenType, exists);

        final var exchange = nftExchanges.get(0);
        final var receiver = exchange.asGrpc().getReceiverAccountID();
        assertTrue(receiver.hasAlias());
        assertArrayEquals(addressBytes, receiver.getAlias().toByteArray());
    }

    @Test
    void nonExtantAccountWithAliasAlreadyIsHollowCreationAttempt() {
        given(aliasResolver.apply(any())).willReturn(addressBytes);
        final var convertedId =
                convertLeftPaddedAddressToAccountId(leftPaddedAddress, aliasResolver, exists);
        assertTrue(convertedId.hasAlias());
        assertArrayEquals(addressBytes, convertedId.getAlias().toByteArray());
    }

    @Test
    void nonMirrorAddressResultsInAliasedAccountId() {
        given(aliasResolver.apply(any())).willReturn(addressBytes);
        final var convertedId =
                convertLeftPaddedAddressToAccountId(leftPaddedAddress, aliasResolver);
        assertTrue(convertedId.hasAlias());
        assertArrayEquals(addressBytes, convertedId.getAlias().toByteArray());
    }

    @Test
    void mirrorAddressResultsInExpectedId() {
        given(aliasResolver.apply(any())).willReturn(mirrorAddressBytes);
        final var expectedId = AccountID.newBuilder().setAccountNum(Long.MAX_VALUE).build();
        final var convertedId =
                convertLeftPaddedAddressToAccountId(leftPaddedAddress, aliasResolver);
        assertEquals(expectedId, convertedId);
    }
}
