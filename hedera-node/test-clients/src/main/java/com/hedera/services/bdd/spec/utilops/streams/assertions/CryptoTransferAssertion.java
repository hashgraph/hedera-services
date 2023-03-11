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

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.stream.proto.RecordStreamItem;
import com.hederahashgraph.api.proto.java.AccountAmount;
import javax.annotation.Nullable;

public class CryptoTransferAssertion implements RecordStreamAssertion {
    private final HapiSpec spec;
    private String fromAccount;
    private String toAccount;

    @Nullable
    private Long amount;

    public CryptoTransferAssertion(
            final HapiSpec spec, final String fromAccount, final String toAccount, final Long amount) {
        this.spec = spec;
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.amount = amount;
    }

    @Override
    public boolean isApplicableTo(final RecordStreamItem item) {
        final var transactionRecord = item.getRecord();
        final var fromAccountId = spec.registry().getAccountID(fromAccount);
        final var toAccountId = spec.registry().getAccountID(toAccount);

        boolean foundFromTx = false; // have we found the from account with the correct amount in this RecordStreamItem?
        boolean foundToTx = false; // have we found the to account with the correct amount in this RecordStreamItem?

        for (AccountAmount accountAmount : transactionRecord.getTransferList().getAccountAmountsList()) {
            if (!foundFromTx || !foundToTx) {
                if (!foundFromTx
                        && accountAmount.getAccountID().equals(fromAccountId)
                        && accountAmount.getAmount() == (-1 * amount)) {
                    foundFromTx = true;
                    continue;
                }
                if (!foundToTx
                        && accountAmount.getAccountID().equals(toAccountId)
                        && accountAmount.getAmount() == amount) foundToTx = true;
            } else {
                break; // we've found both transfers, so we can stop looking
            }
        }
        return (foundFromTx && foundToTx);
    }

    @Override
    public boolean test(final RecordStreamItem item) throws AssertionError {
        return isApplicableTo(item);
    }

    @Override
    public String toString() {
        return "CryptoTransferAssertion{"
                + "fromAccount='"
                + fromAccount
                + '\''
                + ", toAccount='"
                + toAccount
                + '\''
                + ", amount="
                + amount
                + '}';
    }
}
