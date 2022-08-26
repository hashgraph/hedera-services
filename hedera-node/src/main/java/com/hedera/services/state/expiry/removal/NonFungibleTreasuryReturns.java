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

import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.NftAdjustments;
import java.util.Collections;
import java.util.List;

public record NonFungibleTreasuryReturns(
        List<EntityId> tokenTypes, List<NftAdjustments> exchanges, boolean finished) {
    public static final NonFungibleTreasuryReturns FINISHED_NOOP_NON_FUNGIBLE_RETURNS =
            new NonFungibleTreasuryReturns(Collections.emptyList(), Collections.emptyList(), true);
    public static final NonFungibleTreasuryReturns UNFINISHED_NOOP_NON_FUNGIBLE_RETURNS =
            new NonFungibleTreasuryReturns(Collections.emptyList(), Collections.emptyList(), false);

    public int numReturns() {
        return tokenTypes.size();
    }
}
