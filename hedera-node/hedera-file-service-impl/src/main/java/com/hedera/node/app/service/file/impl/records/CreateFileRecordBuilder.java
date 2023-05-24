/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.file.impl.records;

import com.hedera.node.app.spi.records.UniversalRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A {@code RecordBuilder} specialization for tracking the side-effects of a
 * {@code CreateFile} transaction.
 */
public class CreateFileRecordBuilder extends UniversalRecordBuilder<CreateFileRecordBuilder> {
    private long createdFileNum = 0;

    @Override
    protected CreateFileRecordBuilder self() {
        return this;
    }

    /**
     * Tracks creation of a new file by number.
     *
     * @param num the number of the new file
     * @return this builder
     */
    @NonNull
    public CreateFileRecordBuilder setCreatedFile(final long num) {
        this.createdFileNum = num;
        return this;
    }

    /**
     * Returns the number of the created file.
     *
     * @return the number of the created file
     */
    public long getCreatedFile() {
        throwIfMissingFileNum();
        return createdFileNum;
    }

    private void throwIfMissingFileNum() {
        if (createdFileNum == 0L) {
            throw new IllegalStateException("No new file number was recorded");
        }
    }
}
