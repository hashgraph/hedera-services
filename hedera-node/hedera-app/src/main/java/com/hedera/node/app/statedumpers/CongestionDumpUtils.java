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
import com.hedera.node.app.statedumpers.utils.FieldBuilder;
import com.hedera.node.app.statedumpers.utils.ThingsToStrings;
import com.swirlds.base.utility.Pair;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CongestionDumpUtils {
    static final String FIELD_SEPARATOR = ";";

    @NonNull
    static List<Pair<String, BiConsumer<FieldBuilder, BBMCongestion>>> fieldFormatters = List.of(
            Pair.of(
                    "tpsThrottles",
                    getFieldFormatter(BBMCongestion::tpsThrottles, getNullableFormatter(Object::toString))),
            Pair.of(
                    "gasThrottle",
                    getFieldFormatter(BBMCongestion::gasThrottle, getNullableFormatter(Object::toString))),
            Pair.of(
                    "genericLevelStarts",
                    getFieldFormatter(BBMCongestion::genericLevelStarts, getNullableFormatter(Object::toString))),
            Pair.of(
                    "gasLevelStarts",
                    getFieldFormatter(BBMCongestion::gasLevelStarts, getNullableFormatter(Object::toString))));

    public static void dumpModCongestion(
            @NonNull final Path path,
            @NonNull final CongestionLevelStarts congestionLevelStarts,
            @NonNull final ThrottleUsageSnapshots throttleUsageSnapshots, final JsonWriter jsonWriter) {
        final var accountArr = gatherAccounts(accounts);
        jsonWriter.write(congestionLevelStarts, path.toString());
        System.out.printf("Accounts with size %d dumped at checkpoint %s%n", accountArr.length, checkpoint.name());
        try (@NonNull final var writer = new Writer(path)) {
            reportOnCongestion(writer, fromMod(congestionLevelStarts, throttleUsageSnapshots));
            reportSize = writer.getSize();
        }

        System.out.printf("=== congestion report is %d bytes %n", reportSize);
    }
}
