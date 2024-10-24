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

package com.hedera.node.app.statedumpers.associations;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.statedumpers.DumpCheckpoint;
import com.hedera.node.app.statedumpers.legacy.EntityId;
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

public class TokenAssociationsDumpUtils {
    public static void dumpModTokenRelations(
            @NonNull final Path path,
            @NonNull final VirtualMap<OnDiskKey<EntityIDPair>, OnDiskValue<TokenRelation>> associations,
            @NonNull final DumpCheckpoint checkpoint) {
        try (@NonNull final var writer = new Writer(path)) {
            final var dumpableTokenRelations = gatherTokenRelations(associations);
            reportOnTokenAssociations(writer, dumpableTokenRelations);
            System.out.printf(
                    "=== mod token associations report is %d bytes at checkpoint %s%n",
                    writer.getSize(), checkpoint.name());
        }
    }

    @NonNull
    private static Map<BBMTokenAssociationId, BBMTokenAssociation> gatherTokenRelations(
            VirtualMap<OnDiskKey<EntityIDPair>, OnDiskValue<TokenRelation>> source) {
        final var r = new HashMap<BBMTokenAssociationId, BBMTokenAssociation>();
        final var threadCount = 8;
        final var BBMTokenAssociations = new ConcurrentLinkedQueue<Pair<BBMTokenAssociationId, BBMTokenAssociation>>();
        try {
            VirtualMapMigration.extractVirtualMapData(
                    getStaticThreadManager(),
                    source,
                    p -> BBMTokenAssociations.add(Pair.of(fromModIdPair(p.left().getKey()), fromMod(p.right()))),
                    threadCount);
        } catch (final InterruptedException ex) {
            System.err.println("*** Traversal of token associations virtual map interrupted!");
            Thread.currentThread().interrupt();
        }
        BBMTokenAssociations.forEach(pair -> r.put(pair.key(), pair.value()));
        return r;
    }

    private static BBMTokenAssociationId fromModIdPair(@NonNull final EntityIDPair pair) {
        return new BBMTokenAssociationId(
                pair.accountId().accountNum(), pair.tokenId().tokenNum());
    }

    private static BBMTokenAssociation fromMod(@NonNull final OnDiskValue<TokenRelation> wrapper) {
        final var value = wrapper.getValue();
        return new BBMTokenAssociation(
                accountIdFromMod(value.accountId()),
                tokenIdFromMod(value.tokenId()),
                value.balance(),
                value.frozen(),
                value.kycGranted(),
                value.automaticAssociation(),
                tokenIdFromMod(value.previousToken()),
                tokenIdFromMod(value.nextToken()));
    }

    public static EntityId accountIdFromMod(@Nullable final com.hedera.hapi.node.base.AccountID accountId) {
        return null == accountId ? EntityId.MISSING_ENTITY_ID : new EntityId(0L, 0L, accountId.accountNumOrThrow());
    }

    static EntityId tokenIdFromMod(@Nullable final com.hedera.hapi.node.base.TokenID tokenId) {
        return null == tokenId ? EntityId.MISSING_ENTITY_ID : new EntityId(0L, 0L, tokenId.tokenNum());
    }

    public static void reportOnTokenAssociations(
            @NonNull final Writer writer, @NonNull final Map<BBMTokenAssociationId, BBMTokenAssociation> associations) {
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
    private static final List<Pair<String, BiConsumer<FieldBuilder, BBMTokenAssociation>>> fieldFormatters = List.of(
            Pair.of("accountId", getFieldFormatter(BBMTokenAssociation::accountId, ThingsToStrings::toStringOfEntityId)),
            Pair.of("tokenId", getFieldFormatter(BBMTokenAssociation::tokenId, ThingsToStrings::toStringOfEntityId)),
            Pair.of("balance", getFieldFormatter(BBMTokenAssociation::balance, Object::toString)),
            Pair.of("isFrozen", getFieldFormatter(BBMTokenAssociation::isFrozen, Object::toString)),
            Pair.of("isKycGranted", getFieldFormatter(BBMTokenAssociation::isKycGranted, Object::toString)),
            Pair.of("isAutomaticAssociation", getFieldFormatter(BBMTokenAssociation::isAutomaticAssociation, Object::toString)),
            Pair.of("prev", getFieldFormatter(BBMTokenAssociation::prev, ThingsToStrings::toStringOfEntityId)),
            Pair.of("next", getFieldFormatter(BBMTokenAssociation::next, ThingsToStrings::toStringOfEntityId))
    );
    // spotless:on

    @NonNull
    static <T> BiConsumer<FieldBuilder, BBMTokenAssociation> getFieldFormatter(
            @NonNull final Function<BBMTokenAssociation, T> fun, @NonNull final Function<T, String> formatter) {
        return (fb, u) -> formatField(fb, u, fun, formatter);
    }

    static <T> void formatField(
            @NonNull final FieldBuilder fb,
            @NonNull final BBMTokenAssociation tokenAssociation,
            @NonNull final Function<BBMTokenAssociation, T> fun,
            @NonNull final Function<T, String> formatter) {
        fb.append(formatter.apply(fun.apply(tokenAssociation)));
    }

    private static void formatTokenAssociation(
            @NonNull final Writer writer, @NonNull final BBMTokenAssociation tokenAssociation) {
        final var fb = new FieldBuilder(Writer.FIELD_SEPARATOR);
        fieldFormatters.stream().map(Pair::right).forEach(ff -> ff.accept(fb, tokenAssociation));
        writer.writeln(fb);
    }
}
