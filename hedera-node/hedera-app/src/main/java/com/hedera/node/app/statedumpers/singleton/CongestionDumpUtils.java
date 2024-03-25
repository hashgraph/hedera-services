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

package com.hedera.node.app.statedumpers.singleton;

import static com.hedera.node.app.service.mono.statedumpers.singleton.CongestionDumpUtils.reportOnCongestion;

import com.hedera.hapi.node.state.congestion.CongestionLevelStarts;
import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshots;
import com.hedera.node.app.service.mono.statedumpers.DumpCheckpoint;
import com.hedera.node.app.service.mono.statedumpers.singleton.BBMCongestion;
import com.hedera.node.app.service.mono.statedumpers.utils.ThingsToStrings;
import com.hedera.node.app.service.mono.statedumpers.utils.Writer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.stream.Collectors;

public class CongestionDumpUtils {
    public static void dumpModCongestion(
            @NonNull final Path path,
            @NonNull final CongestionLevelStarts congestionLevelStarts,
            @NonNull final ThrottleUsageSnapshots throttleUsageSnapshots,
            @NonNull final DumpCheckpoint checkpoint) {
        int reportSize;
        try (@NonNull final var writer = new Writer(path)) {
            reportOnCongestion(writer, fromMod(congestionLevelStarts, throttleUsageSnapshots));
            reportSize = writer.getSize();
        }

        System.out.printf("=== congestion report is %d bytes %n", reportSize);
    }

    static BBMCongestion fromMod(
            @NonNull final CongestionLevelStarts congestionLevelStarts,
            @NonNull final ThrottleUsageSnapshots throttleUsageSnapshots) {

        final var tpsThrottleUsageSnapshots = throttleUsageSnapshots.tpsThrottles();

        final var gasThrottleUsageSnapshot = throttleUsageSnapshots.gasThrottle();

        // format the following two from `List<RichInstant>` to String
        final var gasCongestionStarts = congestionLevelStarts.gasLevelStarts() != null
                ? congestionLevelStarts.gasLevelStarts().stream()
                        .map(ThingsToStrings::toStringOfTimestamp)
                        .collect(Collectors.joining(", "))
                : "";
        final var genericCongestionStarts = congestionLevelStarts.genericLevelStarts() != null
                ? congestionLevelStarts.genericLevelStarts().stream()
                        .map(ThingsToStrings::toStringOfTimestamp)
                        .collect(Collectors.joining(", "))
                : "";

        return new BBMCongestion(
                tpsThrottleUsageSnapshots, gasThrottleUsageSnapshot, genericCongestionStarts, gasCongestionStarts);
    }
}
