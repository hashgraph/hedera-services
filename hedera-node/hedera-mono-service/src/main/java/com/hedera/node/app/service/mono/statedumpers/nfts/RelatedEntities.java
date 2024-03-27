/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.statedumpers.nfts;

import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;

record RelatedEntities(long ownersNotTreasury, long ownedByTreasury, long spenders) {
    @NonNull
    static RelatedEntities countRelatedEntities(@NonNull final Map<BBMUniqueTokenId, BBMUniqueToken> uniques) {
        final var cs = new long[3];
        uniques.values().forEach(unique -> {
            if (null != unique.owner() && !unique.owner().equals(EntityId.MISSING_ENTITY_ID)) cs[0]++;
            if (null != unique.owner() && unique.owner().equals(EntityId.MISSING_ENTITY_ID)) cs[1]++;
            if (null != unique.spender() && !unique.spender().equals(EntityId.MISSING_ENTITY_ID)) cs[2]++;
        });
        return new RelatedEntities(cs[0], cs[1], cs[2]);
    }
}
