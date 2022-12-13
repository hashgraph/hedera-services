/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.spec.utilops.streams;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static java.util.stream.Collectors.filtering;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.queries.file.HapiGetFileContents;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import com.hedera.services.bdd.spec.verification.NodeSignatureVerifier;
import com.hedera.services.bdd.spec.verification.RecordFileParser;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

public class RecordStreamVerification extends UtilOp {
    private static final Logger log = LogManager.getLogger(RecordStreamVerification.class);

    private static final byte[] EMPTY_HASH = new byte[48];

    private boolean allGood = true;
    private final Supplier<String> baseDir;

    public RecordStreamVerification(Supplier<String> baseDir) {
        this.baseDir = baseDir;
    }

    @Override
    protected boolean submitOp(HapiSpec spec) throws Throwable {
        var addressBook = downloadBook(spec);
        NodeSignatureVerifier verifier = new NodeSignatureVerifier(addressBook);
        Set<String> uniqRecordFiles = allRecordFilesFor(verifier.nodes());
        Map<String, List<File>> sigFilesAvail =
                uniqRecordFiles.stream()
                        .flatMap(
                                rcd ->
                                        verifier.nodes().stream()
                                                .map(
                                                        account ->
                                                                Path.of(
                                                                        recordsDirFor(account),
                                                                        rcd)))
                        .collect(
                                groupingBy(
                                        this::basename,
                                        mapping(
                                                (Path p) ->
                                                        Path.of(
                                                                        p.toFile().getAbsolutePath()
                                                                                + "_sig")
                                                                .toFile(),
                                                filtering(File::exists, toList()))));
        checkOverallValidity(sigFilesAvail, verifier);
        Assertions.assertTrue(
                allGood, "Not everything seemed good with the record streams, see logs above!");
        return false;
    }

    private void checkOverallValidity(
            Map<String, List<File>> sigFilesAvail, NodeSignatureVerifier verifier) {
        sigFilesAvail.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 0)
                .forEach(entry -> checkSigValidity(entry.getKey(), entry.getValue(), verifier));

        List<String> orderedRcdNames =
                sigFilesAvail.keySet().stream()
                        .map(s -> Instant.parse(s.substring(0, s.length() - 4).replace("_", ":")))
                        .sorted()
                        .map(Object::toString)
                        .map(s -> s.replace(":", "_") + ".rcd")
                        .collect(toList());
        log.info(
                "Record file running hashes will be checked in chronological order: {}",
                orderedRcdNames);

        for (String account : verifier.nodes()) {
            String recordsDir = recordsDirFor(account);
            List<RecordFileParser.RecordFile> recordFiles =
                    orderedRcdNames.stream()
                            .map(name -> Path.of(recordsDir, name).toFile())
                            .map(RecordFileParser::parseFrom)
                            .collect(toList());
            log.info("**** Running Hash Validation for {} Record Files ****", account);
            for (int i = 1; i < recordFiles.size(); i++) {
                byte[] actualPrevHash = recordFiles.get(i - 1).getThisHash();
                byte[] expectedPrevHash = recordFiles.get(i).getPrevHash();
                boolean same = Arrays.equals(expectedPrevHash, actualPrevHash);
                if (!same) {
                    if (Arrays.equals(EMPTY_HASH, expectedPrevHash)) {
                        log.warn(
                                "Record file '{}' had an EMPTY hash instead of "
                                        + "the running hash computed from the preceding '{}'",
                                orderedRcdNames.get(i),
                                orderedRcdNames.get(i - 1));
                    } else {
                        log.warn(
                                "Hash of node {} record file '{}' did NOT match "
                                        + "running hash saved in subsequent file '{}'!",
                                account,
                                orderedRcdNames.get(i - 1),
                                orderedRcdNames.get(i));
                    }
                } else {
                    log.info(
                            "Record file '{}' DID contain the running hash computed from the"
                                    + " preceding '{}'",
                            orderedRcdNames.get(i),
                            orderedRcdNames.get(i - 1));
                }
            }
        }
    }

    private void checkSigValidity(String record, List<File> sigs, NodeSignatureVerifier verifier) {
        int numAccounts = verifier.nodes().size();

        if (sigs.size() != numAccounts) {
            setUnGood(
                    "Record file {} had {} sigs ({}), not {} as expected!",
                    record,
                    sigs.size(),
                    sigs,
                    numAccounts);
        }

        List<String> majority = verifier.verifySignatureFiles(sigs);
        if (majority == null) {
            setUnGood(
                    "The nodes did not find majority agreement on the hash of record file '{}'!",
                    record);
        } else if (majority.size() < numAccounts) {
            setUnGood("Only {} agreed on the hash of record file '{}'!", majority, record);
        } else {
            log.info(
                    "All nodes had VALID signatures on the SAME hash for record file '{}'.",
                    record);
        }
    }

    private void setUnGood(String tpl, Object... varargs) {
        log.warn(tpl, varargs);
        allGood = false;
    }

    private Set<String> allRecordFilesFor(List<String> accounts) throws Exception {
        return accounts.stream()
                .map(this::recordsDirFor)
                .flatMap(this::uncheckedWalk)
                .filter(path -> path.toString().endsWith(".rcd"))
                .map(this::basename)
                .collect(toSet());
    }

    private String basename(Path p) {
        return p.getName(p.getNameCount() - 1).toString();
    }

    private Stream<Path> uncheckedWalk(String dir) {
        try {
            return Files.walk(Path.of(dir));
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private String recordsDirFor(String account) {
        return String.format("%s/record%s", baseDir.get(), account);
    }

    private NodeAddressBook downloadBook(HapiSpec spec) throws Exception {
        String addressBook = spec.setup().nodeDetailsName();
        HapiGetFileContents op = getFileContents(addressBook);
        allRunFor(spec, op);
        byte[] serializedBook =
                op.getResponse().getFileGetContents().getFileContents().getContents().toByteArray();
        return NodeAddressBook.parseFrom(serializedBook);
    }
}
