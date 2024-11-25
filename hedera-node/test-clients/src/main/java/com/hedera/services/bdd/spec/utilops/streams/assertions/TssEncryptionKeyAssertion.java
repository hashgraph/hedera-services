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

package com.hedera.services.bdd.spec.utilops.streams.assertions;

import static com.hedera.services.bdd.junit.hedera.utils.AddressBookUtils.CLASSIC_FIRST_NODE_ACCOUNT_NUM;

import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.services.bdd.spec.HapiSpec;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.Assertions;

public class TssEncryptionKeyAssertion implements BlockStreamAssertion {
    private final HapiSpec spec;

    private int actualTssEncryptionKeyTxns = 0;

    public TssEncryptionKeyAssertion(@NonNull HapiSpec spec) {
        this.spec = spec;
    }

    @Override
    public boolean test(@NonNull Block block) throws AssertionError {
        observeInteractionsIn(block);
        int expectedTssEncryptionKeyTxns = 4;
        if (actualTssEncryptionKeyTxns != expectedTssEncryptionKeyTxns) {
            if (actualTssEncryptionKeyTxns > expectedTssEncryptionKeyTxns) {
                Assertions.fail("Too many TSS Encryption Key txns submitted, expected " + expectedTssEncryptionKeyTxns
                        + " but got " + actualTssEncryptionKeyTxns);
            }
            return actualTssEncryptionKeyTxns == expectedTssEncryptionKeyTxns;
        }
        return false;
    }

    private void observeInteractionsIn(@NonNull final Block block) {
        for (final var item : block.items()) {
            if (item.hasEventTransaction()) {
                try {
                    final var wrapper = Transaction.PROTOBUF.parse(
                            item.eventTransactionOrThrow().applicationTransactionOrThrow());
                    final var signedTxn = SignedTransaction.PROTOBUF.parse(wrapper.signedTransactionBytes());
                    final var body =
                            com.hedera.hapi.node.transaction.TransactionBody.PROTOBUF.parse(signedTxn.bodyBytes());
                    if (body.nodeAccountIDOrElse(AccountID.DEFAULT).accountNumOrElse(0L)
                            == CLASSIC_FIRST_NODE_ACCOUNT_NUM) {
                        if (body.hasTssEncryptionKey()) {
                            actualTssEncryptionKeyTxns++;
                        }
                    }
                } catch (ParseException e) {
                    Assertions.fail(e.getMessage());
                }
            }
        }
    }
}
