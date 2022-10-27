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
package com.hedera.services.ledger.accounts;

import com.hedera.node.app.service.token.util.AliasUtils;
import org.hyperledger.besu.datatypes.Address;

public abstract class AbstractContractAliases implements ContractAliases {
    public static final int EVM_ADDRESS_LEN = 20;

    public boolean isMirror(final Address address) {
        return isMirror(address.toArrayUnsafe());
    }

    public boolean isMirror(final byte[] address) {
        return AliasUtils.isMirror(address);
    }
}
