/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.junit.support;

import com.hedera.services.stream.proto.RecordStreamFile;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.stream.Stream;

public interface RecordStreamValidator {
    default Stream<Throwable> validationErrorsIn(@NonNull final RecordStreamAccess.Data data) {
        try {
            validateFiles(data.files());
            validateRecordsAndSidecars(data.records());
        } catch (final Throwable t) {
            return Stream.of(t);
        }
        return Stream.empty();
    }

    default void validateFiles(List<RecordStreamFile> files) {
        // No-op
    }

    default void validateRecordsAndSidecars(List<RecordWithSidecars> records) {
        // No-op
    }
}
