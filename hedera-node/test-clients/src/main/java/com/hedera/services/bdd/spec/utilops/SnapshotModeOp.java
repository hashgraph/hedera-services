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

package com.hedera.services.bdd.spec.utilops;

import static com.hedera.services.bdd.junit.RecordStreamAccess.RECORD_STREAM_ACCESS;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.GeneratedMessageV3;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.domain.EncodedItem;
import com.hedera.services.bdd.spec.utilops.domain.ParsedItem;
import com.hedera.services.bdd.spec.utilops.domain.RecordSnapshot;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

// too may parameters
@SuppressWarnings("java:S5960")
public class SnapshotModeOp extends UtilOp {
    static final Logger log = LogManager.getLogger(SnapshotModeOp.class);

    private static final String PLACEHOLDER_MEMO = "<entity-num-placeholder-creation>";
    private static final String MONO_STREAMS_LOC = "hedera-node/data/recordstreams/record0.0.3";
    private static final String HAPI_TEST_STREAMS_LOC = "hedera-node/test-clients/build/";
    private static final String SNAPSHOT_RESOURCES_LOC = "hedera-node/test-clients/record-snapshots";

    public enum SnapshotMode {
        TAKE_FROM_MONO_STREAMS,
        TAKE_FROM_HAPI_TEST_STREAMS,
        FUZZY_MATCH_AGAINST_MONO_STREAMS,
        FUZZY_MATCH_AGAINST_HAPI_TEST_STREAMS
    }

    private final SnapshotMode mode;

    private long placeholderAccountNum;
    private String recordsLoc;
    private String fullSpecName;
    private String placeholderMemo;
    private RecordSnapshot snapshotToMatchAgainst;

    public static void main(String... args) throws IOException {
        final var snapshotToDump = "CryptoTransfer-okToRepeatSerialNumbersInBurnList";
        final var snapshot = loadSnapshotFor(snapshotToDump);
        final var items = snapshot.parsedItems();
        for (int i = 0, n = items.size(); i < n; i++) {
            final var item = items.get(i);
            System.out.println("Item #" + i + " body: " + item.itemBody());
            System.out.println("Item #" + i + " record: " + item.itemRecord());
        }
    }

    public SnapshotModeOp(@NonNull final SnapshotMode mode) {
        this.mode = Objects.requireNonNull(mode);
        placeholderMemo = PLACEHOLDER_MEMO + Instant.now();
    }

    @Override
    protected boolean submitOp(@NonNull final HapiSpec spec) throws Throwable {
        this.fullSpecName = spec.getSuitePrefix() + "-" + spec.getName();
        switch (mode) {
            case TAKE_FROM_MONO_STREAMS -> computePlaceholderNum(MONO_STREAMS_LOC, spec);
            case TAKE_FROM_HAPI_TEST_STREAMS -> computePlaceholderNum(HAPI_TEST_STREAMS_LOC, spec);
            case FUZZY_MATCH_AGAINST_MONO_STREAMS -> prepToFuzzyMatchAgainstLoc(MONO_STREAMS_LOC, spec);
            case FUZZY_MATCH_AGAINST_HAPI_TEST_STREAMS -> prepToFuzzyMatchAgainstLoc(HAPI_TEST_STREAMS_LOC, spec);
        }
        return false;
    }

