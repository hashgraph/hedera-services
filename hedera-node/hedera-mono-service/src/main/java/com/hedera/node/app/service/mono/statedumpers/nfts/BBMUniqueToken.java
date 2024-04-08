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
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenValue;
import com.hedera.node.app.service.mono.utils.NftNumPair;
import edu.umd.cs.findbugs.annotations.NonNull;

public record BBMUniqueToken(
        EntityId owner,
        EntityId spender,
        @NonNull RichInstant creationTime,
        @NonNull byte[] metadata,
        @NonNull NftNumPair previous,
        @NonNull NftNumPair next) {

    static final byte[] EMPTY_BYTES = new byte[0];

    static BBMUniqueToken fromMono(@NonNull final UniqueTokenValue utv) {
        return new BBMUniqueToken(
                utv.getOwner(),
                utv.getSpender(),
                utv.getCreationTime(),
                null != utv.getMetadata() ? utv.getMetadata() : EMPTY_BYTES,
                utv.getPrev(),
                utv.getNext());
    }
}
