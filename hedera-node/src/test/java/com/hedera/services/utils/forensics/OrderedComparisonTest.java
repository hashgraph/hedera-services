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
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileAppend;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.WRONG_NONCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.ByteString;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderedComparisonTest {
    private static final Path STREAMS_DIR =
            Paths.get("src", "test", "resources", "forensics", "CaseOfTheObviouslyWrongNonce");
    private static final TransactionRecord MOCK_RECORD = TransactionRecord.getDefaultInstance();
    private static final Instant THEN = Instant.ofEpochSecond(1_234_567, 890);
    private static final Instant NOW = Instant.ofEpochSecond(9_999_999, 001);

    @Mock private TxnAccessor aAccessor;
    @Mock private TxnAccessor bAccessor;

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
    }

    @Test
    void onlyEqualLengthsCanBeDiffed() {
        final var aEntry = new RecordStreamEntry(aAccessor, MOCK_RECORD, NOW);
        final var firstList = Collections.<RecordStreamEntry>emptyList();
        final var secondList = List.of(aEntry);
        assertThrows(
                IllegalArgumentException.class,
                () -> OrderedComparison.diff(firstList, secondList));
    }

    @Test
    void allTimestampsMustMatch() {
        final var aEntry = new RecordStreamEntry(aAccessor, MOCK_RECORD, THEN);
        final var bEntry = new RecordStreamEntry(bAccessor, MOCK_RECORD, NOW);
        final var firstList = List.of(aEntry);
        final var secondList = List.of(bEntry);
        assertThrows(
                IllegalArgumentException.class,
                () -> OrderedComparison.diff(firstList, secondList));
    }

    @Test
    void allTransactionsMustMatch() {
        final var aEntry = new RecordStreamEntry(aAccessor, MOCK_RECORD, THEN);
        final var bEntry = new RecordStreamEntry(bAccessor, MOCK_RECORD, THEN);
        final var aMockTxn = Transaction.getDefaultInstance();
        final var bMockTxn =
                Transaction.newBuilder()
                        .setSignedTransactionBytes(ByteString.copyFromUtf8("ABCDEFG"))
                        .build();
        given(aAccessor.getSignedTxnWrapper()).willReturn(aMockTxn);
        given(bAccessor.getSignedTxnWrapper()).willReturn(bMockTxn);
        final var firstList = List.of(aEntry);
        final var secondList = List.of(bEntry);
        assertThrows(
                IllegalArgumentException.class,
                () -> OrderedComparison.diff(firstList, secondList));
    }

    @Test
    void auxInvestigationMethodsWork() throws IOException {
        final var issStreamLoc = STREAMS_DIR + File.separator + "node5";
        final var entries = parseV6RecordStreamEntriesIn(issStreamLoc);

        final var histograms = statusHistograms(entries);
        final var expectedEthTxHist = Map.of(INVALID_ACCOUNT_ID, 1);
        assertEquals(expectedEthTxHist, histograms.get(HederaFunctionality.EthereumTransaction));

        final var fileAppends = OrderedComparison.filterByFunction(entries, FileAppend);
        assertEquals(3, fileAppends.size());
        final var appendTarget = fileAppends.get(0).body().getFileAppend().getFileID();
        assertEquals(48287857L, appendTarget.getFileNum());
    }
}
