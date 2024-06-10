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

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractNativeSystemContract.FUNCTION_SELECTOR_LENGTH;

import com.hedera.hapi.node.base.AccountID;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.code.CodeFactory;

/**
 * A concrete subclass of {@link AbstractProxyEvmAccount} that represents a contract account.
 *
 * Responsible for retrieving the redirectForAccount proxy contract byte code from {@link EvmFrameState}
 * if the function selector is eligible for proxy redirection.
 * Otherwise, it returns the 0x bytecode.
 *
 */
public class ProxyEvmAccount extends AbstractProxyEvmAccount {

    /*
     * Four byte function selectors for the two functions that are eligible for proxy redirection
     * in the Hedera Account Service system contract
     */
    private static final Set<Integer> ACCOUNT_PROXY_FUNCTION_SELECTOR = Set.of(
            // hbarAllowance(address spender)
            0xbbee989e,
            // hbarApprove(address spender, int256 amount)
            0x86aff07c);

    // Only pass in a non-null account address if the function selector is eligible for proxy redirection.
    // A null address will return the 0x bytecode.
    @Nullable
    private Address address;

    public ProxyEvmAccount(final AccountID accountID, @NonNull final EvmFrameState state) {
        super(accountID, state);
    }

    @Override
    public @NonNull Code getEvmCode(@NonNull final Bytes functionSelector) {
        // Check to see if the account needs to return the proxy redirect for account bytecode
        final int selector = functionSelector.size() >= FUNCTION_SELECTOR_LENGTH ? functionSelector.getInt(0) : 0;
        if (ACCOUNT_PROXY_FUNCTION_SELECTOR.contains(selector)) {
            address = state.getAddress(accountID);
        }
        return CodeFactory.createCode(getCode(), 0, false);
    }

    @Override
    public @NonNull Bytes getCode() {
        return state.getAccountRedirectCode(address);
    }

    @Override
    public @NonNull Hash getCodeHash() {
        return state.getAccountRedirectCodeHash(address);
    }
}
