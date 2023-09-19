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

package com.swirlds.platform.event.validation;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The contents of a CES file.
 *
 * @param beginningHash the running hash of the previous file
 * @param endingHash    the running hash of the current file
 * @param events        the events in the file
 * @param timestamp     the timestamp of the file
 * @param signature     the signature of the file, null if signature file is missing
 */
public record ConsensusEventStreamFileContents(
        @NonNull Hash beginningHash,
        @NonNull Hash endingHash,
        @NonNull List<EventImpl> events,
        @NonNull Instant timestamp,
        @Nullable Signature signature) {

    /**
     * Parse a CES file.
     *
     * @param path the path to the file
     * @return the contents of the file or null if the file is not a valid CES file
     */
    @Nullable
    public static ConsensusEventStreamFileContents parse(@NonNull final Path path) {
        Objects.requireNonNull(path);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("the file " + path + " does not exist");
        }

        // TODO

        return null;
    }
}
