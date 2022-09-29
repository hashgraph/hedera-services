/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.utils.forensics;

import static com.hedera.services.utils.forensics.OrderedComparison.findDifferencesBetweenV6;
import static com.hedera.services.utils.forensics.OrderedComparison.statusHistograms;
import static com.hedera.services.utils.forensics.RecordParsers.parseV6RecordStreamEntriesIn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.EthereumTransaction;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.WRONG_NONCE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OrderedComparisonTest {
    private static final Path STREAMS_DIR =
            Paths.get("src", "test", "resources", "forensics", "CaseOfTheObviouslyWrongNonce");

    @Test
    void detectsDifferenceInCaseOfObviouslyWrongNonce() throws IOException {
        final var issStreamLoc = STREAMS_DIR + File.separator + "node5";
        final var consensusStreamLoc = STREAMS_DIR + File.separator + "node0";

        final var diffs = findDifferencesBetweenV6(issStreamLoc, consensusStreamLoc);
        assertEquals(1, diffs.size());
        final var soleDiff = diffs.get(0);
        final var issEntry = soleDiff.firstEntry();
        final var consensusEntry = soleDiff.secondEntry();

        final var issResolvedStatus = issEntry.finalStatus();
        final var consensusResolvedStatus = consensusEntry.finalStatus();
        assertEquals(INVALID_ACCOUNT_ID, issResolvedStatus);
        assertEquals(WRONG_NONCE, consensusResolvedStatus);

        final var issStatuses = statusHistograms(parseV6RecordStreamEntriesIn(issStreamLoc));
        final var expectedEthTxnStatus = Map.of(INVALID_ACCOUNT_ID, 1, WRONG_NONCE, 31);
        assertEquals(issStatuses.get(EthereumTransaction), expectedEthTxnStatus);
    }
}
