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

package com.hedera.node.app.workflows;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.fixtures.Scenarios;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * A very useful class for generating TransactionInfo's that can be used for testing.
 */
public class TransactionScenarioBuilder implements Scenarios {
    private TransactionBody body;
    private HederaFunctionality function;

    public TransactionScenarioBuilder() {
        this(goodDefaultBody(), HederaFunctionality.CRYPTO_TRANSFER);
    }

    public TransactionScenarioBuilder(
            @NonNull final TransactionBody body, @NonNull final HederaFunctionality function) {
        this.body = requireNonNull(body);
        this.function = function;
    }

    public static TransactionScenarioBuilder scenario() {
        return new TransactionScenarioBuilder();
    }

    public TransactionScenarioBuilder withTransactionID(@Nullable final TransactionID transactionID) {
        body = body.copyBuilder().transactionID(transactionID).build();
        return this;
    }

    public TransactionScenarioBuilder withPayer(@Nullable final AccountID payer) {
        final var oldId = body.transactionID();
        final var id = oldId == null
                ? TransactionID.newBuilder().accountID(payer).build()
                : body.transactionID().copyBuilder().accountID(payer).build();
        return withTransactionID(id);
    }

    public TransactionScenarioBuilder withTransactionValidStart(@Nullable final Timestamp transactionValidStart) {
        final var oldId = body.transactionID();
        final var id = oldId == null
                ? TransactionID.newBuilder()
                        .transactionValidStart(transactionValidStart)
                        .build()
                : body.transactionID()
                        .copyBuilder()
                        .transactionValidStart(transactionValidStart)
                        .build();
        return withTransactionID(id);
    }

    public TransactionScenarioBuilder withTransactionValidDuration(@Nullable final Duration transactionValidDuration) {
        body = body.copyBuilder()
                .transactionValidDuration(transactionValidDuration)
                .build();
        return this;
    }

    public TransactionScenarioBuilder withTransactionFee(final long transactionFee) {
        body = body.copyBuilder().transactionFee(transactionFee).build();
        return this;
    }

    public TransactionScenarioBuilder withMemo(@Nullable final String memo) {
        body = body.copyBuilder().memo(memo).build();
        return this;
    }

    public TransactionScenarioBuilder withCryptoTransfer(@Nullable final CryptoTransferTransactionBody op) {
        body = body.copyBuilder().cryptoTransfer(op).build();
        return this;
    }

    @NonNull
    public TransactionInfo txInfo() {
        final var signedbytes = asBytes(TransactionBody.PROTOBUF, body);
        final var signedTx =
                SignedTransaction.newBuilder().bodyBytes(signedbytes).build();
        final var tx = Transaction.newBuilder()
                .signedTransactionBytes(asBytes(SignedTransaction.PROTOBUF, signedTx))
                .build();
        return new TransactionInfo(tx, body, SignatureMap.DEFAULT, signedbytes, function);
    }

    public static TransactionBody goodDefaultBody() {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder()
                        .accountID(ALICE.accountID())
                        .transactionValidStart(Timestamp.newBuilder()
                                .seconds((System.currentTimeMillis() / 1000) - 1)
                                .build())
                        .build())
                .cryptoTransfer(CryptoTransferTransactionBody.newBuilder()
                        .transfers(TransferList.newBuilder()
                                .accountAmounts(
                                        AccountAmount.newBuilder()
                                                .accountID(ALICE.accountID())
                                                .amount(-10)
                                                .build(),
                                        AccountAmount.newBuilder()
                                                .accountID(BOB.accountID())
                                                .amount(10)
                                                .build())
                                .build())
                        .build())
                .transactionFee(1L)
                .transactionValidDuration(Duration.newBuilder().seconds(60).build())
                .build();
    }
}
