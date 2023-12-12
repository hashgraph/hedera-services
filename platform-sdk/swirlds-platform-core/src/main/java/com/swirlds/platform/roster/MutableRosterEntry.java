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

import com.swirlds.common.platform.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.cert.X509Certificate;

/**
 * A mutable roster entry has setters for all the fields of a {@link RosterEntry}.  The MutableRosterEntry should be
 * {@link RosterEntry#seal() sealed} before use as a RosterEntry.
 */
public interface MutableRosterEntry extends RosterEntry {

    /**
     * Sets the node ID of this roster entry.
     *
     * @param nodeId the node ID to set
     * @return this mutable roster entry
     */
    @NonNull
    MutableRosterEntry setNodeId(NodeId nodeId);

    /**
     * Sets the consensus weight of this roster entry.
     *
     * @param weight the consensus weight to set
     * @return this mutable roster entry
     */
    @NonNull
    MutableRosterEntry setWeight(long weight);

    /**
     * Sets the hostname of this roster entry.
     *
     * @param hostname the internal hostname to set
     * @return this mutable roster entry
     */
    @NonNull
    MutableRosterEntry setHostname(String hostname);

    /**
     * Sets the internal port of this roster entry.
     *
     * @param port the internal port to set
     * @return this mutable roster entry
     */
    @NonNull
    MutableRosterEntry setPort(int port);

    /**
     * Sets the signing certificate of this roster entry.
     *
     * @param signingCert the signing certificate to set
     * @return this mutable roster entry
     */
    @NonNull
    MutableRosterEntry setSigningCertificate(X509Certificate signingCert);
}
