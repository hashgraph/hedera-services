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

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asContract;
import static com.hedera.test.utils.IdUtils.asFile;

import com.hedera.test.factories.keys.KeyTree;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Optional;

public class ContractUpdateFactory extends SignedTxnFactory<ContractUpdateFactory> {
    private final String contract;
    private boolean newDeprecatedAdminKey = false;
    private Optional<String> newMemo = Optional.empty();
    private Optional<FileID> newFile = Optional.empty();
    private Optional<KeyTree> newAdminKt = Optional.empty();
    private Optional<Duration> newAutoRenewPeriod = Optional.empty();
    private Optional<Timestamp> newExpiration = Optional.empty();
    private Optional<AccountID> newProxyAccount = Optional.empty();
    private Optional<AccountID> newAutoRenewAccount = Optional.empty();

    public ContractUpdateFactory(String contract) {
        this.contract = contract;
    }

    public static ContractUpdateFactory newSignedContractUpdate(String contract) {
        return new ContractUpdateFactory(contract);
    }

    @Override
    protected ContractUpdateFactory self() {
        return this;
    }

    @Override
    protected long feeFor(Transaction signedTxn, int numPayerKeys) {
        return 0;
    }

    @Override
    protected void customizeTxn(TransactionBody.Builder txn) {
        ContractUpdateTransactionBody.Builder op =
                ContractUpdateTransactionBody.newBuilder().setContractID(asContract(contract));
        newMemo.ifPresent(s -> op.setMemo(s));
        newFile.ifPresent(f -> op.setFileID(f));
        if (newDeprecatedAdminKey) {
            op.setAdminKey(ContractCreateFactory.DEPRECATED_CID_KEY);
        } else {
            newAdminKt.ifPresent(kt -> op.setAdminKey(kt.asKey(keyFactory)));
        }
        newAutoRenewPeriod.ifPresent(p -> op.setAutoRenewPeriod(p));
        newExpiration.ifPresent(t -> op.setExpirationTime(t));
        newProxyAccount.ifPresent(a -> op.setProxyAccountID(a));
        newAutoRenewAccount.ifPresent(a -> op.setAutoRenewAccountId(a));
        txn.setContractUpdateInstance(op);
    }

    public ContractUpdateFactory newMemo(String s) {
        this.newMemo = Optional.of(s);
        return this;
    }

    public ContractUpdateFactory newFile(String f) {
        this.newFile = Optional.of(asFile(f));
        return this;
    }

    public ContractUpdateFactory newDeprecatedAdminKey(boolean useDeprecated) {
        this.newDeprecatedAdminKey = useDeprecated;
        return this;
    }

    public ContractUpdateFactory newAdminKt(KeyTree kt) {
        this.newAdminKt = Optional.of(kt);
        return this;
    }

    public ContractUpdateFactory newAutoRenewPeriod(Duration d) {
        this.newAutoRenewPeriod = Optional.of(d);
        return this;
    }

    public ContractUpdateFactory newExpiration(Timestamp t) {
        this.newExpiration = Optional.of(t);
        return this;
    }

    public ContractUpdateFactory newProxyAccount(String a) {
        this.newProxyAccount = Optional.of(asAccount(a));
        return this;
    }

    public ContractUpdateFactory newAutoRenewAccount(String a) {
        this.newAutoRenewAccount = Optional.of(asAccount(a));
        return this;
    }
}
