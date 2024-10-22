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

package com.hedera.node.app.workflows.handle;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.node.app.spi.records.RecordSource;
import com.hedera.node.app.state.recordcache.BlockRecordSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HandleOutputTest {
    @Mock
    private BlockRecordSource blockRecordSource;

    @Mock
    private RecordSource recordSource;

    @Test
    void throwsIfMissingRecordSourceWhenRequired() {
        final var subject = new HandleOutput(blockRecordSource, null);
        assertThrows(NullPointerException.class, subject::recordSourceOrThrow);
    }

    @Test
    void throwsIfMissingBlockRecordSourceWhenRequired() {
        final var subject = new HandleOutput(null, recordSource);
        assertThrows(NullPointerException.class, subject::blockRecordSourceOrThrow);
    }

    @Test
    void returnsRecordSourceWhenPresent() {
        final var subject = new HandleOutput(null, recordSource);
        assertEquals(recordSource, subject.recordSourceOrThrow());
    }

    @Test
    void returnsBlockRecordSourceWhenPresent() {
        final var subject = new HandleOutput(blockRecordSource, null);
        assertEquals(blockRecordSource, subject.blockRecordSourceOrThrow());
    }

    @Test
    void returnsBlockRecordSourceWhenPresentOtherwiseRecordSource() {
        final var withBlockSource = new HandleOutput(blockRecordSource, recordSource);
        assertEquals(blockRecordSource, withBlockSource.preferringBlockRecordSource());

        final var withoutBlockSource = new HandleOutput(null, recordSource);
        assertEquals(recordSource, withoutBlockSource.preferringBlockRecordSource());
    }
}
