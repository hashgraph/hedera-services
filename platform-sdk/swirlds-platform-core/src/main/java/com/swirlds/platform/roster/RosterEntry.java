/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.roster;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.platform.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.PublicKey;
import java.security.cert.X509Certificate;

/**
 * A RosterEntry is a single node in the roster.  It contains the node's ID, weight, network address, and public signing
 * key in the form of an X509Certificate.  The data in a RosterEntry object is immutable and must not change over time.
 */
public interface RosterEntry extends SelfSerializable {

    /**
     * @return the ID of the node
     */
    @NonNull
    NodeId getNodeId();

    /**
     * @return the non-negative consensus weight of the node
     */
    long getWeight();

    /**
     * @return the hostname portion of a node's gossip endpoint.
     */
    @NonNull
    String getHostname();

    /**
     * @return the port portion of a node's gossip endpoint.
     */
    int getPort();

    /**
     * @return the X509Certificate containing the public signing key of the node
     */
    @NonNull
    X509Certificate getSigningCertificate();

    /**
     * @return the public signing key of the node
     */
    @NonNull
    default PublicKey getSigningPublicKey() {
        return getSigningCertificate().getPublicKey();
    }

    /**
     * @return true if the weight is zero, false otherwise
     */
    default boolean isZeroWeight() {
        return getWeight() == 0;
    }
}
