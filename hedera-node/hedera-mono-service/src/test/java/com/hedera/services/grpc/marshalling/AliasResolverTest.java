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

import static com.hedera.services.utils.EntityNum.MISSING_NUM;
import static com.hedera.test.utils.IdUtils.asAliasAccount;
import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransferList;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AliasResolverTest {
    @Mock private AliasManager aliasManager;

    private AliasResolver subject;

    @BeforeEach
    void setup() {
        subject = new AliasResolver();
    }

    @Test
    void transformsTokenAdjusts() {
        final var unresolved = aaId(bNum.longValue(), theAmount);
        final var op =
                CryptoTransferTransactionBody.newBuilder()
                        .addTokenTransfers(
                                TokenTransferList.newBuilder()
                                        .setToken(someToken)
                                        .addTransfers(aaAlias(anAlias, anAmount))
                                        .addTransfers(unresolved)
                                        .addTransfers(aaAlias(someAlias, -anAmount))
                                        .addNftTransfers(
                                                NftTransfer.newBuilder()
                                                        .setSenderAccountID(
                                                                AccountID.newBuilder()
                                                                        .setAlias(anAlias))
                                                        .setReceiverAccountID(
                                                                bNum.toGrpcAccountId())
                                                        .setSerialNumber(1L)
                                                        .build())
                                        .addNftTransfers(
                                                NftTransfer.newBuilder()
                                                        .setSenderAccountID(bNum.toGrpcAccountId())
                                                        .setReceiverAccountID(
                                                                AccountID.newBuilder()
                                                                        .setAlias(otherAlias))
                                                        .setSerialNumber(2L)
                                                        .build())
                                        .addNftTransfers(
                                                NftTransfer.newBuilder()
                                                        .setSenderAccountID(
                                                                AccountID.newBuilder()
                                                                        .setAlias(anAlias))
                                                        .setReceiverAccountID(
                                                                AccountID.newBuilder()
                                                                        .setAlias(someAlias))
                                                        .setSerialNumber(2L)
                                                        .build())
                                        .addNftTransfers(
                                                NftTransfer.newBuilder()
                                                        .setSenderAccountID(
                                                                AccountID.newBuilder()
                                                                        .setAlias(anAlias))
                                                        .setReceiverAccountID(
                                                                AccountID.newBuilder()
                                                                        .setAlias(
                                                                                anotherValidAlias))
                                                        .setSerialNumber(2L)
                                                        .build()))
                        .build();
        assertTrue(AliasResolver.usesAliases(op));

        given(aliasManager.lookupIdBy(anAlias)).willReturn(aNum);
        given(aliasManager.lookupIdBy(someAlias)).willReturn(MISSING_NUM);
        given(aliasManager.lookupIdBy(otherAlias)).willReturn(MISSING_NUM);
        given(aliasManager.lookupIdBy(anotherValidAlias)).willReturn(MISSING_NUM);

        final var resolvedOp = subject.resolve(op, aliasManager);

        assertEquals(1, subject.perceivedMissingAliases());
        assertEquals(1, subject.perceivedInvalidCreations());
        assertEquals(1, subject.perceivedAutoCreations());
        assertEquals(
                Map.of(
                        anAlias,
                        aNum,
                        someAlias,
                        MISSING_NUM,
                        otherAlias,
                        MISSING_NUM,
                        anotherValidAlias,
                        MISSING_NUM),
                subject.resolutions());
        final var tokensAdjusts = resolvedOp.getTokenTransfers(0);
        assertEquals(someToken, tokensAdjusts.getToken());
        assertEquals(aNum.toGrpcAccountId(), tokensAdjusts.getTransfers(0).getAccountID());
        assertEquals(unresolved, tokensAdjusts.getTransfers(1));
        final var ownershipChange = tokensAdjusts.getNftTransfers(0);
        assertEquals(aNum.toGrpcAccountId(), ownershipChange.getSenderAccountID());
        assertEquals(bNum.toGrpcAccountId(), ownershipChange.getReceiverAccountID());
        assertEquals(1L, ownershipChange.getSerialNumber());
        assertEquals(Map.of(anAlias, aNum, someAlias, MISSING_NUM), subject.tokenResolutions());
    }

    @Test
    void resolvesMirrorAddressInHbarList() {
        final var mirrorAdjust = aaAlias(mirrorAlias, +100);
        final var op =
                CryptoTransferTransactionBody.newBuilder()
                        .setTransfers(
                                TransferList.newBuilder().addAccountAmounts(mirrorAdjust).build())
                        .build();
        assertTrue(AliasResolver.usesAliases(op));
        given(aliasManager.isMirror(evmAddress)).willReturn(true);

        final var resolvedOp = subject.resolve(op, aliasManager);
        assertEquals(
                mirrorNum.toGrpcAccountId(),
                resolvedOp.getTransfers().getAccountAmounts(0).getAccountID());
    }

    @Test
    void resolvesMirrorAddressInNftTransfer() {
        final var op =
                CryptoTransferTransactionBody.newBuilder()
                        .addTokenTransfers(
                                TokenTransferList.newBuilder()
                                        .setToken(someToken)
                                        .addNftTransfers(
                                                NftTransfer.newBuilder()
                                                        .setSenderAccountID(
                                                                AccountID.newBuilder()
                                                                        .setAlias(mirrorAlias))
                                                        .setReceiverAccountID(
                                                                bNum.toGrpcAccountId())
                                                        .setSerialNumber(1L)))
                        .build();
        assertTrue(AliasResolver.usesAliases(op));
        given(aliasManager.isMirror(evmAddress)).willReturn(true);

        final var resolvedOp = subject.resolve(op, aliasManager);
        assertEquals(
                mirrorNum.toGrpcAccountId(),
                resolvedOp.getTokenTransfers(0).getNftTransfers(0).getSenderAccountID());
    }

    @Test
    void resolvesAliasAddressInNftTransfer() {
        final var op =
                CryptoTransferTransactionBody.newBuilder()
                        .addTokenTransfers(
                                TokenTransferList.newBuilder()
                                        .setToken(someToken)
                                        .addNftTransfers(
                                                NftTransfer.newBuilder()
                                                        .setSenderAccountID(bNum.toGrpcAccountId())
                                                        .setReceiverAccountID(
                                                                AccountID.newBuilder()
                                                                        .setAlias(create2Alias))
                                                        .setSerialNumber(1L)))
                        .build();
        assertTrue(AliasResolver.usesAliases(op));
        given(aliasManager.lookupIdBy(create2Alias)).willReturn(aNum);

        final var resolvedOp = subject.resolve(op, aliasManager);
        assertEquals(
                aNum.toGrpcAccountId(),
                resolvedOp.getTokenTransfers(0).getNftTransfers(0).getReceiverAccountID());
    }

    @Test
    void resolvesAliasAddressInHbarList() {
        final var create2Adjust = aaAlias(create2Alias, +100);
        final var op =
                CryptoTransferTransactionBody.newBuilder()
                        .setTransfers(
                                TransferList.newBuilder().addAccountAmounts(create2Adjust).build())
                        .build();

        given(aliasManager.lookupIdBy(create2Alias)).willReturn(MISSING_NUM);
        subject.resolve(op, aliasManager);
        assertEquals(1, subject.perceivedAutoCreations());
    }

    @Test
    void transformsHbarAdjusts() {
        final var creationAdjust = aaAlias(anAlias, theAmount);
        final var badCreationAdjust = aaAlias(someAlias, theAmount);
        final var op =
                CryptoTransferTransactionBody.newBuilder()
                        .setTransfers(
                                TransferList.newBuilder()
                                        .addAccountAmounts(creationAdjust)
                                        .addAccountAmounts(aaAlias(theAlias, anAmount))
                                        .addAccountAmounts(aaAlias(someAlias, someAmount))
                                        .addAccountAmounts(badCreationAdjust)
                                        .build())
                        .build();
        assertTrue(AliasResolver.usesAliases(op));

        given(aliasManager.lookupIdBy(theAlias)).willReturn(aNum);
        given(aliasManager.lookupIdBy(anAlias)).willReturn(MISSING_NUM);
        given(aliasManager.lookupIdBy(someAlias)).willReturn(MISSING_NUM);

        final var resolvedOp = subject.resolve(op, aliasManager);

        assertEquals(1, subject.perceivedAutoCreations());
        assertEquals(1, subject.perceivedInvalidCreations());
        assertEquals(1, subject.perceivedMissingAliases());
        assertEquals(
                Map.of(anAlias, MISSING_NUM, theAlias, aNum, someAlias, MISSING_NUM),
                subject.resolutions());
        assertEquals(
                creationAdjust.getAccountID(),
                resolvedOp.getTransfers().getAccountAmounts(0).getAccountID());
        assertEquals(
                aNum.toGrpcAccountId(),
                resolvedOp.getTransfers().getAccountAmounts(1).getAccountID());
    }

    @Test
    void noAliasesCanBeReturned() {
        assertFalse(AliasResolver.usesAliases(CryptoTransferTransactionBody.getDefaultInstance()));
    }

    @Test
    void allowsAliasesInTokens() {
        var op =
                CryptoTransferTransactionBody.newBuilder()
                        .addTokenTransfers(
                                TokenTransferList.newBuilder()
                                        .setToken(someToken)
                                        .addNftTransfers(
                                                NftTransfer.newBuilder()
                                                        .setSenderAccountID(
                                                                asAliasAccount(someAlias))
                                                        .setReceiverAccountID(
                                                                asAliasAccount(create2Alias))
                                                        .setSerialNumber(1L)))
                        .build();
        assertTrue(AliasResolver.usesAliases(op));

        op =
                CryptoTransferTransactionBody.newBuilder()
                        .addTokenTransfers(
                                TokenTransferList.newBuilder()
                                        .setToken(someToken)
                                        .addTransfers(aaAlias(someAlias, 10L))
                                        .addTransfers(aaAlias(anotherValidAlias, -10L))
                                        .build())
                        .build();
        assertTrue(AliasResolver.usesAliases(op));
    }

    @Test
    void doesntAllowRepeatedAliasesInSingleTokenTransferList() {
        final var op =
                CryptoTransferTransactionBody.newBuilder()
                        .addTokenTransfers(
                                TokenTransferList.newBuilder()
                                        .setToken(someToken)
                                        .addTransfers(aaAlias(anAlias, 10L))
                                        .addTransfers(aaAlias(anotherValidAlias, -10L))
                                        .addTransfers(aaAlias(anAlias, 20L))
                                        .addTransfers(aaAlias(anotherValidAlias, -20L))
                                        .build())
                        .build();
        given(aliasManager.lookupIdBy(anotherValidAlias)).willReturn(aNum);
        given(aliasManager.lookupIdBy(anAlias)).willReturn(MISSING_NUM);
        final var body = subject.resolve(op, aliasManager);

        assertEquals(1, subject.perceivedInvalidCreations());
        assertTrue(AliasResolver.usesAliases(body));
    }

    private AccountAmount aaAlias(final ByteString alias, final long amount) {
        return AccountAmount.newBuilder()
                .setAmount(amount)
                .setAccountID(AccountID.newBuilder().setAlias(alias).build())
                .build();
    }

    private AccountAmount aaId(final long num, final long amount) {
        return AccountAmount.newBuilder()
                .setAmount(amount)
                .setAccountID(AccountID.newBuilder().setAccountNum(num).build())
                .build();
    }

    private static final Key aPrimitiveKey =
            Key.newBuilder()
                    .setEd25519(ByteString.copyFromUtf8("01234567890123456789012345678901"))
                    .build();
    private static final Key anotherPrimitiveKey =
            Key.newBuilder()
                    .setEd25519(ByteString.copyFromUtf8("01234567890123456789012345678911"))
                    .build();
    private static final long anAmount = 1234;
    private static final long theAmount = 12345;
    private static final long someAmount = -1234;
    private static final byte[] evmAddress = unhex("0000000000000000000000000000000000defbbb");
    private static final byte[] create2Address = unhex("0111111111111111111111111111111111defbbb");
    private static final EntityNum aNum = EntityNum.fromLong(4321);
    private static final EntityNum bNum = EntityNum.fromLong(5432);
    private static final EntityNum mirrorNum =
            EntityNum.fromLong(Longs.fromByteArray(Arrays.copyOfRange(evmAddress, 12, 20)));
    private static final ByteString anAlias = aPrimitiveKey.toByteString();
    private static final ByteString anotherValidAlias = anotherPrimitiveKey.toByteString();
    private static final ByteString theAlias = ByteString.copyFromUtf8("second");
    private static final ByteString someAlias = ByteString.copyFromUtf8("third");
    private static final ByteString otherAlias = ByteString.copyFromUtf8("fourth");
    private static final ByteString mirrorAlias = ByteString.copyFrom(evmAddress);
    private static final ByteString create2Alias = ByteString.copyFrom(create2Address);
    private static final TokenID someToken = IdUtils.asToken("0.0.666");
}
