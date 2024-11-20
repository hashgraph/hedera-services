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

package com.swirlds.platform.network;

import static com.swirlds.logging.legacy.LogMarker.SOCKET_EXCEPTIONS;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.utility.throttle.RateLimitedLogger;
import com.swirlds.platform.crypto.CryptoStatic;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.security.auth.x500.X500Principal;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Identifies a connected peer from a list of trusted peers
 */
public class NetworkPeerIdentifier {

    private static final Logger logger = LogManager.getLogger(NetworkPeerIdentifier.class);

    /**
     * limits the frequency of error log statements
     */
    private final RateLimitedLogger noPeerFoundLogger;

    // a mapping of X500Principal and their peers
    private final Map<X500Principal, PeerInfo> x501PrincipalsAndPeers;

    /**
     * constructor
     *
     * @param platformContext the platform context
     * @param peers           list of peers
     */
    public NetworkPeerIdentifier(@NonNull final PlatformContext platformContext, @NonNull final List<PeerInfo> peers) {
        Objects.requireNonNull(platformContext);
        Objects.requireNonNull(peers);
        noPeerFoundLogger = new RateLimitedLogger(logger, platformContext.getTime(), Duration.ofMinutes(5));
        this.x501PrincipalsAndPeers = HashMap.newHashMap(peers.size());
        for (final PeerInfo peerInfo : peers) {
            if (!CryptoStatic.checkCertificate(peerInfo.signingCertificate())) {
                // skip peers without a valid signing certificate
                // https://github.com/hashgraph/hedera-services/issues/16648
                continue;
            }
            x501PrincipalsAndPeers.put(
                    ((X509Certificate) peerInfo.signingCertificate()).getSubjectX500Principal(), peerInfo);
        }
    }

    /**
     * identifies a client on the other end of the socket using their signing certificate.
     *
     * @param certs a list of TLS certificates from the connected socket
     * @return info of the identified peer
     */
    public @Nullable PeerInfo identifyTlsPeer(@NonNull final Certificate[] certs) {
        Objects.requireNonNull(certs);
        if (certs.length == 0) {
            return null;
        }

        // the peer certificates chain is an ordered array of peer certificates,
        // with the peer's own certificate first followed by any certificate authorities.
        // See https://www.rfc-editor.org/rfc/rfc5246
        final X509Certificate agreementCert = (X509Certificate) certs[0];
        final PeerInfo matchedPeer = x501PrincipalsAndPeers.get(agreementCert.getIssuerX500Principal());
        if (matchedPeer == null) {
            noPeerFoundLogger.warn(
                    SOCKET_EXCEPTIONS.getMarker(),
                    "Unable to identify peer with the presented certificate {}.",
                    agreementCert);
        }
        return matchedPeer;
    }
}
