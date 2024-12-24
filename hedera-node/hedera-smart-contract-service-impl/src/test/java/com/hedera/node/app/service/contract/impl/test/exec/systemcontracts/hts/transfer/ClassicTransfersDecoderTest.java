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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.transfer;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.tokenTransferList;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.tokenTransferLists;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.transferList;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.ClassicTransfersDecoder;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.ClassicTransfersTranslator;
import java.math.BigInteger;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClassicTransfersDecoderTest {

    public static final int ACCOUNT_ID_41 = 41;
    public static final int ACCOUNT_ID_42 = 42;
    private static final Address ACCT_ADDR_1 = addressFromNum(ACCOUNT_ID_41);
    private static final Address ACCT_ADDR_2 = addressFromNum(ACCOUNT_ID_42);
    private static final Address TOKEN_ADDR_10 = addressFromNum(10);
    private static final Tuple[] EMPTY_TUPLE_ARRAY = new Tuple[] {};

    @Mock
    private AddressIdConverter converter;

    private ClassicTransfersDecoder subject;

    @BeforeEach
    void setUp() {
        subject = new ClassicTransfersDecoder();
    }

    @Test
    void decodeTransferTokenHasDebitFirst() {
        final var totalToTransfer = 50L;
        BDDMockito.given(converter.convert(ACCT_ADDR_1))
                .willReturn(AccountID.newBuilder().accountNum(ACCOUNT_ID_41).build());
        BDDMockito.given(converter.convertCredit(ACCT_ADDR_2))
                .willReturn(AccountID.newBuilder().accountNum(ACCOUNT_ID_42).build());
        final var encodedInput = ClassicTransfersTranslator.TRANSFER_TOKEN.encodeCallWithArgs(
                TOKEN_ADDR_10, ACCT_ADDR_1, ACCT_ADDR_2, totalToTransfer);

        final var result = subject.decodeTransferToken(encodedInput.array(), converter);
        final var amounts = unwrapTokenAmounts(result);
        Assertions.assertThat(amounts).hasSize(2);
        final var firstEntry = amounts.getFirst();
        Assertions.assertThat(firstEntry.accountID().accountNum()).isEqualTo(ACCOUNT_ID_41);
        Assertions.assertThat(firstEntry.amount()).isEqualTo(-totalToTransfer);
        final var secondEntry = amounts.get(1);
        Assertions.assertThat(secondEntry.accountID().accountNum()).isEqualTo(ACCOUNT_ID_42);
        Assertions.assertThat(secondEntry.amount()).isEqualTo(totalToTransfer);
    }

    @Test
    void decodeHrcTransferFromHasCreditFirst() {
        final var totalToTransfer = 25L;
        BDDMockito.given(converter.convert(ACCT_ADDR_2))
                .willReturn(AccountID.newBuilder().accountNum(ACCOUNT_ID_42).build());
        BDDMockito.given(converter.convertCredit(ACCT_ADDR_1))
                .willReturn(AccountID.newBuilder().accountNum(ACCOUNT_ID_41).build());
        final var encodedInput = ClassicTransfersTranslator.TRANSFER_FROM.encodeCallWithArgs(
                TOKEN_ADDR_10, ACCT_ADDR_2, ACCT_ADDR_1, BigInteger.valueOf(totalToTransfer));

        final var result = subject.decodeHrcTransferFrom(encodedInput.array(), converter);
        final var amounts = unwrapTokenAmounts(result);
        Assertions.assertThat(amounts).hasSize(2);
        final var firstEntry = amounts.getFirst();
        Assertions.assertThat(firstEntry.accountID().accountNum()).isEqualTo(ACCOUNT_ID_41);
        Assertions.assertThat(firstEntry.amount()).isEqualTo(totalToTransfer);
        final var secondEntry = amounts.get(1);
        Assertions.assertThat(secondEntry.accountID().accountNum()).isEqualTo(ACCOUNT_ID_42);
        Assertions.assertThat(secondEntry.amount()).isEqualTo(-totalToTransfer);
    }

    @Test
    void decodeCryptoTransferConsolidates() {
        final var totalToTransfer = 25L;
        BDDMockito.given(converter.convert(ACCT_ADDR_1))
                .willReturn(AccountID.newBuilder().accountNum(ACCOUNT_ID_41).build());
        BDDMockito.given(converter.convertCredit(ACCT_ADDR_1))
                .willReturn(AccountID.newBuilder().accountNum(ACCOUNT_ID_41).build());
        final var encodedInput = ClassicTransfersTranslator.CRYPTO_TRANSFER_V2.encodeCallWithArgs(
                transferList()
                        .withAccountAmounts(
                                Tuple.of(ACCT_ADDR_1, -totalToTransfer, false),
                                Tuple.of(ACCT_ADDR_1, totalToTransfer, false))
                        .build(),
                EMPTY_TUPLE_ARRAY);
        final var result = subject.decodeCryptoTransferV2(encodedInput.array(), converter);
        Assertions.assertThat(unwrapTransferAmounts(result)).hasSize(1);
        Assertions.assertThat(unwrapTransferAmounts(result).get(0).amount()).isEqualTo(0);
    }

    @Test
    void decodeCryptoTransferOverflow() {
        BDDMockito.given(converter.convertCredit(ACCT_ADDR_1))
                .willReturn(AccountID.newBuilder().accountNum(ACCOUNT_ID_41).build());
        final var encodedInput = ClassicTransfersTranslator.CRYPTO_TRANSFER_V2.encodeCallWithArgs(
                transferList()
                        .withAccountAmounts(
                                Tuple.of(ACCT_ADDR_1, Long.MAX_VALUE - 1, false), Tuple.of(ACCT_ADDR_1, 2L, false))
                        .build(),
                EMPTY_TUPLE_ARRAY);
        Assertions.assertThatThrownBy(() -> subject.decodeCryptoTransferV2(encodedInput.array(), converter))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount overflow when merging account amounts");
    }

    @Test
    void decodeCryptoTokenTransferOverflow() {
        BDDMockito.given(converter.convertCredit(ACCT_ADDR_1))
                .willReturn(AccountID.newBuilder().accountNum(ACCOUNT_ID_41).build());
        final var encodedInput = ClassicTransfersTranslator.CRYPTO_TRANSFER_V2.encodeCallWithArgs(
                transferList().withAccountAmounts().build(),
                tokenTransferLists()
                        .withTokenTransferList(
                                tokenTransferList()
                                        .forToken(FUNGIBLE_TOKEN_HEADLONG_ADDRESS)
                                        .withAccountAmounts(Tuple.of(ACCT_ADDR_1, Long.MAX_VALUE - 1, false))
                                        .build(),
                                tokenTransferList()
                                        .forToken(FUNGIBLE_TOKEN_HEADLONG_ADDRESS)
                                        .withAccountAmounts(Tuple.of(ACCT_ADDR_1, 2L, false))
                                        .build())
                        .build());
        Assertions.assertThatThrownBy(() -> subject.decodeCryptoTransferV2(encodedInput.array(), converter))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount overflow when merging account amounts");
    }

    private static List<AccountAmount> unwrapTokenAmounts(final TransactionBody body) {
        final var tokenTransfers = body.cryptoTransfer().tokenTransfers();
        // We shouldn't ever have more than one token transfer list
        return tokenTransfers.getFirst().transfers();
    }

    private static List<AccountAmount> unwrapTransferAmounts(final TransactionBody body) {
        final var transfers = body.cryptoTransfer().transfers();
        // We shouldn't ever have more than one transfer list
        return transfers.accountAmounts();
    }

    private static Address addressFromNum(final long accountId) {
        return Address.wrap(Address.toChecksumAddress(BigInteger.valueOf(accountId)));
    }
}
