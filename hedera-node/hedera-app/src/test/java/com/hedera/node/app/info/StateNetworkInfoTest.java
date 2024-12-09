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

package com.hedera.node.app.info;

import static com.hedera.node.app.workflows.standalone.TransactionExecutorsTest.getCertBytes;
import static com.hedera.node.app.workflows.standalone.TransactionExecutorsTest.randomX509Certificate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.platform.state.Address;
import com.hedera.hapi.platform.state.AddressBook;
import com.hedera.hapi.platform.state.NodeId;
import com.hedera.hapi.platform.state.PlatformState;
import com.hedera.node.app.service.addressbook.AddressBookService;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfigImpl;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.platform.state.service.PlatformStateService;
import com.swirlds.state.State;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableSingletonState;
import com.swirlds.state.spi.ReadableStates;
import java.security.cert.X509Certificate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class StateNetworkInfoTest {
    @Mock(strictness = LENIENT)
    private State state;

    @Mock
    private ConfigProvider configProvider;

    @Mock
    private ReadableKVState<EntityNumber, Node> nodeState;

    @Mock
    private ReadableStates readableStates;

    @Mock
    private ReadableSingletonState<PlatformState> platformReadableState;

    @Mock
    private PlatformState platformState;

    private static final long SELF_ID = 1L;
    private final Roster activeRoster = new Roster(List.of(
            RosterEntry.newBuilder().nodeId(SELF_ID).weight(10).build(),
            RosterEntry.newBuilder().nodeId(3L).weight(20).build()));

    private static final X509Certificate CERTIFICATE_2 = randomX509Certificate();
    private static final X509Certificate CERTIFICATE_3 = randomX509Certificate();

    private StateNetworkInfo networkInfo;

    @BeforeEach
    public void setUp() {
        when(configProvider.getConfiguration())
                .thenReturn(new VersionedConfigImpl(HederaTestConfigBuilder.createConfig(), 1));
        when(state.getReadableStates(AddressBookService.NAME)).thenReturn(readableStates);
        when(readableStates.<EntityNumber, Node>get("NODES")).thenReturn(nodeState);
        when(state.getReadableStates(PlatformStateService.NAME)).thenReturn(readableStates);
        networkInfo = new StateNetworkInfo(state, activeRoster, SELF_ID, configProvider);
    }

    @Test
    public void testLedgerId() {
        assertEquals("00", networkInfo.ledgerId().toHex());
    }

    @Test
    public void testSelfNodeInfo() {
        final var selfNode = networkInfo.selfNodeInfo();
        assertNotNull(selfNode);
        assertEquals(SELF_ID, selfNode.nodeId());
    }

    @Test
    public void testAddressBook() {
        final var addressBook = networkInfo.addressBook();
        assertNotNull(addressBook);
        assertEquals(2, addressBook.size());
    }

    @Test
    public void testNodeInfo() {
        final var nodeInfo = networkInfo.nodeInfo(SELF_ID);
        assertNotNull(nodeInfo);
        assertEquals(SELF_ID, nodeInfo.nodeId());
        assertNull(networkInfo.nodeInfo(999L));
    }

    @Test
    public void testContainsNode() {
        assertTrue(networkInfo.containsNode(SELF_ID));
        assertFalse(networkInfo.containsNode(999L));
    }

    @Test
    public void testUpdateFrom() {
        when(nodeState.get(any(EntityNumber.class))).thenReturn(mock(Node.class));
        when(readableStates.<PlatformState>getSingleton("PLATFORM_STATE")).thenReturn(platformReadableState);
        when(platformReadableState.get()).thenReturn(platformState);
        when(platformState.addressBook())
                .thenReturn(AddressBook.newBuilder()
                        .addresses(
                                Address.newBuilder()
                                        .id(new NodeId(2L))
                                        .weight(111L)
                                        .signingCertificate(getCertBytes(CERTIFICATE_2))
                                        // The agreementCertificate is unused, but required to prevent deserialization
                                        // failure in
                                        // States API.
                                        .agreementCertificate(getCertBytes(CERTIFICATE_2))
                                        .hostnameInternal("10.0.55.66")
                                        .portInternal(222)
                                        .build(),
                                Address.newBuilder()
                                        .id(new NodeId(3L))
                                        .weight(3L)
                                        .signingCertificate(getCertBytes(CERTIFICATE_3))
                                        // The agreementCertificate is unused, but required to prevent deserialization
                                        // failure in
                                        // States API.
                                        .agreementCertificate(getCertBytes(CERTIFICATE_3))
                                        .hostnameExternal("external3.com")
                                        .portExternal(111)
                                        .build())
                        .build());

        networkInfo.updateFrom(state);
        assertEquals(2, networkInfo.addressBook().size());
    }

    @Test
    public void testBuildNodeInfoMapNodeNotFound() {
        when(nodeState.get(any(EntityNumber.class))).thenReturn(null);

        StateNetworkInfo networkInfo = new StateNetworkInfo(state, activeRoster, SELF_ID, configProvider);
        final var nodeInfo = networkInfo.nodeInfo(SELF_ID);

        assertNotNull(nodeInfo);
        assertEquals(SELF_ID + 3, nodeInfo.accountId().accountNum());
    }
}
