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
package com.hedera.services.bdd.junit;

import static com.hedera.node.app.hapi.utils.exports.recordstreaming.RecordStreamingUtils.orderedRecordFilesFrom;
import static com.hedera.node.app.hapi.utils.exports.recordstreaming.RecordStreamingUtils.orderedSidecarFilesFrom;
import static com.hedera.node.app.hapi.utils.exports.recordstreaming.RecordStreamingUtils.parseRecordFileConsensusTime;
import static com.hedera.node.app.hapi.utils.exports.recordstreaming.RecordStreamingUtils.parseSidecarFileConsensusTimeAndSequenceNo;

import com.hedera.node.app.hapi.utils.exports.recordstreaming.RecordStreamingUtils;
import com.hedera.services.stream.proto.RecordStreamFile;
import com.hedera.services.stream.proto.SidecarFile;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class RecordStreamAccess {
    /**
     * Reads the record and sidecar stream files from a given directory.
     *
     * @param loc the directory to read from
     * @param relativeSidecarLoc the relative location of the sidecar files
     * @return the list of record and sidecar files
     * @throws IOException if there is an error reading the files
     */
    public List<RecordWithSidecars> readStreamFilesFrom(
            final String loc, final String relativeSidecarLoc) throws IOException {
        final var recordFiles = orderedRecordFilesFrom(loc);
        final var sidecarLoc = loc + File.separator + relativeSidecarLoc;
        final List<String> sidecarFiles;
        if (new File(sidecarLoc).exists()) {
            sidecarFiles = orderedSidecarFilesFrom(sidecarLoc);
        } else {
            sidecarFiles = List.of();
        }
        final var sidecarFilesByRecordFile =
                sidecarFiles.stream()
                        .collect(
                                Collectors.groupingBy(
                                        f ->
                                                parseSidecarFileConsensusTimeAndSequenceNo(f)
                                                        .getLeft(),
                                        Collectors.toList()));
        return recordFiles.stream()
                .map(
                        f ->
                                new RecordWithSidecars(
                                        ensurePresentRecordFile(f),
                                        sidecarFilesByRecordFile
                                                .getOrDefault(
                                                        parseRecordFileConsensusTime(f),
                                                        Collections.emptyList())
                                                .stream()
                                                .map(this::ensurePresentSidecarFile)
                                                .toList()))
                .toList();
    }

    private RecordStreamFile ensurePresentRecordFile(final String f) {
        try {
            final var contents = RecordStreamingUtils.readRecordStreamFile(f);
            if (contents.getRight().isEmpty()) {
                throw new IllegalArgumentException("No record found in " + f);
            }
            return contents.getRight().get();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read record stream file " + f, e);
        }
    }

    private SidecarFile ensurePresentSidecarFile(final String f) {
        try {
            return RecordStreamingUtils.readSidecarFile(f);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read record stream file " + f, e);
        }
    }
}
