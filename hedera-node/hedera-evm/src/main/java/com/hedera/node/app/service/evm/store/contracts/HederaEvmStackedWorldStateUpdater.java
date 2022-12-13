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
package com.hedera.node.app.service.evm.store.contracts;

import com.hedera.node.app.service.evm.accounts.AccountAccessor;
import com.hedera.node.app.service.evm.store.models.UpdatedHederaEvmAccount;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.worldstate.UpdateTrackingAccount;
import org.hyperledger.besu.evm.worldstate.WrappedEvmAccount;

public class HederaEvmStackedWorldStateUpdater extends AbstractLedgerEvmWorldUpdater {

    protected final HederaEvmEntityAccess hederaEvmEntityAccess;

    public HederaEvmStackedWorldStateUpdater(
            AccountAccessor accountAccessor, HederaEvmEntityAccess hederaEvmEntityAccess) {
        super(accountAccessor);
        this.hederaEvmEntityAccess = hederaEvmEntityAccess;
    }

    @Override
    public EvmAccount getAccount(Address address) {
        final var balance = Wei.of(hederaEvmEntityAccess.getBalance(address));
        final var evmAccount =
                new UpdatedHederaEvmAccount(accountAccessor.canonicalAddress(address));
        evmAccount.setBalance(balance);

        return new WrappedEvmAccount(new UpdateTrackingAccount<>(evmAccount));
    }
}
