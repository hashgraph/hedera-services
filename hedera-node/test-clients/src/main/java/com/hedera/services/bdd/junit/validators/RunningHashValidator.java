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

package com.hedera.services.bdd.junit.validators;

import com.hedera.services.bdd.junit.RecordStreamValidator;
import com.hedera.services.stream.proto.RecordStreamFile;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import org.junit.jupiter.api.Assertions;

/**
 * This validator is intended to validate that, given a list of record files, that the end
 * running hash of the n-1 record is equal to the beginning running hash of the nth record
 */
public class RunningHashValidator implements RecordStreamValidator {
    @Override
    public void validateFiles(@NonNull final List<RecordStreamFile> files) {
        // Start at index 1 because we have nothing to compare the first file running hash to
        for (int i = 1; i < files.size(); i++) {
            final var endRunningHash = files.get(i - 1).getEndObjectRunningHash();
            final var startRunningHash = files.get(i).getStartObjectRunningHash();
            Assertions.assertEquals(endRunningHash, startRunningHash);
        }
    }
}
