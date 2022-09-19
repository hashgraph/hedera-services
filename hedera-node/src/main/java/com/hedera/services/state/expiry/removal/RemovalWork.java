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
package com.hedera.services.state.expiry.removal;

import com.hedera.services.state.expiry.ExpiryProcessResult;
import com.hedera.services.utils.EntityNum;

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
    ExpiryProcessResult tryToRemoveAccount(EntityNum account);
    /**
     * Tries to remove an expired contract and returns {@code EntityProcessResult.DONE} if it is
     * successful. If the auto-removal of expiring contracts is not enabled, returns {@code
     * EntityProcessResult.NOTHING_TO_DO}
     *
     * @param contract expired contract
     * @return result for the successful removal
     */
    ExpiryProcessResult tryToRemoveContract(EntityNum contract);
}
