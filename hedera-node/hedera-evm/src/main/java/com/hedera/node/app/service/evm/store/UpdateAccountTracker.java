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

package com.hedera.node.app.service.evm.store;

import org.hyperledger.besu.datatypes.Address;

/**
 * When an account's balance is changed by an EVM call, it is necessary to ensure that any Hedera system contract running
 * within the same frame can immediately see the change. This means that waiting for commit() to be called on a WorldUpdater
 * is not an option; instead, the "parallel" Hedera world state must be updated immediately.
 *
 * <p>This class defines the callback mechanism that a mutated account uses to report its balance changes to the Hedera
 * world state when executing an EVM transaction on a consensus node. Accounts that are "mutated" during an eth_estimateGas
 * call running on a mirror node do not actually change, so a null implementation of the callback can be provided in such cases.
 * */
public interface UpdateAccountTracker {

    void setBalance(final Address accountAddess, final long balance);
}
