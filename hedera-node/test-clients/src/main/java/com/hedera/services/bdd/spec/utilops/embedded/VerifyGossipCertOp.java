/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.utilops.embedded;

import static com.hedera.node.app.service.addressbook.impl.schemas.V053AddressBookSchema.NODES_KEY;
import static com.hedera.services.bdd.spec.TargetNetworkType.EMBEDDED_NETWORK;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.node.app.hapi.utils.keys.RSAUtils;
import com.hedera.node.app.service.addressbook.AddressBookService;
import com.hedera.services.bdd.junit.hedera.embedded.AbstractEmbeddedHedera;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import com.swirlds.common.platform.NodeId;
import com.swirlds.state.spi.ReadableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;

public class VerifyGossipCertOp extends UtilOp {
    private final int nodeId;

    public VerifyGossipCertOp(final int nodeId) {
        this.nodeId = nodeId;
    }

    @Override
    protected boolean submitOp(@NonNull final HapiSpec spec) throws Throwable {
        if (spec.targetNetworkType() != EMBEDDED_NETWORK) {
            throw new IllegalStateException("This op is only compatible with embedded networks");
        }

        final var targetNetwork = ((AbstractEmbeddedHedera) spec.embeddedHederaOrThrow());
        final var readableStates = spec.embeddedStateOrThrow().getReadableStates(AddressBookService.NAME);

        final ReadableKVState<EntityNumber, Node> nodesState = readableStates.get(NODES_KEY);
        final var node = nodesState.get(EntityNumber.newBuilder().number(nodeId).build());
        final var actualCert =
                RSAUtils.parseCertificate(node.gossipCaCertificate().toByteArray());

        final var expectedAddr = targetNetwork.getAddressBook().getAddress(NodeId.of(nodeId));
        final var expectedCert = expectedAddr.getSigCert();

        assertEquals(expectedCert, actualCert, "Actual cert didn't match expected cert for node " + nodeId);

        return false;
    }
}
