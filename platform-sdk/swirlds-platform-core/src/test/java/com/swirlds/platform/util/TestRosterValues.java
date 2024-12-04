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

package com.swirlds.platform.util;

import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.List;

public class TestRosterValues {
    private static final String LOCALHOST = "localhost";
    private static final int PORT_1234 = 1234;
    private static final String EXTERNAL_HOST = "www.hashgraph.com";
    private static final int PORT_5678 = 5678;
    private static final byte[] BYTES_1_2_3_4 = {1, 2, 3, 4};
    private static final byte[] BYTES_5_6_7_8 = {5, 6, 7, 8};

    private TestRosterValues() {
        throw new IllegalArgumentException("Utility class");
    }

    public static final Node.Builder NODE_1 = Node.newBuilder()
            .nodeId(5)
            .weight(15)
            .gossipCaCertificate(Bytes.wrap(BYTES_1_2_3_4))
            .serviceEndpoint(List.of(
                    ServiceEndpoint.newBuilder()
                            .domainName(LOCALHOST)
                            .port(PORT_1234)
                            .build(),
                    ServiceEndpoint.newBuilder()
                            .domainName(EXTERNAL_HOST)
                            .port(PORT_5678)
                            .build()));
    public static final Node NODE_2 = Node.newBuilder()
            .nodeId(6)
            .weight(16)
            .gossipCaCertificate(Bytes.wrap(BYTES_5_6_7_8))
            .serviceEndpoint(List.of(
                    ServiceEndpoint.newBuilder()
                            .domainName(LOCALHOST + "2")
                            .port(4321)
                            .build(),
                    ServiceEndpoint.newBuilder()
                            .domainName(EXTERNAL_HOST + "2")
                            .port(8765)
                            .build()))
            .build();
    public static final Roster EXPECTED_ROSTER = Roster.newBuilder()
            .rosterEntries(
                    RosterEntry.newBuilder()
                            .nodeId(5)
                            .weight(15)
                            .gossipCaCertificate(Bytes.wrap(BYTES_1_2_3_4))
                            .gossipEndpoint(List.of(
                                    // External endpoint should be ordered first
                                    ServiceEndpoint.newBuilder()
                                            .domainName(EXTERNAL_HOST)
                                            .port(PORT_5678)
                                            .build(),
                                    ServiceEndpoint.newBuilder()
                                            .domainName(LOCALHOST)
                                            .port(PORT_1234)
                                            .build()))
                            .build(),
                    RosterEntry.newBuilder()
                            .nodeId(6)
                            .weight(16)
                            .gossipCaCertificate(Bytes.wrap(BYTES_5_6_7_8))
                            .gossipEndpoint(List.of(
                                    ServiceEndpoint.newBuilder()
                                            .domainName(EXTERNAL_HOST + "2")
                                            .port(8765)
                                            .build(),
                                    ServiceEndpoint.newBuilder()
                                            .domainName(LOCALHOST + "2")
                                            .port(4321)
                                            .build()))
                            .build())
            .build();
}
