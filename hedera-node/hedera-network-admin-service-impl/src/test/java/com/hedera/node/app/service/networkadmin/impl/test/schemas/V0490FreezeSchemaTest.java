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

package com.hedera.node.app.service.networkadmin.impl.test.schemas;

import static com.hedera.node.app.service.networkadmin.impl.schemas.V0490FreezeSchema.FREEZE_TIME_KEY;
import static com.hedera.node.app.service.networkadmin.impl.schemas.V0490FreezeSchema.UPGRADE_FILE_HASH_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.networkadmin.impl.schemas.V0490FreezeSchema;
import com.swirlds.state.spi.MigrationContext;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableStates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class V0490FreezeSchemaTest {

    @Mock
    private MigrationContext migrationContext;

    @Mock
    private WritableStates writableStates;

    @Mock
    private WritableSingletonState upgradeFileHashKeyState;

    @Mock
    private WritableSingletonState freezeTimeKeyState;

    private V0490FreezeSchema subject;

    @BeforeEach
    void setUp() {
        subject = new V0490FreezeSchema();
    }

    @Test
    void registersExpectedSchema() {
        final var statesToCreate = subject.statesToCreate();
        assertThat(statesToCreate.size()).isEqualTo(2);
        //todo fix
//        final var iter =
//                statesToCreate.stream().map(
//                        (Function<StateDefinition, String>) stateDefinition -> "").sorted().iterator();
//        assertEquals(FREEZE_TIME_KEY, iter.next());
//        assertEquals(UPGRADE_FILE_HASH_KEY, iter.next());
    }

    @Test
    void setFSasExpectedAndHappyPathMigration() {
        V0490FreezeSchema.setFs(true);
        given(migrationContext.previousVersion()).willReturn(null);
        given(migrationContext.newStates()).willReturn(writableStates);
        given(writableStates.getSingleton(UPGRADE_FILE_HASH_KEY)).willReturn(upgradeFileHashKeyState);
        given(writableStates.getSingleton(FREEZE_TIME_KEY)).willReturn(freezeTimeKeyState);

        assertThatCode(() -> subject.migrate(migrationContext)).doesNotThrowAnyException();
    }
}
