/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
import static com.hederahashgraph.api.proto.java.HederaFunctionality.SystemDelete;

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SystemDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.Assertions;

public class HapiSysDelete extends HapiTxnOp<HapiSysDelete> {
    private OptionalLong newExpiry = OptionalLong.empty();
    private Optional<String> file = Optional.empty();
    private Optional<String> contract = Optional.empty();

    public HapiSysDelete file(String target) {
        file = Optional.of(target);
        return this;
    }

    public HapiSysDelete contract(String target) {
        contract = Optional.of(target);
        return this;
    }

    public HapiSysDelete updatingExpiry(long to) {
        newExpiry = OptionalLong.of(to);
        return this;
    }

    @Override
    protected HapiSysDelete self() {
        return this;
    }

    @Override
    public HederaFunctionality type() {
        return SystemDelete;
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(HapiSpec spec) throws Throwable {
        if (file.isPresent() && contract.isPresent()) {
            Assertions.fail("Ambiguous SystemDelete---both file and contract present!");
        }
        SystemDeleteTransactionBody opBody =
                spec.txns()
                        .<SystemDeleteTransactionBody, SystemDeleteTransactionBody.Builder>body(
                                SystemDeleteTransactionBody.class,
                                b -> {
                                    newExpiry.ifPresent(
                                            l ->
                                                    b.setExpirationTime(
                                                            TimestampSeconds.newBuilder()
                                                                    .setSeconds(l)));
                                    file.ifPresent(n -> b.setFileID(asFileId(n, spec)));
                                    contract.ifPresent(n -> b.setContractID(asContractId(n, spec)));
                                });
        return b -> b.setSystemDelete(opBody);
    }

    @Override
    protected Function<Transaction, TransactionResponse> callToUse(HapiSpec spec) {
        if (file.isPresent()) {
            return spec.clients().getFileSvcStub(targetNodeFor(spec), useTls)::systemDelete;
        } else {
            return spec.clients().getScSvcStub(targetNodeFor(spec), useTls)::systemDelete;
        }
    }

    @Override
    protected long feeFor(HapiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
        return spec.fees().maxFeeTinyBars();
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        MoreObjects.ToStringHelper helper = super.toStringHelper();
        newExpiry.ifPresent(l -> helper.add("newExpiry", l));
        file.ifPresent(n -> helper.add("file", n));
        contract.ifPresent(n -> helper.add("contract", n));
        return helper;
    }
}
