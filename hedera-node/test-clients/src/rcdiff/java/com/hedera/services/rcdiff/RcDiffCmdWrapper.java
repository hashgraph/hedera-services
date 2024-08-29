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

import static picocli.CommandLine.Command;
import static picocli.CommandLine.Model;
import static picocli.CommandLine.Option;
import static picocli.CommandLine.ParameterException;
import static picocli.CommandLine.Spec;

import com.hedera.services.bdd.utils.RcDiff;
import java.util.concurrent.Callable;
import picocli.CommandLine;

@Command(name = "rcdiff", description = "Diffs two record streams")
public class RcDiffCmdWrapper implements Callable<Integer> {
    @Spec
    Model.CommandSpec spec;

    @Option(
            names = {"-m", "--max-diffs-to-export"},
            paramLabel = "max diffs to export",
            defaultValue = "10")
    Long maxDiffsToExport;

    @Option(
            names = {"-l", "--len-of-diff-secs"},
            paramLabel = "number of seconds to diff at a time",
            defaultValue = "300")
    Long lenOfDiffSecs;

    @Option(
            names = {"-e", "--expected-stream"},
            paramLabel = "location of expected stream files")
    String expectedStreamsLoc;

    @Option(
            names = {"-a", "--actual-stream"},
            paramLabel = "location of actual stream files")
    String actualStreamsLoc;

    @Option(
            names = {"-d", "--diffs"},
            paramLabel = "location of diffs file",
            defaultValue = "diffs.txt")
    String diffsLoc;

    public static void main(String... args) {
        int rc = new CommandLine(new RcDiffCmdWrapper()).execute(args);
        System.exit(rc);
    }

    @Override
    public Integer call() throws Exception {
        try {
            return RcDiff.fromDirs(maxDiffsToExport, lenOfDiffSecs, expectedStreamsLoc, actualStreamsLoc, diffsLoc)
                    .call();
        } catch (IllegalArgumentException e) {
            throw new ParameterException(spec.commandLine(), e.getMessage(), e);
        }
    }
}
