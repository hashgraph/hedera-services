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

import com.google.common.collect.ComparisonChain;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenKey;
import com.hedera.node.app.service.mono.statedumpers.utils.Writer;
import edu.umd.cs.findbugs.annotations.NonNull;

public record BBMUniqueTokenId(long id, long serial) implements Comparable<BBMUniqueTokenId> {

    static BBMUniqueTokenId fromMono(@NonNull final UniqueTokenKey ukey) {
        return new BBMUniqueTokenId(ukey.getNum(), ukey.getTokenSerial());
    }

    @Override
    public String toString() {
        return "%d%s%d".formatted(id, Writer.FIELD_SEPARATOR, serial);
    }

    @Override
    public int compareTo(BBMUniqueTokenId o) {
        return ComparisonChain.start()
                .compare(this.id, o.id)
                .compare(this.serial, o.serial)
                .result();
    }
}
