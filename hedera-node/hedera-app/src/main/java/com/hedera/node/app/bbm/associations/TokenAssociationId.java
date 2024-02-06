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

package com.hedera.node.app.bbm.associations;

import static com.hedera.node.app.bbm.associations.TokenAssociation.toLongsPair;
import static com.hedera.node.app.bbm.associations.TokenAssociation.toPair;

import com.google.common.collect.ComparisonChain;
import com.hedera.node.app.bbm.utils.Writer;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskTokenRel;
import com.hedera.node.app.state.merkle.disk.OnDiskKey;
import edu.umd.cs.findbugs.annotations.NonNull;

record TokenAssociationId(long accountId, long tokenId) implements Comparable<TokenAssociationId> {
    static TokenAssociationId fromMod(@NonNull final com.hedera.hapi.node.base.TokenAssociation association) {
        return new TokenAssociationId(
                association.accountId().accountNum(), association.tokenId().tokenNum());
    }

    static TokenAssociationId fromMono(@NonNull final OnDiskKey<OnDiskTokenRel> tokenRel) {
        final var key = toLongsPair(toPair(tokenRel.getKey().getKey()));
        ;
        return new TokenAssociationId(key.left(), key.right());
    }

    @Override
    public String toString() {
        return "%d%s%d".formatted(accountId, Writer.FIELD_SEPARATOR, tokenId);
    }

    @Override
    public int compareTo(TokenAssociationId o) {
        return ComparisonChain.start()
                .compare(this.accountId, o.accountId)
                .compare(this.tokenId, o.tokenId)
                .result();
    }
}
