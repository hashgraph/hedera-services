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

package com.hedera.node.app.bbm.associations;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.bbm.utils.FieldBuilder;
import com.hedera.node.app.bbm.utils.ThingsToStrings;
import com.hedera.node.app.bbm.utils.Writer;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.statedumpers.DumpCheckpoint;
import com.hedera.node.app.state.merkle.disk.OnDiskKey;
import com.hedera.node.app.state.merkle.disk.OnDiskValue;
import com.swirlds.base.utility.Pair;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualValue;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TokenAssociationsDumpUtils {
    public static void dumpModTokenRelations(
            @NonNull final Path path,
            @NonNull
                    final VirtualMap<OnDiskKey<com.hedera.hapi.node.base.TokenAssociation>, OnDiskValue<TokenRelation>>
                            associations,
            @NonNull final DumpCheckpoint checkpoint) {
        try (@NonNull final var writer = new Writer(path)) {
            final var dumpableTokenRelations = gatherTokenRelations(
                    associations, key -> TokenAssociationId.fromMod(key.getKey()), TokenAssociation::fromMod);
            reportOnTokenAssociations(writer, dumpableTokenRelations);
            System.out.printf(
                    "=== mod token associations report is %d bytes at checkpoint %s%n",
                    writer.getSize(), checkpoint.name());
        }
    }

    @NonNull
    private static <K extends VirtualKey, V extends VirtualValue>
            Map<TokenAssociationId, TokenAssociation> gatherTokenRelations(
                    @NonNull final VirtualMap<K, V> source,
                    @NonNull final Function<K, TokenAssociationId> keyMapper,
                    @NonNull final Function<V, TokenAssociation> valueMapper) {
        final var r = new HashMap<TokenAssociationId, TokenAssociation>();
        final var threadCount = 8;
        final var tokenAssociations = new ConcurrentLinkedQueue<Pair<TokenAssociationId, TokenAssociation>>();
        try {
            VirtualMapLike.from(source)
                    .extractVirtualMapData(
                            getStaticThreadManager(),
                            p -> tokenAssociations.add(
                                    Pair.of(keyMapper.apply(p.left()), valueMapper.apply(p.right()))),
                            threadCount);
        } catch (final InterruptedException ex) {
            System.err.println("*** Traversal of token associations virtual map interrupted!");
            Thread.currentThread().interrupt();
        }
        tokenAssociations.forEach(
                tokenAssociationPair -> r.put(tokenAssociationPair.key(), tokenAssociationPair.value()));
        return r;
    }

    private static void reportOnTokenAssociations(
            @NonNull final Writer writer, @NonNull final Map<TokenAssociationId, TokenAssociation> associations) {
        writer.writeln(formatHeader());
        associations.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> formatTokenAssociation(writer, e.getValue()));
        writer.writeln("");
    }

    @NonNull
    private static String formatHeader() {
        return fieldFormatters.stream().map(Pair::left).collect(Collectors.joining(Writer.FIELD_SEPARATOR));
    }

    // spotless:off
    @NonNull
    private static final List<Pair<String, BiConsumer<FieldBuilder, TokenAssociation>>> fieldFormatters = List.of(
            Pair.of("accountId", getFieldFormatter(TokenAssociation::accountId, ThingsToStrings::toStringOfEntityId)),
            Pair.of("tokenId", getFieldFormatter(TokenAssociation::tokenId, ThingsToStrings::toStringOfEntityId)),
            Pair.of("balance", getFieldFormatter(TokenAssociation::balance, Object::toString)),
            Pair.of("isFrozen", getFieldFormatter(TokenAssociation::isFrozen, Object::toString)),
            Pair.of("isKycGranted", getFieldFormatter(TokenAssociation::isKycGranted, Object::toString)),
            Pair.of("isAutomaticAssociation", getFieldFormatter(TokenAssociation::isAutomaticAssociation, Object::toString)),
            Pair.of("prev", getFieldFormatter(TokenAssociation::prev, ThingsToStrings::toStringOfEntityId)),
            Pair.of("next", getFieldFormatter(TokenAssociation::next, ThingsToStrings::toStringOfEntityId))
    );
    // spotless:on

    @NonNull
    static <T> BiConsumer<FieldBuilder, TokenAssociation> getFieldFormatter(
            @NonNull final Function<TokenAssociation, T> fun, @NonNull final Function<T, String> formatter) {
        return (fb, u) -> formatField(fb, u, fun, formatter);
    }

    static <T> void formatField(
            @NonNull final FieldBuilder fb,
            @NonNull final TokenAssociation tokenAssociation,
            @NonNull final Function<TokenAssociation, T> fun,
            @NonNull final Function<T, String> formatter) {
        fb.append(formatter.apply(fun.apply(tokenAssociation)));
    }

    private static void formatTokenAssociation(
            @NonNull final Writer writer, @NonNull final TokenAssociation tokenAssociation) {
        final var fb = new FieldBuilder(Writer.FIELD_SEPARATOR);
        fieldFormatters.stream().map(Pair::right).forEach(ff -> ff.accept(fb, tokenAssociation));
        writer.writeln(fb);
    }
}
