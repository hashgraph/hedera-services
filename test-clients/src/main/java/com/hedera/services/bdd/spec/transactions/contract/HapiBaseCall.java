/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.spec.transactions.contract;

import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.encodeParametersForCall;
import static com.hedera.services.bdd.suites.HapiSuite.SECP_256K1_SOURCE_KEY;

import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.swirlds.common.utility.CommonUtils;
import java.util.Optional;
import java.util.function.Supplier;

public abstract class HapiBaseCall<T extends HapiTxnOp<T>> extends HapiTxnOp<T> {

    public static final int HEXED_EVM_ADDRESS_LEN = 40;
    public static final String FALLBACK_ABI = "<empty>";
    protected boolean tryAsHexedAddressIfLenMatches = true;
    protected Object[] params;
    protected String abi;
    protected String contract;
    protected Optional<Long> gas = Optional.empty();
    protected Optional<Supplier<String>> explicitHexedParams = Optional.empty();
    protected String privateKeyRef = SECP_256K1_SOURCE_KEY;

    protected byte[] initializeCallData() {
        byte[] callData;
        if (explicitHexedParams.isPresent()) {
            callData = explicitHexedParams.map(Supplier::get).map(CommonUtils::unhex).orElseThrow();
        } else {
            callData = encodeParametersForCall(params, abi);
        }

        return callData;
    }
}
