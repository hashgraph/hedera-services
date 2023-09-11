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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.APPROVED_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.APPROVED_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_FUNGIBLE_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.OWNER_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.OWNER_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.RECEIVER_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.RECEIVER_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.UNAUTHORIZED_SPENDER_ID;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.DecodingStrategies;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.associations.AssociationsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.ClassicTransfersCall;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DecodingStrategiesTest {
    private static final long SERIAL_NO = 666;
    private static final long SECOND_SERIAL_NO = 777;
    private static final BigInteger BI_SERIAL_NO = BigInteger.valueOf(SERIAL_NO);
    private static final long HBAR_AMOUNT = 42L;
    private static final long SECOND_HBAR_AMOUNT = 69L;
    private static final long FUNGIBLE_AMOUNT = 1_234_567L;
    private static final long SECOND_FUNGIBLE_AMOUNT = 7_654_321L;
    private static final BigInteger BI_FUNGIBLE_AMOUNT = BigInteger.valueOf(1_234_567L);

    @Mock
    private AddressIdConverter addressIdConverter;

    private final DecodingStrategies subject = new DecodingStrategies();

    @Test
    void associateOneWorks() {
        final var encoded = AssociationsCall.ASSOCIATE_ONE
                .encodeCallWithArgs(OWNER_HEADLONG_ADDRESS, NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS)
                .array();
        givenConvertible(OWNER_HEADLONG_ADDRESS, OWNER_ID);
        final var body = subject.decodeAssociateOne(encoded, addressIdConverter);
        assertAssociationPresent(body, OWNER_ID, NON_FUNGIBLE_TOKEN_ID);
    }

    @Test
    void associateManyWorks() {
        final var encoded = AssociationsCall.ASSOCIATE_MANY
                .encodeCallWithArgs(
                        OWNER_HEADLONG_ADDRESS,
                        new Address[] {NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, FUNGIBLE_TOKEN_HEADLONG_ADDRESS})
                .array();
        givenConvertible(OWNER_HEADLONG_ADDRESS, OWNER_ID);
        final var body = subject.decodeAssociateMany(encoded, addressIdConverter);
        assertAssociationPresent(body, OWNER_ID, FUNGIBLE_TOKEN_ID);
        assertAssociationPresent(body, OWNER_ID, NON_FUNGIBLE_TOKEN_ID);
    }

    @Test
    void dissociateOneWorks() {
        final var encoded = AssociationsCall.DISSOCIATE_ONE
                .encodeCallWithArgs(OWNER_HEADLONG_ADDRESS, NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS)
                .array();
        givenConvertible(OWNER_HEADLONG_ADDRESS, OWNER_ID);
        final var body = subject.decodeDissociateOne(encoded, addressIdConverter);
        assertDissociationPresent(body, OWNER_ID, NON_FUNGIBLE_TOKEN_ID);
    }

    @Test
    void dissociateManyWorks() {
        final var encoded = AssociationsCall.DISSOCIATE_MANY
                .encodeCallWithArgs(
                        OWNER_HEADLONG_ADDRESS,
                        new Address[] {NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, FUNGIBLE_TOKEN_HEADLONG_ADDRESS})
                .array();
        givenConvertible(OWNER_HEADLONG_ADDRESS, OWNER_ID);
        final var body = subject.decodeDissociateMany(encoded, addressIdConverter);
        assertDissociationPresent(body, OWNER_ID, FUNGIBLE_TOKEN_ID);
        assertDissociationPresent(body, OWNER_ID, NON_FUNGIBLE_TOKEN_ID);
    }

    @Test
    void hrcTransferNftFromWorks() {
        final var encoded = ClassicTransfersCall.TRANSFER_NFT_FROM
                .encodeCallWithArgs(
                        NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                        OWNER_HEADLONG_ADDRESS,
                        RECEIVER_HEADLONG_ADDRESS,
                        BI_SERIAL_NO)
                .array();
        givenConvertible(OWNER_HEADLONG_ADDRESS, OWNER_ID);
        givenConvertibleCredit(RECEIVER_HEADLONG_ADDRESS, RECEIVER_ID);

        final var body = subject.decodeHrcTransferNftFrom(encoded, addressIdConverter);

        assertTokenTransfersCount(body, 1);
        assertOwnershipChange(body, NON_FUNGIBLE_TOKEN_ID, OWNER_ID, RECEIVER_ID, SERIAL_NO, true);
    }

    @Test
    void hrcTransferFromWorks() {
        final var encoded = ClassicTransfersCall.TRANSFER_FROM
                .encodeCallWithArgs(
                        FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                        OWNER_HEADLONG_ADDRESS,
                        RECEIVER_HEADLONG_ADDRESS,
                        BI_FUNGIBLE_AMOUNT)
                .array();
        givenConvertible(OWNER_HEADLONG_ADDRESS, OWNER_ID);
        givenConvertibleCredit(RECEIVER_HEADLONG_ADDRESS, RECEIVER_ID);

        final var body = subject.decodeHrcTransferFrom(encoded, addressIdConverter);

        assertTokenTransfersCount(body, 1);
        assertUnitsFromTo(body, FUNGIBLE_TOKEN_ID, OWNER_ID, RECEIVER_ID, FUNGIBLE_AMOUNT, true);
    }

    @Test
    void transferNftWorks() {
        final var encoded = ClassicTransfersCall.TRANSFER_NFT
                .encodeCallWithArgs(
                        NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                        OWNER_HEADLONG_ADDRESS,
                        RECEIVER_HEADLONG_ADDRESS,
                        SERIAL_NO)
                .array();
        givenConvertible(OWNER_HEADLONG_ADDRESS, OWNER_ID);
        givenConvertibleCredit(RECEIVER_HEADLONG_ADDRESS, RECEIVER_ID);

        final var body = subject.decodeTransferNft(encoded, addressIdConverter);

        assertTokenTransfersCount(body, 1);
        assertOwnershipChange(body, NON_FUNGIBLE_TOKEN_ID, OWNER_ID, RECEIVER_ID, SERIAL_NO, false);
    }

    @Test
    void transferNftsWorks() {
        final var encoded = ClassicTransfersCall.TRANSFER_NFTS
                .encodeCallWithArgs(
                        NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                        new Address[] {OWNER_HEADLONG_ADDRESS, APPROVED_HEADLONG_ADDRESS},
                        new Address[] {RECEIVER_HEADLONG_ADDRESS, UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS},
                        new long[] {SERIAL_NO, SECOND_SERIAL_NO})
                .array();
        givenConvertible(OWNER_HEADLONG_ADDRESS, OWNER_ID);
        givenConvertible(APPROVED_HEADLONG_ADDRESS, APPROVED_ID);
        givenConvertibleCredit(RECEIVER_HEADLONG_ADDRESS, RECEIVER_ID);
        givenConvertibleCredit(UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS, UNAUTHORIZED_SPENDER_ID);

        final var body = subject.decodeTransferNfts(encoded, addressIdConverter);

        assertTokenTransfersCount(body, 1);
        assertOwnershipChange(body, NON_FUNGIBLE_TOKEN_ID, OWNER_ID, RECEIVER_ID, SERIAL_NO, false);
        assertOwnershipChange(
                body, NON_FUNGIBLE_TOKEN_ID, APPROVED_ID, UNAUTHORIZED_SPENDER_ID, SECOND_SERIAL_NO, false);
    }

    @Test
    void transferNftsThrowsOnMismatchedArgs() {
        final var encoded = ClassicTransfersCall.TRANSFER_NFTS
                .encodeCallWithArgs(
                        NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                        new Address[] {OWNER_HEADLONG_ADDRESS},
                        new Address[] {RECEIVER_HEADLONG_ADDRESS, UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS},
                        new long[] {SERIAL_NO, SECOND_SERIAL_NO})
                .array();
        assertThrows(IllegalArgumentException.class, () -> subject.decodeTransferNfts(encoded, addressIdConverter));
    }

    @Test
    void transferTokensWorks() {
        final var encoded = ClassicTransfersCall.TRANSFER_TOKENS
                .encodeCallWithArgs(
                        FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                        new Address[] {
                            OWNER_HEADLONG_ADDRESS,
                            APPROVED_HEADLONG_ADDRESS,
                            RECEIVER_HEADLONG_ADDRESS,
                            UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS
                        },
                        new long[] {-FUNGIBLE_AMOUNT, -SECOND_FUNGIBLE_AMOUNT, FUNGIBLE_AMOUNT, SECOND_FUNGIBLE_AMOUNT})
                .array();
        givenConvertible(OWNER_HEADLONG_ADDRESS, OWNER_ID);
        givenConvertible(APPROVED_HEADLONG_ADDRESS, APPROVED_ID);
        givenConvertibleCredit(RECEIVER_HEADLONG_ADDRESS, RECEIVER_ID);
        givenConvertibleCredit(UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS, UNAUTHORIZED_SPENDER_ID);

        final var body = subject.decodeTransferTokens(encoded, addressIdConverter);

        assertTokenTransfersCount(body, 1);
        assertUnitsFromTo(body, FUNGIBLE_TOKEN_ID, OWNER_ID, RECEIVER_ID, FUNGIBLE_AMOUNT, false);
        assertUnitsFromTo(body, FUNGIBLE_TOKEN_ID, APPROVED_ID, UNAUTHORIZED_SPENDER_ID, SECOND_FUNGIBLE_AMOUNT, false);
    }

    @Test
    void transferTokenWorks() {
        final var encoded = ClassicTransfersCall.TRANSFER_TOKEN
                .encodeCallWithArgs(
                        FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                        OWNER_HEADLONG_ADDRESS,
                        RECEIVER_HEADLONG_ADDRESS,
                        FUNGIBLE_AMOUNT)
                .array();
        givenConvertible(OWNER_HEADLONG_ADDRESS, OWNER_ID);
        givenConvertibleCredit(RECEIVER_HEADLONG_ADDRESS, RECEIVER_ID);

        final var body = subject.decodeTransferToken(encoded, addressIdConverter);

        assertTokenTransfersCount(body, 1);
        assertUnitsFromTo(body, FUNGIBLE_TOKEN_ID, OWNER_ID, RECEIVER_ID, FUNGIBLE_AMOUNT, false);
    }

    @Test
    void transferTokensThrowsOnMismatchedArgs() {
        final var encoded = ClassicTransfersCall.TRANSFER_TOKENS
                .encodeCallWithArgs(
                        FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                        new Address[] {
                            OWNER_HEADLONG_ADDRESS,
                            APPROVED_HEADLONG_ADDRESS,
                            RECEIVER_HEADLONG_ADDRESS,
                            UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS
                        },
                        new long[] {-SECOND_FUNGIBLE_AMOUNT, FUNGIBLE_AMOUNT, SECOND_FUNGIBLE_AMOUNT})
                .array();
        assertThrows(IllegalArgumentException.class, () -> subject.decodeTransferTokens(encoded, addressIdConverter));
    }

    @Test
    void cryptoTransferWorks() {
        final var encoded = ClassicTransfersCall.CRYPTO_TRANSFER
                .encodeCallWithArgs(new Object[] {
                    new Tuple[] {
                        Tuple.of(
                                FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                                new Tuple[] {
                                    Tuple.of(OWNER_HEADLONG_ADDRESS, -FUNGIBLE_AMOUNT),
                                    Tuple.of(RECEIVER_HEADLONG_ADDRESS, FUNGIBLE_AMOUNT)
                                },
                                new Tuple[] {}),
                        Tuple.of(NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, new Tuple[] {}, new Tuple[] {
                            Tuple.of(OWNER_HEADLONG_ADDRESS, RECEIVER_HEADLONG_ADDRESS, SERIAL_NO),
                            Tuple.of(APPROVED_HEADLONG_ADDRESS, UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS, SECOND_SERIAL_NO)
                        }),
                    }
                })
                .array();
        givenConvertible(OWNER_HEADLONG_ADDRESS, OWNER_ID);
        givenConvertible(APPROVED_HEADLONG_ADDRESS, APPROVED_ID);
        givenConvertibleCredit(RECEIVER_HEADLONG_ADDRESS, RECEIVER_ID);
        givenConvertibleCredit(UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS, UNAUTHORIZED_SPENDER_ID);

        final var body = subject.decodeCryptoTransfer(encoded, addressIdConverter);

        assertTokenTransfersCount(body, 2);
        assertUnitsFromTo(body, FUNGIBLE_TOKEN_ID, OWNER_ID, RECEIVER_ID, FUNGIBLE_AMOUNT, false);
        assertOwnershipChange(body, NON_FUNGIBLE_TOKEN_ID, OWNER_ID, RECEIVER_ID, SERIAL_NO, false);
        assertOwnershipChange(
                body, NON_FUNGIBLE_TOKEN_ID, APPROVED_ID, UNAUTHORIZED_SPENDER_ID, SECOND_SERIAL_NO, false);
    }

    @Test
    void cryptoTransferV2Works() {
        final var encoded = ClassicTransfersCall.CRYPTO_TRANSFER_V2
                .encodeCallWithArgs(
                        Tuple.of(new Object[] {
                            new Tuple[] {
                                Tuple.of(OWNER_HEADLONG_ADDRESS, -HBAR_AMOUNT, false),
                                Tuple.of(RECEIVER_HEADLONG_ADDRESS, HBAR_AMOUNT, false),
                                Tuple.of(APPROVED_HEADLONG_ADDRESS, -SECOND_HBAR_AMOUNT, true),
                                Tuple.of(UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS, SECOND_HBAR_AMOUNT, false),
                            }
                        }),
                        new Tuple[] {
                            Tuple.of(
                                    FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                                    new Tuple[] {
                                        Tuple.of(OWNER_HEADLONG_ADDRESS, -FUNGIBLE_AMOUNT, true),
                                        Tuple.of(RECEIVER_HEADLONG_ADDRESS, FUNGIBLE_AMOUNT, false)
                                    },
                                    new Tuple[] {}),
                            Tuple.of(NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, new Tuple[] {}, new Tuple[] {
                                Tuple.of(OWNER_HEADLONG_ADDRESS, RECEIVER_HEADLONG_ADDRESS, SERIAL_NO, true),
                                Tuple.of(
                                        APPROVED_HEADLONG_ADDRESS,
                                        UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS,
                                        SECOND_SERIAL_NO,
                                        false)
                            }),
                        })
                .array();
        givenConvertible(OWNER_HEADLONG_ADDRESS, OWNER_ID);
        givenConvertible(APPROVED_HEADLONG_ADDRESS, APPROVED_ID);
        givenConvertibleCredit(RECEIVER_HEADLONG_ADDRESS, RECEIVER_ID);
        givenConvertibleCredit(UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS, UNAUTHORIZED_SPENDER_ID);

        final var body = subject.decodeCryptoTransferV2(encoded, addressIdConverter);

        assertTokenTransfersCount(body, 2);
        assertHbarFromTo(body, OWNER_ID, RECEIVER_ID, HBAR_AMOUNT, false);
        assertHbarFromTo(body, APPROVED_ID, UNAUTHORIZED_SPENDER_ID, SECOND_HBAR_AMOUNT, true);
        assertUnitsFromTo(body, FUNGIBLE_TOKEN_ID, OWNER_ID, RECEIVER_ID, FUNGIBLE_AMOUNT, true);
        assertOwnershipChange(body, NON_FUNGIBLE_TOKEN_ID, OWNER_ID, RECEIVER_ID, SERIAL_NO, true);
        assertOwnershipChange(
                body, NON_FUNGIBLE_TOKEN_ID, APPROVED_ID, UNAUTHORIZED_SPENDER_ID, SECOND_SERIAL_NO, false);
    }

    private void assertTokenTransfersCount(@NonNull final TransactionBody body, final int n) {
        assertEquals(
                n,
                body.cryptoTransferOrThrow().tokenTransfersOrElse(emptyList()).size());
    }

    private void assertHbarFromTo(
            @NonNull final TransactionBody body,
            @NonNull final AccountID from,
            @NonNull final AccountID to,
            final long amount,
            final boolean approval) {
        boolean foundDebit = false;
        boolean foundCredit = false;
        for (final var unitAdjust :
                body.cryptoTransferOrThrow().transfersOrThrow().accountAmountsOrThrow()) {
            if (unitAdjust.accountIDOrThrow().equals(from)
                    && unitAdjust.amount() == -amount
                    && unitAdjust.isApproval() == approval) {
                foundDebit = true;
            } else if (unitAdjust.accountIDOrThrow().equals(to)
                    && unitAdjust.amount() == amount
                    && !unitAdjust.isApproval()) {
                foundCredit = true;
            }
        }
        if (!foundDebit || !foundCredit) {
            Assertions.fail("No matching unit adjustment found");
        }
    }

    private void assertUnitsFromTo(
            @NonNull final TransactionBody body,
            @NonNull final TokenID tokenId,
            @NonNull final AccountID from,
            @NonNull final AccountID to,
            final long amount,
            final boolean approval) {
        boolean foundDebit = false;
        boolean foundCredit = false;
        for (final var tokenTransfers : body.cryptoTransferOrThrow().tokenTransfersOrThrow()) {
            if (tokenTransfers.tokenOrThrow().equals(tokenId)) {
                assertTrue(tokenTransfers.nftTransfersOrElse(emptyList()).isEmpty());
                for (final var unitAdjust : tokenTransfers.transfersOrThrow()) {
                    if (unitAdjust.accountIDOrThrow().equals(from)
                            && unitAdjust.amount() == -amount
                            && unitAdjust.isApproval() == approval) {
                        foundDebit = true;
                    } else if (unitAdjust.accountIDOrThrow().equals(to)
                            && unitAdjust.amount() == amount
                            && !unitAdjust.isApproval()) {
                        foundCredit = true;
                    }
                }
            }
        }
        if (!foundDebit || !foundCredit) {
            Assertions.fail("No matching unit adjustment found");
        }
    }

    private void assertOwnershipChange(
            @NonNull final TransactionBody body,
            @NonNull final TokenID tokenId,
            @NonNull final AccountID from,
            @NonNull final AccountID to,
            final long serialNo,
            final boolean approval) {
        for (final var tokenTransfers : body.cryptoTransferOrThrow().tokenTransfersOrThrow()) {
            if (tokenTransfers.tokenOrThrow().equals(tokenId)) {
                assertTrue(tokenTransfers.transfersOrElse(emptyList()).isEmpty());
                for (final var ownershipChange : tokenTransfers.nftTransfersOrThrow()) {
                    if (ownershipChange.senderAccountIDOrThrow().equals(from)
                            && ownershipChange.receiverAccountIDOrThrow().equals(to)
                            && ownershipChange.serialNumber() == serialNo
                            && ownershipChange.isApproval() == approval) {
                        return;
                    }
                }
            }
        }
        Assertions.fail("No matching ownership change found");
    }

    private void givenConvertible(@NonNull final Address address, @NonNull final AccountID id) {
        given(addressIdConverter.convert(address)).willReturn(id);
    }

    private void givenConvertibleCredit(@NonNull final Address address, @NonNull final AccountID id) {
        given(addressIdConverter.convertCredit(address)).willReturn(id);
    }

    private void assertAssociationPresent(
            @NonNull final TransactionBody body, @NonNull final AccountID target, @NonNull final TokenID tokenId) {
        final var associate = body.tokenAssociateOrThrow();
        assertEquals(target, associate.account());
        org.assertj.core.api.Assertions.assertThat(associate.tokensOrThrow()).contains(tokenId);
    }

    private void assertDissociationPresent(
            @NonNull final TransactionBody body, @NonNull final AccountID target, @NonNull final TokenID tokenId) {
        final var dissociate = body.tokenDissociateOrThrow();
        assertEquals(target, dissociate.account());
        org.assertj.core.api.Assertions.assertThat(dissociate.tokensOrThrow()).contains(tokenId);
    }
}
