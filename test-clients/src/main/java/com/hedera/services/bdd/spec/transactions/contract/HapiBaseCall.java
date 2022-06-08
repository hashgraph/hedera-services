package com.hedera.services.bdd.spec.transactions.contract;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.bdd.spec.transactions.HapiTxnOp;

import java.util.Optional;

import static com.hedera.services.bdd.suites.HapiApiSuite.SECP_256K1_SOURCE_KEY;

public abstract class HapiBaseCall<T extends HapiTxnOp<T>> extends HapiTxnOp<T> {

    public static final int HEXED_EVM_ADDRESS_LEN = 40;
    protected static final String FALLBACK_ABI = "<empty>";
    protected boolean tryAsHexedAddressIfLenMatches = true;
    protected Object[] params;
    protected String abi;
    protected String contract;
    protected Optional<Long> gas = Optional.empty();
    protected String privateKeyRef = SECP_256K1_SOURCE_KEY;


}
