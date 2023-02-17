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
import java.util.List;
import org.junit.jupiter.api.Assertions;

public class BlockNoValidator implements RecordStreamValidator {
    @Override
    public void validateFiles(final List<RecordStreamFile> files) {
        var precedingBlockNo = 0L;
        for (final var file : files) {
            final var blockNo = file.getBlockNumber();
            Assertions.assertEquals(
                    precedingBlockNo + 1,
                    blockNo,
                    String.format(
                            "Block number %d is not the successor of preceding block number %d",
                            blockNo, precedingBlockNo));
            precedingBlockNo = blockNo;
        }
    }
}
