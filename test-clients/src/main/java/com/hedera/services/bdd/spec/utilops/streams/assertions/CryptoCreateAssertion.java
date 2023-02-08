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
package com.hedera.services.bdd.spec.utilops.streams.assertions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.streams.RecordStreamAssertion;
import com.hedera.services.stream.proto.RecordStreamItem;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransferList;
import javax.annotation.Nullable;

/**
 * Hello world-style example of a {@link RecordStreamAssertion}. This one asserts that the record
 * stream includes an item matching the creation of a given account, based on its name in the {@link
 * com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry}.
 */
public class CryptoCreateAssertion implements RecordStreamAssertion {
    private final HapiSpec spec;
    private final String account;

    @Nullable private String expectedMemo;
    @Nullable private Long expectedBalance;

    public CryptoCreateAssertion(final HapiSpec spec, final String account) {
        this.spec = spec;
        this.account = account;
    }

    public CryptoCreateAssertion withMemo(final String expectedMemo) {
        this.expectedMemo = expectedMemo;
        return this;
    }

    public CryptoCreateAssertion withBalance(final Long expectedBalance) {
        this.expectedBalance = expectedBalance;
        return this;
    }

    @Override
    public boolean isApplicableTo(final RecordStreamItem item) {
        final var transactionRecord = item.getRecord();
        if (transactionRecord.getReceipt().hasAccountID()) {
            try {
                final var accountId = spec.registry().getAccountID(account);
                return transactionRecord.getReceipt().getAccountID().equals(accountId);
            } catch (final Throwable ignore) {
                return false;
            }
        }
        return false;
    }

    @Override
    public boolean updateAndTest(final RecordStreamItem item) throws AssertionError {
        if (expectedMemo != null) {
            final var actualMemo = item.getRecord().getMemo();
            assertEquals(expectedMemo, actualMemo, "Wrong memo");
        }
        if (expectedBalance != null) {
            final var actualBalance =
                    amountCredited(
                            item.getRecord().getTransferList(),
                            spec.registry().getAccountID(account));
            assertEquals(expectedBalance, actualBalance, "Wrong balance");
        }
        return true;
    }

    @Override
    public String toString() {
        return "CryptoCreateAssertion{"
                + "account='"
                + account
                + '\''
                + ", expectedMemo='"
                + expectedMemo
                + '\''
                + ", expectedBalance="
                + expectedBalance
                + '}';
    }

    private long amountCredited(final TransferList transfers, final AccountID beneficiary) {
        return transfers.getAccountAmountsList().stream()
                .filter(aa -> aa.getAccountID().equals(beneficiary))
                .mapToLong(AccountAmount::getAmount)
                .sum();
    }
}
