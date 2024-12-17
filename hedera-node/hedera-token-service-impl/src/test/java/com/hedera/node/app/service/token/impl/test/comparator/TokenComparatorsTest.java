/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.test.comparator;

import static com.hedera.node.app.service.token.impl.comparator.TokenComparators.ACCOUNT_AMOUNT_COMPARATOR;
import static com.hedera.node.app.service.token.impl.comparator.TokenComparators.NFT_TRANSFER_COMPARATOR;
import static com.hedera.node.app.service.token.impl.comparator.TokenComparators.TOKEN_ID_COMPARATOR;
import static com.hedera.node.app.service.token.impl.comparator.TokenComparators.TOKEN_TRANSFER_LIST_COMPARATOR;
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.service.token.impl.handlers.BaseTokenHandler.asToken;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TokenComparatorsTest {
    private static final AccountID ACCOUNT_1234_ID = asAccount(1234);
    private static final AccountID ACCOUNT_9876_ID = asAccount(9876);

    @Nested
    class AccountAmountComparatorTest {
        private static final AccountAmount ACC_AMOUNT_1234 =
                AccountAmount.newBuilder().amount(1).accountID(ACCOUNT_1234_ID).build();
        private static final AccountAmount ACC_AMOUNT_9876 =
                AccountAmount.newBuilder().amount(1).accountID(ACCOUNT_9876_ID).build();

        @Test
        void checkComparisons() {
            //noinspection EqualsWithItself
            Assertions.assertThatThrownBy(() -> ACCOUNT_AMOUNT_COMPARATOR.compare(null, null))
                    .isInstanceOf(NullPointerException.class);
            Assertions.assertThatThrownBy(() -> ACCOUNT_AMOUNT_COMPARATOR.compare(ACC_AMOUNT_1234, null))
                    .isInstanceOf(NullPointerException.class);
            Assertions.assertThatThrownBy(() -> ACCOUNT_AMOUNT_COMPARATOR.compare(null, ACC_AMOUNT_1234))
                    .isInstanceOf(NullPointerException.class);
            //noinspection EqualsWithItself
            Assertions.assertThat(ACCOUNT_AMOUNT_COMPARATOR.compare(ACC_AMOUNT_1234, ACC_AMOUNT_1234))
                    .isZero();
            Assertions.assertThat(ACCOUNT_AMOUNT_COMPARATOR.compare(ACC_AMOUNT_1234, ACC_AMOUNT_9876))
                    .isNegative();
            Assertions.assertThat(ACCOUNT_AMOUNT_COMPARATOR.compare(ACC_AMOUNT_9876, ACC_AMOUNT_1234))
                    .isPositive();
        }
    }

    @Nested
    class TokenIdComparatorTest {
        private static final TokenID TOKEN_1111 = asToken(1111);
        private static final TokenID TOKEN_2222 = asToken(2222);

        @Test
        void checkComparisons() {
            //noinspection EqualsWithItself
            Assertions.assertThatThrownBy(() -> TOKEN_ID_COMPARATOR.compare(null, null))
                    .isInstanceOf(NullPointerException.class);
            Assertions.assertThatThrownBy(() -> TOKEN_ID_COMPARATOR.compare(TOKEN_1111, null))
                    .isInstanceOf(NullPointerException.class);
            Assertions.assertThatThrownBy(() -> TOKEN_ID_COMPARATOR.compare(null, TOKEN_1111))
                    .isInstanceOf(NullPointerException.class);
            //noinspection EqualsWithItself
            Assertions.assertThat(TOKEN_ID_COMPARATOR.compare(TOKEN_1111, TOKEN_1111))
                    .isZero();
            Assertions.assertThat(TOKEN_ID_COMPARATOR.compare(TOKEN_1111, TOKEN_2222))
                    .isNegative();
            Assertions.assertThat(TOKEN_ID_COMPARATOR.compare(TOKEN_2222, TOKEN_1111))
                    .isPositive();
        }
    }

    @Nested
    class TokenTransferListComparatorTest {
        private static final TokenTransferList TOKEN_TRANSFER_LIST_1 =
                TokenTransferList.newBuilder().token(asToken(1)).build();
        private static final TokenTransferList TOKEN_TRANSFER_LIST_2 =
                TokenTransferList.newBuilder().token(asToken(2)).build();

        @Test
        void checkComparisons() {
            //noinspection EqualsWithItself
            Assertions.assertThatThrownBy(() -> TOKEN_TRANSFER_LIST_COMPARATOR.compare(null, null))
                    .isInstanceOf(NullPointerException.class);
            Assertions.assertThatThrownBy(() -> TOKEN_TRANSFER_LIST_COMPARATOR.compare(TOKEN_TRANSFER_LIST_1, null))
                    .isInstanceOf(NullPointerException.class);
            Assertions.assertThatThrownBy(() -> TOKEN_TRANSFER_LIST_COMPARATOR.compare(null, TOKEN_TRANSFER_LIST_1))
                    .isInstanceOf(NullPointerException.class);
            //noinspection EqualsWithItself
            Assertions.assertThat(TOKEN_TRANSFER_LIST_COMPARATOR.compare(TOKEN_TRANSFER_LIST_1, TOKEN_TRANSFER_LIST_1))
                    .isZero();
            Assertions.assertThat(TOKEN_TRANSFER_LIST_COMPARATOR.compare(TOKEN_TRANSFER_LIST_1, TOKEN_TRANSFER_LIST_2))
                    .isNegative();
            Assertions.assertThat(TOKEN_TRANSFER_LIST_COMPARATOR.compare(TOKEN_TRANSFER_LIST_2, TOKEN_TRANSFER_LIST_1))
                    .isPositive();
        }
    }

    @Nested
    class NftTransferComparatorTest {
        private static final NftTransfer NFT_TRANSFER_LIST_1 = NftTransfer.newBuilder()
                .senderAccountID(asAccount(1111))
                .receiverAccountID(asAccount(2222))
                .serialNumber(1)
                .build();
        private static final NftTransfer NFT_TRANSFER_LIST_2 = NftTransfer.newBuilder()
                .senderAccountID(asAccount(1111))
                .receiverAccountID(asAccount(2222))
                .serialNumber(2)
                .build();
        private static final NftTransfer NFT_TRANSFER_LIST_3 = NftTransfer.newBuilder()
                .senderAccountID(asAccount(3333))
                .receiverAccountID(asAccount(2222))
                .serialNumber(2)
                .build();
        private static final NftTransfer NFT_TRANSFER_LIST_4 = NftTransfer.newBuilder()
                .senderAccountID(asAccount(1111))
                .receiverAccountID(asAccount(3333))
                .serialNumber(2)
                .build();

        @Test
        void checkComparisons() {
            Assertions.assertThatThrownBy(() -> NFT_TRANSFER_COMPARATOR.compare(null, null))
                    .isInstanceOf(NullPointerException.class);
            Assertions.assertThatThrownBy(
                            () -> NFT_TRANSFER_COMPARATOR.compare(NftTransferComparatorTest.NFT_TRANSFER_LIST_1, null))
                    .isInstanceOf(NullPointerException.class);
            Assertions.assertThatThrownBy(() -> NFT_TRANSFER_COMPARATOR.compare(null, NFT_TRANSFER_LIST_1))
                    .isInstanceOf(NullPointerException.class);
            Assertions.assertThat(NFT_TRANSFER_COMPARATOR.compare(NFT_TRANSFER_LIST_1, NFT_TRANSFER_LIST_1))
                    .isZero();
            Assertions.assertThat(NFT_TRANSFER_COMPARATOR.compare(NFT_TRANSFER_LIST_1, NFT_TRANSFER_LIST_2))
                    .isNegative();
            Assertions.assertThat(NFT_TRANSFER_COMPARATOR.compare(NFT_TRANSFER_LIST_2, NFT_TRANSFER_LIST_1))
                    .isPositive();
            Assertions.assertThat(NFT_TRANSFER_COMPARATOR.compare(NFT_TRANSFER_LIST_2, NFT_TRANSFER_LIST_3))
                    .isNegative();
            Assertions.assertThat(NFT_TRANSFER_COMPARATOR.compare(NFT_TRANSFER_LIST_3, NFT_TRANSFER_LIST_2))
                    .isPositive();
            Assertions.assertThat(NFT_TRANSFER_COMPARATOR.compare(NFT_TRANSFER_LIST_2, NFT_TRANSFER_LIST_4))
                    .isNegative();
            Assertions.assertThat(NFT_TRANSFER_COMPARATOR.compare(NFT_TRANSFER_LIST_4, NFT_TRANSFER_LIST_2))
                    .isPositive();
        }
    }
}
