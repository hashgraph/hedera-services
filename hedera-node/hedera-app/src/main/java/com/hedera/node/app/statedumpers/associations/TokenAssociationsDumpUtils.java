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

import static com.hedera.node.app.service.mono.statedumpers.associations.TokenAssociationsDumpUtils.reportOnTokenAssociations;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.statedumpers.DumpCheckpoint;
import com.hedera.node.app.service.mono.statedumpers.associations.BBMTokenAssociation;
import com.hedera.node.app.service.mono.statedumpers.associations.BBMTokenAssociationId;
import com.hedera.node.app.service.mono.statedumpers.utils.Writer;
import com.hedera.node.app.state.merkle.disk.OnDiskKey;
import com.hedera.node.app.state.merkle.disk.OnDiskValue;
import com.swirlds.base.utility.Pair;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

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
            VirtualMapLike.from(source)
                    .extractVirtualMapData(
                            getStaticThreadManager(),
                            p -> BBMTokenAssociations.add(
                                    Pair.of(fromModIdPair(p.left().getKey()), fromMod(p.right()))),
                            threadCount);
        } catch (final InterruptedException ex) {
            System.err.println("*** Traversal of token associations virtual map interrupted!");
            Thread.currentThread().interrupt();
        }
        BBMTokenAssociations.forEach(
                BBMTokenAssociationPair -> r.put(BBMTokenAssociationPair.key(), BBMTokenAssociationPair.value()));
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
}
