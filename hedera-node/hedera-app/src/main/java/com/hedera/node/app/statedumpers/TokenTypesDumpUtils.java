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

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.node.app.hapi.utils.CommonPbjConverters;
import com.hedera.node.app.statedumpers.legacy.EntityId;
import com.hedera.node.app.statedumpers.legacy.FcCustomFee;
import com.hedera.node.app.statedumpers.legacy.JKey;
import com.hedera.node.app.statedumpers.tokentypes.BBMToken;
import com.hedera.node.app.statedumpers.utils.FieldBuilder;
import com.hedera.node.app.statedumpers.utils.LegacyTypeUtils;
import com.hedera.node.app.statedumpers.utils.ThingsToStrings;
import com.swirlds.base.utility.Pair;
import com.swirlds.state.merkle.disk.OnDiskKey;
import com.swirlds.state.merkle.disk.OnDiskValue;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualMapMigration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TokenTypesDumpUtils {
    public static void dumpModTokenType(
            @NonNull final Path path,
            @NonNull final VirtualMap<OnDiskKey<TokenID>, OnDiskValue<Token>> tokens,
            @NonNull final DumpCheckpoint checkpoint, final JsonWriter jsonWriter) {

        try (@NonNull final var writer = new Writer(path)) {
            final var allTokens = gatherTokensFromMod(tokens);
            dump(writer, allTokens);
            System.out.printf(
                    "=== mod tokens report is %d bytes at checkpoint %s%n", writer.getSize(), checkpoint.name());
        }
    }
}
