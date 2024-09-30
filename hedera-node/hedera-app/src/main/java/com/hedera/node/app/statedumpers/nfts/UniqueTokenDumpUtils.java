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

package com.hedera.node.app.statedumpers.nfts;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.node.app.statedumpers.DumpCheckpoint;
import com.hedera.node.app.statedumpers.legacy.EntityId;
import com.hedera.node.app.statedumpers.legacy.NftNumPair;
import com.hedera.node.app.statedumpers.legacy.RichInstant;
import com.hedera.node.app.statedumpers.utils.FieldBuilder;
import com.hedera.node.app.statedumpers.utils.ThingsToStrings;
import com.hedera.node.app.statedumpers.utils.Writer;
import com.swirlds.base.utility.Pair;
import com.swirlds.state.merkle.vmapsupport.OnDiskKey;
import com.swirlds.state.merkle.vmapsupport.OnDiskValue;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualMapMigration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class UniqueTokenDumpUtils {
    public static void dumpModUniqueTokens(
            @NonNull final Path path,
            @NonNull final VirtualMap<OnDiskKey<NftID>, OnDiskValue<Nft>> uniques,
            @NonNull final DumpCheckpoint checkpoint) {
        try (@NonNull final var writer = new Writer(path)) {
            final var dumpableUniques = gatherUniques(uniques);
            reportOnUniques(writer, dumpableUniques);
            System.out.printf(
                    "=== mod  uniques report is %d bytes at checkpoint %s%n", writer.getSize(), checkpoint.name());
        }
    }

    @NonNull
    private static Map<BBMUniqueTokenId, BBMUniqueToken> gatherUniques(
            @NonNull final VirtualMap<OnDiskKey<NftID>, OnDiskValue<Nft>> source) {
        final var r = new HashMap<BBMUniqueTokenId, BBMUniqueToken>();
        final var threadCount = 5;
        final var mappings = new ConcurrentLinkedQueue<Pair<BBMUniqueTokenId, BBMUniqueToken>>();
        try {
            VirtualMapMigration.extractVirtualMapDataC(
                    getStaticThreadManager(),
                    source,
                    p -> mappings.add(Pair.of(fromMod(p.left().getKey()), fromMod(p.right()))),
                    threadCount);
        } catch (final InterruptedException ex) {
            System.err.println("*** Traversal of uniques virtual map interrupted!");
            Thread.currentThread().interrupt();
        }
        // Consider in the future: Use another thread to pull things off the queue as they're put on by the
        // virtual map traversal
        while (!mappings.isEmpty()) {
            final var mapping = mappings.poll();
            r.put(mapping.key(), mapping.value());
        }
        return r;
    }

    static BBMUniqueToken fromMod(@NonNull final OnDiskValue<Nft> wrapper) {
        final var value = wrapper.getValue();
        return new BBMUniqueToken(
                idFromMod(value.ownerId()),
                idFromMod(value.spenderId()),
                new RichInstant(value.mintTime().seconds(), value.mintTime().nanos()),
                value.metadata().toByteArray(),
                idPairFromMod(value.ownerPreviousNftId()),
                idPairFromMod(value.ownerNextNftId()));
    }

    private static EntityId idFromMod(@Nullable final AccountID accountId) {
        return null == accountId ? EntityId.MISSING_ENTITY_ID : new EntityId(0L, 0L, accountId.accountNumOrThrow());
    }

    private static NftNumPair idPairFromMod(@Nullable final NftID nftId) {
        return null == nftId
                ? NftNumPair.MISSING_NFT_NUM_PAIR
                : NftNumPair.fromLongs(nftId.tokenIdOrThrow().tokenNum(), nftId.serialNumber());
    }

    static BBMUniqueTokenId fromMod(@NonNull final NftID nftID) {
        return new BBMUniqueTokenId(nftID.tokenIdOrThrow().tokenNum(), nftID.serialNumber());
    }

    public static void reportOnUniques(
            @NonNull final Writer writer, @NonNull final Map<BBMUniqueTokenId, BBMUniqueToken> uniques) {
        writer.writeln(formatHeader());
        uniques.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> formatUnique(writer, e.getKey(), e.getValue()));
        writer.writeln("");
    }

    @NonNull
    private static String formatHeader() {
        return "nftId,nftSerial,"
                + fieldFormatters.stream().map(Pair::left).collect(Collectors.joining(Writer.FIELD_SEPARATOR));
    }

    // spotless:off
    @NonNull
    private static final List<Pair<String, BiConsumer<FieldBuilder, BBMUniqueToken>>> fieldFormatters = List.of(
            Pair.of("owner", getFieldFormatter(BBMUniqueToken::owner, ThingsToStrings::toStringOfEntityId)),
            Pair.of("spender", getFieldFormatter(BBMUniqueToken::spender, ThingsToStrings::toStringOfEntityId)),
            Pair.of("creationTime", getFieldFormatter(BBMUniqueToken::creationTime, ThingsToStrings::toStringOfRichInstant)),
            Pair.of("metadata", getFieldFormatter(BBMUniqueToken::metadata, ThingsToStrings.getMaybeStringifyByteString(Writer.FIELD_SEPARATOR))),
            Pair.of("prev", getFieldFormatter(BBMUniqueToken::previous, Object::toString)),
            Pair.of("next", getFieldFormatter(BBMUniqueToken::next, Object::toString))
    );
    // spotless:on

    @NonNull
    static <T> BiConsumer<FieldBuilder, BBMUniqueToken> getFieldFormatter(
            @NonNull final Function<BBMUniqueToken, T> fun, @NonNull final Function<T, String> formatter) {
        return (fb, u) -> formatField(fb, u, fun, formatter);
    }

    static <T> void formatField(
            @NonNull final FieldBuilder fb,
            @NonNull final BBMUniqueToken unique,
            @NonNull final Function<BBMUniqueToken, T> fun,
            @NonNull final Function<T, String> formatter) {
        fb.append(formatter.apply(fun.apply(unique)));
    }

    private static void formatUnique(
            @NonNull final Writer writer, @NonNull final BBMUniqueTokenId id, @NonNull final BBMUniqueToken unique) {
        final var fb = new FieldBuilder(Writer.FIELD_SEPARATOR);
        fb.append(id.toString());
        fieldFormatters.stream().map(Pair::right).forEach(ff -> ff.accept(fb, unique));
        writer.writeln(fb);
    }
}
