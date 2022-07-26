/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.test.factories.txns;

import static com.hedera.test.factories.keys.NodeFactory.ed25519;
import static com.hedera.test.factories.keys.NodeFactory.list;
import static com.hedera.test.factories.keys.NodeFactory.threshold;

import com.hedera.test.factories.keys.KeyTree;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.OptionalLong;

public class CryptoCreateFactory extends SignedTxnFactory<CryptoCreateFactory> {
    public static final KeyTree DEFAULT_ACCOUNT_KT =
            KeyTree.withRoot(list(ed25519(), threshold(1, ed25519(), ed25519(false))));
    public static final Duration DEFAULT_AUTO_RENEW_PERIOD =
            Duration.newBuilder().setSeconds(1_000L).build();

    private long sendThresholdTinybars = 5_000_000_000_000_000_000L;
    private long receiveThresholdTinybars = 5_000_000_000_000_000_000L;
    private boolean receiverSigRequired = false;
    private KeyTree accountKt = DEFAULT_ACCOUNT_KT;
    private Duration autoRenewPeriod = DEFAULT_AUTO_RENEW_PERIOD;
    private OptionalLong balance = OptionalLong.empty();

    private CryptoCreateFactory() {}

    public static CryptoCreateFactory newSignedCryptoCreate() {
        return new CryptoCreateFactory();
    }

    @Override
    protected CryptoCreateFactory self() {
        return this;
    }

    @Override
    protected long feeFor(Transaction signedTxn, int numPayerKeys) {
        return 0;
    }

    @Override
    protected void customizeTxn(TransactionBody.Builder txn) {
        CryptoCreateTransactionBody.Builder op =
                CryptoCreateTransactionBody.newBuilder()
                        .setKey(accountKt.asKey(keyFactory))
                        .setAutoRenewPeriod(autoRenewPeriod)
                        .setReceiverSigRequired(receiverSigRequired)
                        .setSendRecordThreshold(sendThresholdTinybars)
                        .setReceiveRecordThreshold(receiveThresholdTinybars);
        balance.ifPresent(op::setInitialBalance);
        txn.setCryptoCreateAccount(op);
    }

    public CryptoCreateFactory accountKt(KeyTree accountKt) {
        this.accountKt = accountKt;
        return this;
    }

    public CryptoCreateFactory balance(long amount) {
        this.balance = OptionalLong.of(amount);
        return this;
    }

    public CryptoCreateFactory receiverSigRequired(boolean isRequired) {
        this.receiverSigRequired = isRequired;
        return this;
    }
}
