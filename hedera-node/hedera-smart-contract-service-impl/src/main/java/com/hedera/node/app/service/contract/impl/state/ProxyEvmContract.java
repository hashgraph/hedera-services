/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.state;

import com.hedera.hapi.node.base.AccountID;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.code.CodeFactory;

/**
 * A concrete subclass of {@link AbstractProxyEvmAccount} that represents a contract account.
 *
 * Responsible for retrieving the contract byte code from the {@link EvmFrameState}
 */
public class ProxyEvmContract extends AbstractProxyEvmAccount {

    public ProxyEvmContract(final AccountID accountID, @NonNull final EvmFrameState state) {
        super(accountID, state);
    }

    @Override
    public @NonNull Code getEvmCode(@NonNull final Bytes functionSelector) {
        return CodeFactory.createCode(getCode(), 0, false);
    }

    @Override
    public @NonNull Bytes getCode() {
        return state.getCode(hederaContractId());
    }

    @Override
    public @NonNull Hash getCodeHash() {
        return state.getCodeHash(hederaContractId());
    }
}
