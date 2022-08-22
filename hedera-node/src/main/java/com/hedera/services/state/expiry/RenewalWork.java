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
package com.hedera.services.state.expiry;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import javax.annotation.Nullable;
import java.time.Instant;

public interface RenewalWork {
    @Nullable MerkleAccount tryToGetNextMutableExpiryCandidate();
    MerkleAccount getMutableAutoRenewPayer();

    @Nullable MerkleAccount tryToGetNextExpiryCandidate();
    MerkleAccount getAutoRenewPayer();

    EntityProcessResult tryToRenewAccount(EntityNum account, final Instant cycleTime);
    EntityProcessResult tryToRenewContract(EntityNum contract, final Instant cycleTime);
}
