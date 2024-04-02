/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.network;

import static com.swirlds.platform.crypto.CryptoStatic.loadKeys;

import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.io.ResourceLoader;
import com.swirlds.platform.crypto.KeyCertPurpose;
import com.swirlds.platform.crypto.KeyLoadingException;
import com.swirlds.platform.crypto.PublicStores;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.network.NetworkUtils;
import com.swirlds.platform.network.PeerInfo;
import java.net.URISyntaxException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.net.ssl.SSLException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class NetworkUtilsTest {
    @Test
    void handleNetworkExceptionTest() {
        final Connection c = new FakeConnection();
        Assertions.assertDoesNotThrow(
                () -> NetworkUtils.handleNetworkException(new Exception(), c),
                "handling should not throw an exception");
        Assertions.assertFalse(c.connected(), "method should have disconnected the connection");

        Assertions.assertDoesNotThrow(
                () -> NetworkUtils.handleNetworkException(new SSLException("test", new NullPointerException()), null),
                "handling should not throw an exception");

        Assertions.assertThrows(
                InterruptedException.class,
                () -> NetworkUtils.handleNetworkException(new InterruptedException(), null),
                "an interrupted exception should be rethrown");
    }

    /**
     * Tests that given a list of valid Swirlds production certificates,
     * {@link NetworkUtils#identifyTlsPeer(Certificate[], List)} is able to successfully identify a matching peer.
     */
    @Test
    void testExtractPeerInfoWorksForMainnet()
            throws URISyntaxException, KeyLoadingException, KeyStoreException, InvalidAlgorithmParameterException {

        // sample node names from mainnet
        final List<String> names =
                List.of("node1", "node2", "node3", "node4", "node5", "node6", "node7", "node8", "node9", "node10");
        // sample pfx file grabbed from mainnet
        final KeyStore publicKeys = loadKeys(
                ResourceLoader.getFile("preGeneratedKeysAndCerts/").resolve("publicMainnet.pfx"),
                "password".toCharArray());
        final PublicStores publicStores = PublicStores.fromAllPublic(publicKeys, names);

        final List<PeerInfo> peerInfoList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final String name = names.get(i);
            final NodeId node = new NodeId(i);
            final PeerInfo peer = new PeerInfo(
                    node,
                    names.get(i),
                    "127.0.0.1",
                    Objects.requireNonNull(publicStores.getCertificate(KeyCertPurpose.SIGNING, name)));
            peerInfoList.add(peer);
        }
        final PKIXParameters params = new PKIXParameters(publicStores.agrTrustStore());
        final Set<TrustAnchor> trustAnchors = params.getTrustAnchors();

        final Certificate[] certificates =
                trustAnchors.stream().map(TrustAnchor::getTrustedCert).toArray(Certificate[]::new);

        final PeerInfo matchedPeer = NetworkUtils.identifyTlsPeer(certificates, peerInfoList);
        Assertions.assertNotNull(matchedPeer);
    }
}
