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
package com.hedera.services.bdd.spec.transactions.system;

import static com.hedera.services.bdd.spec.transactions.TxnUtils.asContractId;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asFileId;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.SystemUndelete;

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SystemUndeleteTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.Assertions;

public class HapiSysUndelete extends HapiTxnOp<HapiSysUndelete> {
    private Optional<String> file = Optional.empty();
    private Optional<String> contract = Optional.empty();

    public HapiSysUndelete file(String target) {
        file = Optional.of(target);
        return this;
    }

    public HapiSysUndelete contract(String target) {
        contract = Optional.of(target);
        return this;
    }

    @Override
    protected HapiSysUndelete self() {
        return this;
    }

    @Override
    public HederaFunctionality type() {
        return SystemUndelete;
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(HapiSpec spec) throws Throwable {
        if (file.isPresent() && contract.isPresent()) {
            Assertions.fail("Ambiguous SystemUndelete---both file and contract present!");
        }
        SystemUndeleteTransactionBody opBody =
                spec.txns()
                        .<SystemUndeleteTransactionBody, SystemUndeleteTransactionBody.Builder>body(
                                SystemUndeleteTransactionBody.class,
                                b -> {
                                    file.ifPresent(n -> b.setFileID(asFileId(n, spec)));
                                    contract.ifPresent(n -> b.setContractID(asContractId(n, spec)));
                                });
        return b -> b.setSystemUndelete(opBody);
    }

    @Override
    protected Function<Transaction, TransactionResponse> callToUse(HapiSpec spec) {
        if (file.isPresent()) {
            return spec.clients().getFileSvcStub(targetNodeFor(spec), useTls)::systemUndelete;
        } else {
            return spec.clients().getScSvcStub(targetNodeFor(spec), useTls)::systemUndelete;
        }
    }

    @Override
    protected long feeFor(HapiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
        return spec.fees().maxFeeTinyBars();
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        MoreObjects.ToStringHelper helper = super.toStringHelper();
        file.ifPresent(n -> helper.add("file", n));
        contract.ifPresent(n -> helper.add("contract", n));
        return helper;
    }
}
