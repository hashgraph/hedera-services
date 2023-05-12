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

package com.hedera.services.bdd.spec.queries.contract;

import static com.hedera.services.bdd.spec.queries.QueryUtils.answerCostHeader;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerHeader;
import static com.hedera.services.bdd.spec.transactions.contract.HapiContractCall.HEXED_EVM_ADDRESS_LEN;
import static com.swirlds.common.utility.CommonUtils.unhex;

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.fee.FeeBuilder;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.ContractGetBytecodeQuery;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Transaction;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Assertions;

public class HapiGetContractBytecode extends HapiQueryOp<HapiGetContractBytecode> {
    private final String contract;
    private Optional<byte[]> expected = Optional.empty();
    private Optional<Consumer<byte[]>> bytecodeObs = Optional.empty();
    private Optional<String> saveResultToEntry = Optional.empty();
    private boolean hasExpectations = false;

    public HapiGetContractBytecode(String contract) {
        this.contract = contract;
    }

    public HapiGetContractBytecode isNonEmpty() {
        hasExpectations = true;
        return this;
    }

    public HapiGetContractBytecode hasBytecode(byte[] c) {
        expected = Optional.of(c);
        return this;
    }

    public HapiGetContractBytecode exposingBytecodeTo(Consumer<byte[]> obs) {
        bytecodeObs = Optional.of(obs);
        return this;
    }

    public HapiGetContractBytecode saveResultTo(String key) {
        saveResultToEntry = Optional.of(key);
        return this;
    }

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.ContractGetBytecode;
    }

    @Override
    protected HapiGetContractBytecode self() {
        return this;
    }

    @Override
    @SuppressWarnings("java:S5960")
    protected void assertExpectationsGiven(HapiSpec spec) throws Throwable {
        if (hasExpectations) {
            Assertions.assertFalse(
                    response.getContractGetBytecodeResponse().getBytecode().isEmpty(), "Empty " + "bytecode!");
        }
        expected.ifPresent(bytes -> Assertions.assertArrayEquals(
                bytes, response.getContractGetBytecodeResponse().getBytecode().toByteArray(), "Wrong bytecode!"));
    }

    @Override
    protected void submitWith(HapiSpec spec, Transaction payment) throws Throwable {
        Query query = getContractBytecodeQuery(spec, payment, false);
        response = spec.clients().getScSvcStub(targetNodeFor(spec), useTls).contractGetBytecode(query);

        final var code = response.getContractGetBytecodeResponse().getBytecode();
        saveResultToEntry.ifPresent(s -> spec.registry().saveBytes(s, code));
        bytecodeObs.ifPresent(obs -> obs.accept(code.toByteArray()));
    }

    @Override
    protected long lookupCostWith(HapiSpec spec, Transaction payment) throws Throwable {
        Query query = getContractBytecodeQuery(spec, payment, true);
        Response response =
                spec.clients().getScSvcStub(targetNodeFor(spec), useTls).contractGetBytecode(query);
        return costFrom(response);
    }

    private Query getContractBytecodeQuery(HapiSpec spec, Transaction payment, boolean costOnly) {
        final ContractID resolvedTarget;
        if (contract.length() == HEXED_EVM_ADDRESS_LEN) {
            resolvedTarget = ContractID.newBuilder()
                    .setEvmAddress(ByteString.copyFrom(unhex(contract)))
                    .build();
        } else {
            resolvedTarget = TxnUtils.asContractId(contract, spec);
        }
        ContractGetBytecodeQuery query = ContractGetBytecodeQuery.newBuilder()
                .setHeader(costOnly ? answerCostHeader(payment) : answerHeader(payment))
                .setContractID(resolvedTarget)
                .build();
        return Query.newBuilder().setContractGetBytecode(query).build();
    }

    @Override
    protected long costOnlyNodePayment(HapiSpec spec) {
        return spec.fees().forOp(HederaFunctionality.ContractGetBytecode, FeeBuilder.getCostForQueryByIDOnly());
    }

    @Override
    protected boolean needsPayment() {
        return true;
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        return super.toStringHelper().add("contract", contract);
    }
}
