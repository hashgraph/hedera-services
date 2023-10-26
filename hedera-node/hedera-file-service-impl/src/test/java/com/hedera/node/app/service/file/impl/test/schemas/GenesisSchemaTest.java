/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.file.impl.test.schemas;

import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.file.File;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.service.file.impl.schemas.GenesisSchema;
import com.hedera.node.app.spi.fixtures.info.FakeNetworkInfo;
import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import com.hedera.node.app.spi.fixtures.state.MapWritableStates;
import com.hedera.node.app.spi.fixtures.throttle.FakeHandleThrottleParser;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.state.EmptyReadableStates;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.throttle.HandleThrottleParser;
import com.hedera.node.app.workflows.handle.record.GenesisRecordsConsensusHook;
import com.hedera.node.app.workflows.handle.record.MigrationContextImpl;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class GenesisSchemaTest {
    private final ReadableStates prevStates = EmptyReadableStates.INSTANCE;
    private MapWritableStates newStates;
    private final NetworkInfo networkInfo = new FakeNetworkInfo();
    private final HandleThrottleParser handleThrottleParser = new FakeHandleThrottleParser();

    @BeforeEach
    void setUp() {
        newStates = MapWritableStates.builder()
                .state(MapWritableKVState.builder(FileServiceImpl.BLOBS_KEY).build())
                .build();
    }

    @DisplayName("Special update files are created with empty contents")
    @Test
    void emptyFilesCreatedForUpdateFiles() {
        // Given a file GenesisSchema, and a configuration setting for the range that is unique, so we can make
        // sure to verify that the code in question is using the config values, (and same for key and expiry)
        final var schema = new GenesisSchema();
        final var expiry = 1000;
        final var keyString = "0123456789012345678901234567890123456789012345678901234567890123";
        final var key = Key.newBuilder().ed25519(Bytes.wrap(unhex(keyString))).build();
        final var config = HederaTestConfigBuilder.create()
                .withValue("files.softwareUpdateRange", "151-158")
                .withValue("bootstrap.system.entityExpiry", expiry)
                .withValue("bootstrap.genesisPublicKey", keyString)
                .getOrCreateConfig();

        // When we migrate
        schema.migrate(new MigrationContextImpl(
                prevStates, newStates, config, networkInfo, new GenesisRecordsConsensusHook(), handleThrottleParser));

        // Then the new state has empty bytes for files 151-158 and proper values
        final var files = newStates.<FileID, File>get(FileServiceImpl.BLOBS_KEY);
        for (int i = 151; i <= 158; i++) {
            final var fileID = FileID.newBuilder().fileNum(i).build();
            final var file = files.get(fileID);
            assertThat(file).as("file %d is null", i).isNotNull();
            assertThat(file.contents()).as("file %d has non-empty contents", i).isEqualTo(Bytes.EMPTY);
            assertThat(file.deleted()).isFalse();
            assertThat(file.expirationSecond()).isEqualTo(expiry);
            assertThat(file.memo()).isEmpty();
            assertThat(file.hasKeys()).isTrue();
            assertThat(file.keysOrThrow().keysOrThrow()).containsExactly(key);
        }

        // And files outside of that range (but within the normal, default range!) do not exist
        assertThat(files.get(FileID.newBuilder().fileNum(150).build())).isNull();
        assertThat(files.get(FileID.newBuilder().fileNum(159).build())).isNull();
    }
}
