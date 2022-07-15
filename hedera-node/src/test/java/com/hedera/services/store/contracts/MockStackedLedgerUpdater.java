/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store.contracts;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.ledger.accounts.ContractCustomizer;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

public class MockStackedLedgerUpdater
        extends AbstractStackedLedgerUpdater<HederaWorldState, Account> {
    private final ContractCustomizer customizer;

    public MockStackedLedgerUpdater(
            final AbstractLedgerWorldUpdater<HederaWorldState, Account> world,
            final WorldLedgers trackingLedgers,
            final ContractCustomizer customizer) {
        super(world, trackingLedgers);
        this.customizer = customizer;
    }

    @Override
    public ContractCustomizer customizerForPendingCreation() {
        return customizer;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public WorldUpdater updater() {
        return new MockStackedLedgerUpdater(
                (AbstractLedgerWorldUpdater) this,
                trackingLedgers().wrapped(new SideEffectsTracker()),
                customizer);
    }
}
