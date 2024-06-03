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

import static com.hedera.node.app.spi.fixtures.state.TestSchema.CURRENT_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;

import com.hedera.node.app.service.networkadmin.impl.schemas.InitialModServiceNetworkSchema;
import com.hedera.node.app.spi.fixtures.util.LogCaptor;
import com.hedera.node.app.spi.fixtures.util.LogCaptureExtension;
import com.hedera.node.app.spi.fixtures.util.LoggingSubject;
import com.hedera.node.app.spi.fixtures.util.LoggingTarget;
import com.hedera.node.app.spi.state.MigrationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
public class InitialModServiceNetworkSchemaTest {

    @LoggingTarget
    private LogCaptor logCaptor;

    @Mock
    private MigrationContext migrationContext;

    @LoggingSubject
    private InitialModServiceNetworkSchema subject;

    @BeforeEach
    void setUp() {
        subject = new InitialModServiceNetworkSchema(CURRENT_VERSION);
    }

    @Test
    void registersExpectedSchema() {
        final var statesToCreate = subject.statesToCreate();
        assertThat(statesToCreate.size()).isEqualTo(0);
    }

    @Test
    void HappyPathMigration() {
        assertThatCode(() -> subject.migrate(migrationContext)).doesNotThrowAnyException();
        subject.migrate(migrationContext);
        assertThat(logCaptor.infoLogs()).contains("BBM: no actions required for network service");
    }
}
