/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.rcdiff;

import static com.hedera.node.app.hapi.utils.forensics.OrderedComparison.findDifferencesBetweenV6;

import com.hedera.node.app.hapi.utils.forensics.DifferingEntries;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;

@CommandLine.Command(name = "rcdiff", description = "Diffs two record streams")
public class RcDiff implements Callable<Integer> {
    public static void main(String... args) {
        int rc = new CommandLine(new RcDiff()).execute(args);
        System.exit(rc);
    }

    @CommandLine.Option(
            names = {"-e", "--expected-stream"},
            paramLabel = "location of expected stream files")
    String expectedStreamsLoc;

    @CommandLine.Option(
            names = {"-a", "--actual-stream"},
            paramLabel = "location of actual stream files")
    String actualStreamsLoc;

    @CommandLine.Option(
            names = {"-d", "--diffs"},
            paramLabel = "location of diffs file",
            defaultValue = "diffs.txt")
    String diffsLoc;

    @Override
    public Integer call() throws Exception {
        final var diffs = findDifferencesBetweenV6(expectedStreamsLoc, actualStreamsLoc, null);
        if (diffs.isEmpty()) {
            System.out.println("These streams are identical ☺️");
            return 0;
        } else {
            dumpDiffs(diffs);
            return 1;
        }
    }

    private void dumpDiffs(@NonNull final List<DifferingEntries> diffs) {
        try {
            Files.write(
                    Paths.get(diffsLoc), diffs.stream().map(this::readableDiff).toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String readableDiff(@NonNull final DifferingEntries diff) {
        final var firstEncounteredDifference = diff.firstEncounteredDifference();
        final var sb = new StringBuilder()
                .append("---- ")
                .append(diff.involvedFunctions())
                .append(" DIFFERED (")
                .append(firstEncounteredDifference)
                .append(") ----\n");
        if (diff.summary() != null) {
            sb.append(diff.summary()).append("\n");
        }
        return sb.toString();
    }
}
