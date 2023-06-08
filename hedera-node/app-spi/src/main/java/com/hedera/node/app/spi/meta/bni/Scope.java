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

package com.hedera.node.app.spi.meta.bni;

import com.hedera.node.app.spi.state.WritableStates;

/**
 * The unit of atomicity for all changes the {@code ContractService} can make, either directly
 * via its {@link WritableStates} or indirectly by {@link Dispatch} methods.
 *
 * <p>Each new {@link Scope} is a child of the previous {@link Scope}, and the parent
 * {@link Scope} absorbs all changes made when a child calls {@link Scope#commit()}.
 */
public interface Scope {
    /**
     * Returns the {@link WritableStates} the {@code ContractService} can use to update
     * its own state within this {@link Scope}.
     *
     * @return the contract state reflecting all changes made up to this {@link Scope}
     */
    WritableStates writableContractState();

    /**
     * Returns a {@link Dispatch} that reflects all changes made up to, and within, this
     * {@link Scope}. If a dispatch has side effects on state such as records or entity ids,
     * they remain limited to the scope of this session until the session is committed.
     *
     * @return a dispatch reflecting all changes made up to, and within, this {@link Scope}
     */
    Dispatch dispatch();

    /**
     * Returns the {@link Fees} that reflect all changes up to and including this {@link Scope}.
     *
     * @return the fees reflecting all changes made up to this {@link Scope}
     */
    Fees fees();

    /**
     * Commits all changes made within this {@link Scope} to the parent {@link Scope}. For
     * everything except records, these changes will only affect state if every ancestor up to
     * and including the root {@link Scope} is also committed. Records are a bit different,
     * as even if the root {@link Scope} reverts, any records created within this
     * {@link Scope} will still appear in state; but those with status {@code SUCCESS} will
     * have with their stateful effects cleared from the record and their status replaced with
     * {@code REVERTED_SUCCESS}.
     */
    void commit();

    /**
     * Reverts all changes and ends this session, with the possible exception of records, as
     * described above.
     */
    void revert();

    /**
     * Creates a new {@link Scope} that is a child of this {@link Scope}.
     *
     * @return a nested {@link Scope}
     */
    Scope begin();

    /**
     * Returns the account number of the Hedera account that is paying for the transaction.
     *
     * @return the payer account number
     */
    long payerAccountNumber();
}
