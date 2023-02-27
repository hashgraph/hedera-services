/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.store;

import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.accountIdFromEvmAddress;

import com.hedera.node.app.service.evm.store.UpdatedAccountTracker;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hederahashgraph.api.proto.java.AccountID;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.hyperledger.besu.datatypes.Address;

public class UpdatedAccountTrackerImpl implements UpdatedAccountTracker {
    private TransactionalLedger<AccountID, AccountProperty, HederaAccount> trackingAccounts;

    public UpdatedAccountTrackerImpl(
            @Nullable TransactionalLedger<AccountID, AccountProperty, HederaAccount> trackingAccounts) {
        this.trackingAccounts = trackingAccounts;
    }

    @Override
    public void setBalance(Address accountAddress, final long balance) {
        if (trackingAccounts != null) {
            trackingAccounts.set(accountIdFromEvmAddress(accountAddress), BALANCE, balance);
        }
    }

    public void updateTrackingAccounts(
            final TransactionalLedger<AccountID, AccountProperty, HederaAccount> trackingAccounts) {
        this.trackingAccounts = trackingAccounts;
    }
}