    public void finishLifecycle() {
        try {
            final var data = RECORD_STREAM_ACCESS.readStreamDataFrom(recordsLoc, "sidecar");
            final List<ParsedItem> postPlaceholderItems = new ArrayList<>();
            final var allItems = data.records().stream()
                    .flatMap(recordWithSidecars -> recordWithSidecars.recordFile().getRecordStreamItemsList().stream())
                    .toList();
            boolean placeholderFound = false;
            for (final var item : allItems) {
                final var parsedItem = ParsedItem.parse(item);
                final var body = parsedItem.itemBody();
                if (!placeholderFound) {
                    if (body.getMemo().equals(placeholderMemo)) {
                        log.info(
                                "Found placeholder account num 0.0.{} (expected {} from creation)",
                                parsedItem
                                        .itemRecord()
                                        .getReceipt()
                                        .getAccountID()
                                        .getAccountNum(),
                                placeholderAccountNum);
                        placeholderFound = true;
                    }
                } else {
                    postPlaceholderItems.add(parsedItem);
                }
            }
            switch (mode) {
                case TAKE_FROM_MONO_STREAMS, TAKE_FROM_HAPI_TEST_STREAMS -> writeSnapshotOf(postPlaceholderItems);
                case FUZZY_MATCH_AGAINST_MONO_STREAMS,
                        FUZZY_MATCH_AGAINST_HAPI_TEST_STREAMS -> fuzzyMatchAgainstSnapshot(postPlaceholderItems);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void fuzzyMatchAgainstSnapshot(@NonNull final List<ParsedItem> postPlaceholderItems) {
        final var itemsFromSnapshot = snapshotToMatchAgainst.parsedItems();
        final var minItems = Math.min(postPlaceholderItems.size(), itemsFromSnapshot.size());
        final var snapshotPlaceholderNum = snapshotToMatchAgainst.getPlaceholderNum();
        for (int i = 0; i < minItems; i++) {
            final var fromSnapshot = itemsFromSnapshot.get(i);
            final var fromStream = postPlaceholderItems.get(i);
            final var j = i;
            fuzzyMatch(
                    fromSnapshot.itemBody(),
                    snapshotPlaceholderNum,
                    fromStream.itemBody(),
                    placeholderAccountNum,
                    () -> "Item #" + j + " body mismatch (EXPECTED " + fromSnapshot.itemBody() + " ACTUAL "
                            + fromStream.itemBody() + ")");
            fuzzyMatch(
                    fromSnapshot.itemRecord(),
                    snapshotPlaceholderNum,
                    fromStream.itemRecord(),
                    placeholderAccountNum,
                    () -> "Item #" + j + " record mismatch (EXPECTED " + fromSnapshot.itemRecord() + " ACTUAL "
                            + fromStream.itemRecord() + ")");
        }
        if (postPlaceholderItems.size() != itemsFromSnapshot.size()) {
            Assertions.fail("Instead of " + itemsFromSnapshot.size() + " items, " + postPlaceholderItems.size()
                    + " were generated");
        }
    }

    private static final Set<String> FIELDS_TO_SKIP_IN_FUZZY_MATCH = Set.of(
            // These time-dependent fields will necessarily vary each test execution
            "expiry",
            "consensusTimestamp",
            "transactionValidStart",
            // And transaction hashes as well
            "transactionHash",
            // Keys are also regenerated every test execution
            "ed25519",
            "ECDSA_secp256k1",
            // Plus some other fields that we might prefer to make deterministic
            "symbol");

    /**
     * Given two messages, recursively asserts that they are equal up to certain "fuzziness" in values like timestamps,
     * hashes, and entity ids; since these quantities will vary based on the number of entities in the system and the
     * time at which the test is run.
     *
     * <p>Two {@link GeneratedMessageV3} messages are fuzzy-equal iff they have the same number of fields, where each
     * un-skipped primitive field matches exactly; and each un-skipped list field consists of fuzzy-equal elements.
     *
     * @param expectedMessage the expected message
     * @param expectedPlaceholderNum the placeholder number for the expected message
     * @param actualMessage the actual message
     * @param actualPlaceholderNum the placeholder number for the actual message
     * @param mismatchContext a supplier of a string that describes the context of the mismatch
     */
    private static void fuzzyMatch(
            @NonNull GeneratedMessageV3 expectedMessage,
            final long expectedPlaceholderNum,
            @NonNull GeneratedMessageV3 actualMessage,
            final long actualPlaceholderNum,
            @NonNull final Supplier<String> mismatchContext) {
        final var expectedType = expectedMessage.getClass();
        final var actualType = actualMessage.getClass();
        if (!expectedType.equals(actualType)) {
            Assertions.fail("Mismatched types between expected " + expectedType + " and " + actualType + " - "
                    + mismatchContext.get());
        }
        expectedMessage = normalized(expectedMessage, expectedPlaceholderNum);
        actualMessage = normalized(actualMessage, actualPlaceholderNum);
        // These are TreeMaps so ordering is deterministic
        final var expectedFields =
                new ArrayList<>(expectedMessage.getAllFields().entrySet());
        final var actualFields = new ArrayList<>(actualMessage.getAllFields().entrySet());
        if (expectedFields.size() != actualFields.size()) {
            Assertions.fail("Mismatched field counts between expected " + expectedMessage + " and " + actualMessage
                    + " - " + mismatchContext.get());
        }
        for (int i = 0, n = expectedFields.size(); i < n; i++) {
            final var expectedField = expectedFields.get(i);
            final var actualField = actualFields.get(i);
            final var expectedName = expectedField.getKey().getName();
            final var actualName = actualField.getKey().getName();
            if (!Objects.equals(expectedName, actualName)) {
                Assertions.fail(
                        "Mismatched field names ('" + expectedName + "' vs '" + actualName + "' between expected "
                                + expectedMessage + " and " + actualMessage + " - " + mismatchContext.get());
            }
            if (FIELDS_TO_SKIP_IN_FUZZY_MATCH.contains(expectedName)) {
                continue;
            }
            final var expectedValue = expectedField.getValue();
            final var actualValue = actualField.getValue();
            if (expectedValue instanceof List<?> expectedList) {
                if (actualValue instanceof List<?> actualList) {
                    if (expectedList.size() != actualList.size()) {
                        Assertions.fail("Mismatched list sizes between expected list " + expectedList + " and "
                                + actualList + " - " + mismatchContext.get());
                    }
                    for (int j = 0, m = expectedList.size(); j < m; j++) {
                        final var expectedElement = expectedList.get(j);
                        final var actualElement = actualList.get(j);
                        // There are no lists of lists in the record stream, so match values directly
                        matchValues(
                                expectedElement,
                                expectedPlaceholderNum,
                                actualElement,
                                actualPlaceholderNum,
                                mismatchContext);
                    }
                } else {
                    Assertions.fail("Mismatched types between expected list '" + expectedList + "' and "
                            + actualValue.getClass().getSimpleName() + " '" + actualValue + "' - "
                            + mismatchContext.get());
                }
            } else {
                matchValues(
                        expectedValue,
                        expectedPlaceholderNum,
                        actualValue,
                        actualPlaceholderNum,
                        () -> "Matching field '" + expectedName + "' " + mismatchContext.get());
            }
        }
    }

    private static void matchValues(
            @NonNull final Object expected,
            final long expectedPlaceholderNum,
            @NonNull final Object actual,
            final long actualPlaceholderNum,
            @NonNull final Supplier<String> mismatchContext) {
        if (expected instanceof GeneratedMessageV3 expectedMessage) {
            if (actual instanceof GeneratedMessageV3 actualMessage) {
                fuzzyMatch(
                        expectedMessage, expectedPlaceholderNum, actualMessage, actualPlaceholderNum, mismatchContext);
            } else {
                Assertions.fail("Mismatched types between expected message '" + expectedMessage + "' and "
                        + actual.getClass().getSimpleName() + " '" + actual + "' - " + mismatchContext.get());
            }
        } else {
            Assertions.assertEquals(
                    expected,
                    actual,
                    "Mismatched values '" + expected + "' vs '" + actual + "' - " + mismatchContext.get());
        }
    }

    private static GeneratedMessageV3 normalized(@NonNull final GeneratedMessageV3 message, final long placeholderNum) {
        if (message instanceof AccountID accountID) {
            final var normalizedNum = placeholderNum < accountID.getAccountNum()
                    ? accountID.getAccountNum() - placeholderNum
                    : accountID.getAccountNum();
            return accountID.toBuilder().setAccountNum(normalizedNum).build();
        } else if (message instanceof ContractID contractID) {
            final var normalizedNum = placeholderNum < contractID.getContractNum()
                    ? contractID.getContractNum() - placeholderNum
                    : contractID.getContractNum();
            return contractID.toBuilder().setContractNum(normalizedNum).build();
        } else if (message instanceof TopicID topicID) {
            final var normalizedNum = placeholderNum < topicID.getTopicNum()
                    ? topicID.getTopicNum() - placeholderNum
                    : topicID.getTopicNum();
            return topicID.toBuilder().setTopicNum(normalizedNum).build();
        } else if (message instanceof TokenID tokenID) {
            final var normalizedNum = placeholderNum < tokenID.getTokenNum()
                    ? tokenID.getTokenNum() - placeholderNum
                    : tokenID.getTokenNum();
            return tokenID.toBuilder().setTokenNum(normalizedNum).build();
        } else if (message instanceof FileID fileID) {
            final var normalizedNum =
                    placeholderNum < fileID.getFileNum() ? fileID.getFileNum() - placeholderNum : fileID.getFileNum();
            return fileID.toBuilder().setFileNum(normalizedNum).build();
        } else if (message instanceof ScheduleID scheduleID) {
            final var normalizedNum = placeholderNum < scheduleID.getScheduleNum()
                    ? scheduleID.getScheduleNum() - placeholderNum
                    : scheduleID.getScheduleNum();
            return scheduleID.toBuilder().setScheduleNum(normalizedNum).build();
        } else {
            return message;
        }
    }

    private void writeSnapshotOf(@NonNull final List<ParsedItem> postPlaceholderItems) throws IOException {
        final var recordSnapshot = new RecordSnapshot();
        recordSnapshot.setPlaceholderNum(placeholderAccountNum);
        final var encodedItems = postPlaceholderItems.stream()
                .map(item -> EncodedItem.fromParsed(item.itemBody(), item.itemRecord()))
                .toList();
        recordSnapshot.setEncodedItems(encodedItems);
        final var om = new ObjectMapper();
        final var outputLoc = resourceLocOf(fullSpecName);
        log.info("Writing snapshot of {} post-placeholder records to {}", encodedItems.size(), outputLoc);
        final var fout = Files.newOutputStream(outputLoc);
        om.writeValue(fout, recordSnapshot);
    }

    private static Path resourceLocOf(@NonNull final String specName) {
        return Paths.get(SNAPSHOT_RESOURCES_LOC, specName + ".json");
    }

    private void prepToFuzzyMatchAgainstLoc(@NonNull final String loc, @NonNull final HapiSpec spec)
            throws IOException {
        computePlaceholderNum(loc, spec);
        snapshotToMatchAgainst = loadSnapshotFor(fullSpecName);
        log.info(
                "Read {} post-placeholder records from snapshot",
                snapshotToMatchAgainst.getEncodedItems().size());
    }

    private static RecordSnapshot loadSnapshotFor(@NonNull final String specName) throws IOException {
        final var om = new ObjectMapper();
        final var inputLoc = resourceLocOf(specName);
        final var fin = Files.newInputStream(inputLoc);
        return om.reader().readValue(fin, RecordSnapshot.class);
    }

    private void computePlaceholderNum(@NonNull final String loc, @NonNull final HapiSpec spec) {
        recordsLoc = loc;
        final var placeholderCreation = cryptoCreate("PLACEHOLDER")
                .memo(placeholderMemo)
                .exposingCreatedIdTo(id -> this.placeholderAccountNum = id.getAccountNum())
                .noLogging();
        allRunFor(spec, placeholderCreation);
    }
}
