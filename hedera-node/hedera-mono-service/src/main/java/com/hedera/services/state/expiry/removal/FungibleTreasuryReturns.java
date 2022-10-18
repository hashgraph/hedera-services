/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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

import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.EntityId;
import java.util.Collections;
import java.util.List;

public record FungibleTreasuryReturns(
        List<EntityId> tokenTypes, List<CurrencyAdjustments> transfers, boolean finished) {
    public static final FungibleTreasuryReturns FINISHED_NOOP_FUNGIBLE_RETURNS =
            new FungibleTreasuryReturns(Collections.emptyList(), Collections.emptyList(), true);
    public static final FungibleTreasuryReturns UNFINISHED_NOOP_FUNGIBLE_RETURNS =
            new FungibleTreasuryReturns(Collections.emptyList(), Collections.emptyList(), false);

    public int numReturns() {
        return tokenTypes.size();
    }
}
