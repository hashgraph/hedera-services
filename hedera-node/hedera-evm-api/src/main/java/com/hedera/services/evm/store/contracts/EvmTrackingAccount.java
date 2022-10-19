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
package com.hedera.services.evm.store.contracts;

import org.hyperledger.besu.datatypes.Wei;

public class EvmTrackingAccount {
    private long nonce;
    private Wei balance;

    public EvmTrackingAccount(final long nonce, final Wei balance) {
        this.nonce = nonce;
        this.balance = balance;
    }

    public long getNonce() {
        return nonce;
    }

    public void setNonce(final long value) {
        this.nonce = value;
    }

    public Wei getBalance() {
        return balance;
    }

    public void setBalance(final Wei amount) {
        this.balance = amount;
    }
}
