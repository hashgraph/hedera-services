package com.hedera.services.store.contracts;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import com.hedera.services.store.contracts.world.HederaWorldState;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.worldstate.UpdateTrackingAccount;

public class HederaUpdateTrackingAccount extends UpdateTrackingAccount<HederaWorldState.WorldStateAccount> {

    public HederaUpdateTrackingAccount(HederaWorldState.WorldStateAccount account) {
        super(account);
    }

    @Override
    public void setBalance(Wei value) {
        super.setBalance(value);
    }

    @Override
    public Wei decrementBalance(final Wei value) {
        final Wei current = getBalance();
        if (current.compareTo(value) < 0) {
            throw new IllegalStateException(
                    String.format("Cannot remove %s wei from account, balance is only %s", value, current));
        }
        setBalance(current.subtract(value));
        return current;
    }
}
