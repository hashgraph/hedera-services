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

package com.swirlds.platform.bls.protocol;

import com.swirlds.common.system.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Object which keeps track of the behavior and status of counterparties during BLS protocol execution
 */
public class BlsCounterpartyManager {
    /** Set of counterparties who behaved in a verifiably incorrect way, assumed to be malicious */
    @NonNull
    private final Set<NodeId> maliciousNodes;

    /** Set of counterparties who failed to send an expected message */
    @NonNull
    private final Set<NodeId> offlineNodes;

    /** List of reports detailing the disqualification of a counterparty */
    @NonNull
    private final List<BlsIncidentReport> incidentReports;

    /** Constructor */
    public BlsCounterpartyManager() {
        this.maliciousNodes = new HashSet<>();
        this.offlineNodes = new HashSet<>();

        this.incidentReports = new ArrayList<>();
    }

    /**
     * Add a malicious party
     *
     * @param nodeId the id of the malicious party
     */
    public void declareMalicious(@NonNull final NodeId nodeId, @NonNull final BlsIncidentReport report) {
        Objects.requireNonNull(report, "report must not be null");

        // Don't disqualify a party in multiple ways
        if (checkDisqualified(nodeId)) {
            return;
        }

        maliciousNodes.add(nodeId);
        incidentReports.add(report);
    }

    /**
     * Add an offline party
     *
     * @param nodeId the id of the offline party
     */
    public void declareOffline(@NonNull final NodeId nodeId, @NonNull final BlsIncidentReport report) {
        Objects.requireNonNull(report, "report must not be null");

        // Don't disqualify a party in multiple ways
        if (checkDisqualified(nodeId)) {
            return;
        }

        offlineNodes.add(nodeId);
        incidentReports.add(report);
    }

    /**
     * Gets an unmodifiable view of {@link #maliciousNodes} counterparties
     *
     * @return {@link #maliciousNodes} counterparties
     */
    @NonNull
    public Set<NodeId> getMaliciousNodes() {
        return Collections.unmodifiableSet(maliciousNodes);
    }

    /**
     * Gets an unmodifiable view of {@link #offlineNodes} counterparties
     *
     * @return {@link #offlineNodes} counterparties
     */
    @NonNull
    public Set<NodeId> getOfflineNodes() {
        return Collections.unmodifiableSet(offlineNodes);
    }

    /**
     * Gets whether a nodeId has been marked malicious
     *
     * @param nodeId the nodeId to check
     * @return true if the node is malicious, otherwise false
     */
    public boolean checkMalicious(@NonNull final NodeId nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");

        return maliciousNodes.contains(nodeId);
    }

    /**
     * Gets whether a nodeId has been marked offline
     *
     * @param nodeId the nodeId to check
     * @return true if the node is offline, otherwise false
     */
    public boolean checkOffline(@NonNull final NodeId nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");

        return offlineNodes.contains(nodeId);
    }

    /**
     * Gets whether a nodeId has been disqualified from participating in the protocol
     *
     * @param nodeId the nodeId to check
     * @return true if the node is malicious or offline, otherwise false
     */
    public boolean checkDisqualified(@NonNull final NodeId nodeId) {
        return checkMalicious(nodeId) || checkOffline(nodeId);
    }

    /**
     * Gets the list of incidents which were reported
     *
     * @return the {@link #incidentReports}
     */
    @NonNull
    public List<BlsIncidentReport> getIncidentReports() {
        return incidentReports;
    }
}
