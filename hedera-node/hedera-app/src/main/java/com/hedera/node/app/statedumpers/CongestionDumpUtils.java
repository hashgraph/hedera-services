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

package com.hedera.node.app.statedumpers;

import com.hedera.hapi.node.state.congestion.CongestionLevelStarts;
import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshots;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;

public class CongestionDumpUtils {
    public static void dumpModCongestion(
            @NonNull final Path path,
            @NonNull final CongestionLevelStarts congestionLevelStarts,
            @NonNull final ThrottleUsageSnapshots throttleUsageSnapshots, final JsonWriter jsonWriter) {
//        final var accountArr = gatherAccounts(accounts);
//        jsonWriter.write(congestionLevelStarts, path.toString());
//        System.out.printf("Accounts with size %d dumped at checkpoint %s%n", accountArr.length, checkpoint.name());
//        try (@NonNull final var writer = new Writer(path)) {
//            reportOnCongestion(writer, fromMod(congestionLevelStarts, throttleUsageSnapshots));
//            reportSize = writer.getSize();
//        }
//
//        System.out.printf("=== congestion report is %d bytes %n", reportSize);
    }
}
