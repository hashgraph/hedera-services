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

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.code.CodeFactory;

/**
 * An {@link Account} whose code proxies all calls to the {@code 0x16b} system contract, and thus can
 * never change its storage or nonce.
 *
 * <p>It also cannot have a non-zero balance, as dispatching a {@code transferValue()} with a schedule
 * address as receiver will always fail.
 *
 * <p>Despite this inherent immutability, for convenience still implements {@link MutableAccount} so
 * that instances can be used anywhere the Besu EVM needs a <i>potentially</i> mutable account.
 * Mutability should always turn out to be unnecessary in these cases, however; so the mutator methods
 * on this class do throw {@code UnsupportedOperationException} .
 */
public class ScheduleEvmAccount extends AbstractEvmEntityAccount {

    /*
     * Four byte function selectors for the functions that are eligible for proxy redirection
     * in the Hedera Schedule Service system contract
     */
    private static final Set<Integer> SCHEDULE_PROXY_FUNCTION_SELECTOR = Set.of(
            // signSchedule()
            0x06d15889,
            // getScheduledTransactionInfo()
            0x88af14e3);

    public ScheduleEvmAccount(@NonNull final Address address, @NonNull final EvmFrameState state) {
        super(address, state);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isScheduleTxnFacade() {
        return true;
    }

    @Override
    public Bytes getCode() {
        return state.getScheduleRedirectCode(address);
    }

    @Override
    public @NonNull Code getEvmCode(@NonNull final Bytes functionSelector) {
        // Check to see if the account needs to return the proxy redirect for schedule bytecode
        final int selector = functionSelector.size() >= FUNCTION_SELECTOR_LENGTH ? functionSelector.getInt(0) : 0;
        if (!SCHEDULE_PROXY_FUNCTION_SELECTOR.contains(selector)) {
            return CodeFactory.createCode(Bytes.EMPTY, 0, false);
        }
        return CodeFactory.createCode(getCode(), 0, false);
    }

    @Override
    public Hash getCodeHash() {
        return state.getScheduleRedirectCodeHash(address);
    }
}
