/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import com.hedera.node.app.service.evm.store.models.UpdatedHederaEvmAccount;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.worldstate.WrappedEvmAccount;

public class HederaEvmStackedWorldStateUpdater
        extends AbstractEvmStackedLedgerUpdater<HederaEvmMutableWorldState, Account> {

    protected final HederaEvmEntityAccess hederaEvmEntityAccess;
    protected final TokenAccessor tokenAccessor;
    private final EvmProperties evmProperties;

    public HederaEvmStackedWorldStateUpdater(
            final AbstractLedgerEvmWorldUpdater<HederaEvmMutableWorldState, Account> updater,
            final AccountAccessor accountAccessor,
            final HederaEvmEntityAccess hederaEvmEntityAccess,
            final TokenAccessor tokenAccessor,
            final EvmProperties evmProperties) {
        super(updater, accountAccessor, hederaEvmEntityAccess);
        this.hederaEvmEntityAccess = hederaEvmEntityAccess;
        this.tokenAccessor = tokenAccessor;
        this.evmProperties = evmProperties;
    }

    public TokenAccessor tokenAccessor() {
        return tokenAccessor;
    }

    @Override
    public Account get(final Address address) {
        // TODO resolve for evm
        if (isTokenRedirect(address)) {
            return new HederaEvmWorldStateTokenAccount(address);
        }
        return super.get(address);

        //        final var accountBalance = Wei.of(hederaEvmEntityAccess.getBalance(address));
        //        final var account = new UpdatedHederaEvmAccount<>(address);
        //        account.setBalance(accountBalance);
        //        account.setEvmEntityAccess(hederaEvmEntityAccess);
        //
        //        return account;
    }

    @Override
    public EvmAccount getAccount(final Address address) {
        //        final address = resolveForEvm()
        // TODO resolve for evm -> in TokenAccessor add aliases()
        if (isTokenRedirect(address)) {
            final var proxyAccount = new HederaEvmWorldStateTokenAccount(address);
            final var newMutable = new UpdatedHederaEvmAccount<>(proxyAccount);
            newMutable.setEvmEntityAccess(hederaEvmEntityAccess);
            return new WrappedEvmAccount(newMutable);
        }

        return super.getAccount(address);
    }

    private boolean isTokenRedirect(final Address address) {
        return hederaEvmEntityAccess.isTokenAccount(address)
                && evmProperties.isRedirectTokenCallsEnabled();
    }
}
