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
package com.hedera.services.state.expiry.renewal;

import com.hedera.services.state.tasks.SystemTaskResult;
import com.hedera.services.utils.EntityNum;
import java.time.Instant;

/** Provides the logic needed for the account and contract auto-renewal cycle */
public interface RenewalWork {
    /**
     * Tries to renew an account and returns {@code EntityProcessResult.DONE} if it is successful.
     * If the auto-renewal for accounts is not enabled, returns {@code
     * EntityProcessResult.NOTHING_TO_DO}
     *
     * @param account to be renewed account
     * @param cycleTime consensus time for the current renewal cycle
     * @return result for the successful renewal
     */
    SystemTaskResult tryToRenewAccount(EntityNum account, final Instant cycleTime);

    /**
     * Tries to renew a contract and returns {@code EntityProcessResult.DONE} if it is successful.
     * If the auto-renewal for contracts is not enabled, returns {@code
     * EntityProcessResult.NOTHING_TO_DO}
     *
     * @param contract to be renewed contract
     * @param cycleTime consensus time for the current renewal cycle
     * @return result for the successful renewal
     */
    SystemTaskResult tryToRenewContract(EntityNum contract, final Instant cycleTime);
}
