/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import com.hedera.hapi.node.base.FileID;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A {@code StreamBuilder} specialization for tracking the side effects of a {@code CreateFile} transaction.
 */
public interface CreateFileStreamBuilder extends StreamBuilder {

    /**
     * Tracks creation of a new file by {@link FileID}.
     *
     * @param fileID the {@link FileID} of the new file
     * @return this builder
     */
    @NonNull
    CreateFileStreamBuilder fileID(@NonNull FileID fileID);
}
