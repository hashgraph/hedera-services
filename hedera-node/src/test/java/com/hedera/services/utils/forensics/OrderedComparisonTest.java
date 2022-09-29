package com.hedera.services.utils.forensics;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static com.hedera.services.utils.forensics.OrderedComparison.findDifferencesBetweenV6;
import static com.hedera.services.utils.forensics.OrderedComparison.statusHistograms;
import static com.hedera.services.utils.forensics.RecordParsers.parseV6RecordStreamEntriesIn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.EthereumTransaction;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.WRONG_NONCE;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderedComparisonTest {
    private static final Path STREAMS_DIR = Paths.get(
            "src", "test", "resources", "forensics", "CaseOfTheObviouslyWrongNonce");

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

        final var issStatuses =
                statusHistograms(parseV6RecordStreamEntriesIn(issStreamLoc));
        final var expectedEthTxnStatus = Map.of(INVALID_ACCOUNT_ID, 1, WRONG_NONCE, 31);
        assertEquals(issStatuses.get(EthereumTransaction), expectedEthTxnStatus);
    }
}