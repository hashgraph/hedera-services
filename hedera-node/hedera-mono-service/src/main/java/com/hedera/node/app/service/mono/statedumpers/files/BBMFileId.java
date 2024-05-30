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

package com.hedera.node.app.service.mono.statedumpers.files;

import com.google.common.collect.ComparisonChain;
import com.hedera.hapi.node.base.FileID;
import com.hedera.node.app.service.mono.statedumpers.utils.Writer;
import edu.umd.cs.findbugs.annotations.NonNull;

public record BBMFileId(long shardNum, long realmNum, long fileNum) implements Comparable<BBMFileId> {

    public static BBMFileId fromMod(@NonNull final FileID fileID) {
        return new BBMFileId(fileID.shardNum(), fileID.realmNum(), fileID.fileNum());
    }

    static BBMFileId fromMono(@NonNull final Integer fileNum) {
        return new BBMFileId(0, 0, fileNum);
    }

    @Override
    public String toString() {
        return "%d%s%d%s%d".formatted(shardNum, Writer.FIELD_SEPARATOR, realmNum, Writer.FIELD_SEPARATOR, fileNum);
    }

    @Override
    public int compareTo(BBMFileId o) {
        return ComparisonChain.start()
                .compare(this.shardNum, o.shardNum)
                .compare(this.realmNum, o.realmNum)
                .compare(this.fileNum, o.fileNum)
                .result();
    }
}
