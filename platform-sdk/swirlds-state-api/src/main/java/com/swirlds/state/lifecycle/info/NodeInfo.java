/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.state.lifecycle.info;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.utility.CommonUtils;
import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Summarizes useful information about the nodes in the AddressBook from the Platform. In
 * the future, there may be events that require re-reading the book; but at present nodes may treat
 * the initializing book as static.
 */
public interface NodeInfo {

    /**
     * Convenience method to check if this node is zero-stake.
     *
     * @return whether this node has zero stake.
     */
    default boolean zeroStake() {
        return stake() == 0;
    }

    /**
     * Gets the node ID. This is a separate identifier from the node's account. This IS NOT IN ANY WAY related to the
     * node AccountID.
     *
     * <p>FUTURE: Should we expose NodeId from the platform? It would make this really hard to misuse as the node
     * accountID, whereas as a long, it could be.
     *
     * @return The node ID.
     */
    long nodeId();

    /**
     * Returns the account ID corresponding with this node.
     *
     * @return the account ID of the node.
     * @throws IllegalStateException if the book did not contain the id, or was missing an account for the id
     */
    AccountID accountId();

    /**
     * The stake weight of this node.
     * @return the stake weight
     */
    long stake();

    /**
     * The signing x509 certificate bytes of the member
     * @return the signing x509 certificate bytes
     */
    Bytes sigCertBytes();

    /**
     * The list of service endpoints of this node, as known by the internal and external worlds.
     * This has an IP address and port.
     *
     * @return The host name (IP Address) of this node
     */
    List<ServiceEndpoint> gossipEndpoints();

    /**
     * The public key of this node, as a hex-encoded string. It is extracted from the certificate bytes.
     *
     * @return the public key
     */
    default String hexEncodedPublicKey() {
        try {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");

            // Convert the byte array to an InputStream and generate the X509Certificate object
            X509Certificate certificate = (X509Certificate) certificateFactory.generateCertificate(
                    new ByteArrayInputStream(sigCertBytes().toByteArray()));

            // Return the public key from the certificate
            return CommonUtils.hex(certificate.getPublicKey().getEncoded());
        } catch (CertificateException e) {
            throw new IllegalStateException("Error extracting public key from certificate", e);
        }
    }
}
