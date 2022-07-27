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

import static com.hedera.test.utils.IdUtils.asContract;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Optional;

public class ContractDeleteFactory extends SignedTxnFactory<ContractDeleteFactory> {
    private final String contract;
    private Optional<AccountID> transferAccount = Optional.empty();
    private Optional<ContractID> transferContract = Optional.empty();

    public ContractDeleteFactory(String contract) {
        this.contract = contract;
    }

    public static ContractDeleteFactory newSignedContractDelete(String contract) {
        return new ContractDeleteFactory(contract);
    }

    public ContractDeleteFactory withBeneficiary(AccountID account) {
        transferAccount = Optional.of(account);
        return this;
    }

    public ContractDeleteFactory withBeneficiary(ContractID contract) {
        transferContract = Optional.of(contract);
        return this;
    }

    @Override
    protected ContractDeleteFactory self() {
        return this;
    }

    @Override
    protected long feeFor(Transaction signedTxn, int numPayerKeys) {
        return 0;
    }

    @Override
    protected void customizeTxn(TransactionBody.Builder txn) {
        ContractDeleteTransactionBody.Builder op =
                ContractDeleteTransactionBody.newBuilder().setContractID(asContract(contract));
        transferAccount.ifPresent(op::setTransferAccountID);
        transferContract.ifPresent(op::setTransferContractID);
        txn.setContractDeleteInstance(op);
    }
}
