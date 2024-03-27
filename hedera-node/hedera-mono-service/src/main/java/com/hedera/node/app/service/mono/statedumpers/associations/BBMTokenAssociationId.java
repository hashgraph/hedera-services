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

package com.hedera.node.app.service.mono.statedumpers.associations;

import com.google.common.collect.ComparisonChain;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.statedumpers.utils.Writer;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import edu.umd.cs.findbugs.annotations.NonNull;

public record BBMTokenAssociationId(long accountId, long tokenId) implements Comparable<BBMTokenAssociationId> {
    public static BBMTokenAssociationId fromMono(@NonNull final EntityNumVirtualKey tokenRel) {
        final var key =
                BBMTokenAssociation.toLongsPair(BBMTokenAssociation.toPair(new EntityNumPair(tokenRel.getKeyAsLong())));
        return new BBMTokenAssociationId(key.left(), key.right());
    }

    @Override
    public String toString() {
        return "%d%s%d".formatted(accountId, Writer.FIELD_SEPARATOR, tokenId);
    }

    @Override
    public int compareTo(BBMTokenAssociationId o) {
        return ComparisonChain.start()
                .compare(this.accountId, o.accountId)
                .compare(this.tokenId, o.tokenId)
                .result();
    }
}
