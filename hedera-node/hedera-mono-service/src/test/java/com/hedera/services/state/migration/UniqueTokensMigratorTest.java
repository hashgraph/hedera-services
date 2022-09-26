/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.migration;

import static com.google.common.truth.Truth.assertThat;
import static com.hedera.services.state.migration.StateChildIndices.UNIQUE_TOKENS;

import com.hedera.services.ServicesState;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.virtual.UniqueTokenKey;
import com.hedera.services.state.virtual.UniqueTokenValue;
import com.hedera.services.state.virtual.VirtualMapFactory;
import com.hedera.services.store.models.NftId;
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.jasperdb.JasperDbBuilder;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UniqueTokensMigratorTest {
    private ServicesState state;
    private MerkleMap<EntityNumPair, MerkleUniqueToken> legacyTokens = new MerkleMap<>();

    @BeforeEach
    void setUp() {
        state = new ServicesState();
        state.setChild(UNIQUE_TOKENS, legacyTokens);
    }

    @Test
    void givenEmptyDataSetToMigrate_properlyMigrated() {
        UniqueTokensMigrator.migrateFromUniqueTokenMerkleMap(state);
        VirtualMap<UniqueTokenKey, UniqueTokenValue> result = state.getChild(UNIQUE_TOKENS);
        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void givenDataSetWithVirtualMerkleData_properlyMigrated() {
        legacyTokens.put(
                EntityNumPair.fromNftId(
                        NftId.withDefaultShardRealm(0xFFFF_FFFFL, 0xFFFF_FFFFL - 1)),
                new MerkleUniqueToken(
                        EntityId.fromNum(0xFFFF_FFFFL),
                        "hello world".getBytes(),
                        RichInstant.fromJava(Instant.ofEpochSecond(3333L, 3L))));

        legacyTokens.put(
                EntityNumPair.fromNftId(
                        NftId.withDefaultShardRealm(0xFFFF_FFFFL - 3, 0xFFFF_FFFFL - 4)),
                new MerkleUniqueToken(
                        EntityId.fromNum(0xFEEE_EEEEL),
                        "hello 2".getBytes(),
                        RichInstant.fromJava(Instant.ofEpochSecond(4444L, 4L))));

        legacyTokens.put(
                EntityNumPair.fromNftId(
                        NftId.withDefaultShardRealm(0xFFFF_FFFFL - 5, 0xFFFF_FFFFL - 6)),
                new MerkleUniqueToken(
                        EntityId.fromNum(0xDEEE_EEEEL),
                        "hello 3".getBytes(),
                        RichInstant.fromJava(Instant.ofEpochSecond(5555L, 5L))));

        UniqueTokensMigrator.migrateFromUniqueTokenMerkleMap(state);

        VirtualMap<UniqueTokenKey, UniqueTokenValue> result = state.getChild(UNIQUE_TOKENS);
        assertThat(result.size()).isEqualTo(3);

        UniqueTokenValue token1 =
                result.get(
                        UniqueTokenKey.from(
                                NftId.withDefaultShardRealm(0xFFFF_FFFFL, 0xFFFF_FFFFL - 1)));
        UniqueTokenValue token2 =
                result.get(
                        UniqueTokenKey.from(
                                NftId.withDefaultShardRealm(0xFFFF_FFFFL - 3, 0xFFFF_FFFFL - 4)));
        UniqueTokenValue token3 =
                result.get(
                        UniqueTokenKey.from(
                                NftId.withDefaultShardRealm(0xFFFF_FFFFL - 5, 0xFFFF_FFFFL - 6)));

        // Verify token owners
        assertThat(token1.getOwner()).isEqualTo(new EntityId(0, 0, 0xFFFF_FFFFL));
        assertThat(token2.getOwner()).isEqualTo(new EntityId(0, 0, 0xFEEE_EEEEL));
        assertThat(token3.getOwner()).isEqualTo(new EntityId(0, 0, 0xDEEE_EEEEL));

        // Verify token metadata preserved
        assertThat(token1.getMetadata()).isEqualTo("hello world".getBytes());
        assertThat(token2.getMetadata()).isEqualTo("hello 2".getBytes());
        assertThat(token3.getMetadata()).isEqualTo("hello 3".getBytes());

        // Verify that time stamps preserved
        assertThat(token1.getCreationTime())
                .isEqualTo(RichInstant.fromJava(Instant.ofEpochSecond(3333L, 3L)));
        assertThat(token2.getCreationTime())
                .isEqualTo(RichInstant.fromJava(Instant.ofEpochSecond(4444L, 4L)));
        assertThat(token3.getCreationTime())
                .isEqualTo(RichInstant.fromJava(Instant.ofEpochSecond(5555L, 5L)));
    }

    @Test
    void givenDataAlreadyMigrated_noMigration() {
        UniqueTokensMigrator.migrateFromUniqueTokenMerkleMap(state);
        final var virtualMap =
                new VirtualMapFactory(JasperDbBuilder::new).newVirtualizedUniqueTokenStorage();
        state.setChild(UNIQUE_TOKENS, virtualMap);

        UniqueTokensMigrator.migrateFromUniqueTokenMerkleMap(state);
        VirtualMap<UniqueTokenKey, UniqueTokenValue> result = state.getChild(UNIQUE_TOKENS);
        assertThat(result.isEmpty()).isTrue();
        assertThat(result).isSameInstanceAs(virtualMap);
        virtualMap.release();
    }
}
