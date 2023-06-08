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

package com.hedera.node.app.service.mono.state.expiry.removal;

import com.hedera.node.app.service.mono.state.tasks.SystemTaskResult;
import com.hedera.node.app.service.mono.utils.EntityNum;

/** Provides the logic needed for the account and contract expiry and removal cycle */
public interface RemovalWork {
    /**
     * Tries to remove an expired account and returns {@code EntityProcessResult.DONE} if it is
     * successful. If the auto-removal of expiring accounts is not enabled, returns {@code
     * EntityProcessResult.NOTHING_TO_DO}
     *
     * @param account expired account
     * @return result for the successful removal
     */
    SystemTaskResult tryToRemoveAccount(EntityNum account);
    /**
     * Tries to remove an expired contract and returns {@code EntityProcessResult.DONE} if it is
     * successful. If the auto-removal of expiring contracts is not enabled, returns {@code
     * EntityProcessResult.NOTHING_TO_DO}
     *
     * @param contract expired contract
     * @return result for the successful removal
     */
    SystemTaskResult tryToRemoveContract(EntityNum contract);

    /**
     * Tries to mark a "detached" account as expired and pending removal. Setting this explicitly
     * makes it cheap and easy to see when a zero-balance account is <i>truly</i> detached, and not
     * simply in the window between its expiry and its auto-renewal. If the auto-removal of the
     * expiring account's type is not enabled, returns {@code EntityProcessResult.NOTHING_TO_DO}
     *
     * @param num an expired account that also had no usable auto-renew account funds
     * @param isContract whether this account is for a smart contract
     * @return result for marking the account as detached
     */
    SystemTaskResult tryToMarkDetached(EntityNum num, boolean isContract);
}
