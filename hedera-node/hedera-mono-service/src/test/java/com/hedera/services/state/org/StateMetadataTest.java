/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.org;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.hedera.services.ServicesApp;
import com.hedera.services.utils.EntityNum;
import com.swirlds.fchashmap.FCHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StateMetadataTest {
    @Mock private ServicesApp app;
    @Mock private FCHashMap<ByteString, EntityNum> aliases;
    @Mock private FCHashMap<ByteString, EntityNum> copyAliases;

    private StateMetadata subject;

    @BeforeEach
    void setUp() {
        subject = new StateMetadata(app, aliases);
    }

    @Test
    void copyAsExpected() {
        given(aliases.copy()).willReturn(copyAliases);

        final var copy = subject.copy();

        assertSame(app, copy.app());
        assertSame(copyAliases, copy.aliases());
    }

    @Test
    void releasesAliasesOnRelease() {
        subject.release();

        verify(aliases).release();
    }

    @Test
    void doesntReleaseAlreadyReleasedAliasesOnRelease() {
        given(aliases.isDestroyed()).willReturn(true);

        subject.release();

        verify(aliases, never()).release();
    }

    @Test
    void releasesAliasesOnArchive() {
        subject.release();

        verify(aliases).release();
    }

    @Test
    void gettersWork() {
        // expect:
        assertSame(app, subject.app());
    }
}
