/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.components;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.DaggerHederaApp;
import com.hedera.node.app.HederaApp;
import com.hedera.node.app.service.mono.context.properties.BootstrapProperties;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.platform.gui.SwirldsGui;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueryComponentTest {
    @Mock
    private Platform platform;

    private HederaApp app;

    @BeforeEach
    void setUp() {
        final var selfNodeId = new NodeId(false, 666L);

        app = DaggerHederaApp.builder()
                .platform(platform)
                .crypto(CryptographyHolder.get())
                .consoleCreator(SwirldsGui::createConsole)
                .staticAccountMemo("memo")
                .bootstrapProps(new BootstrapProperties())
                .selfId(selfNodeId.getId())
                .initialHash(new Hash())
                .maxSignedTxnSize(1024)
                .build();
    }

    @Test
    void objectGraphRootsAreAvailable() {
        given(platform.getSelfId()).willReturn(new NodeId(false, 0L));

        final QueryComponent subject = app.queryComponentFactory().get().create();

        assertNotNull(subject.queryWorkflow());
    }
}
