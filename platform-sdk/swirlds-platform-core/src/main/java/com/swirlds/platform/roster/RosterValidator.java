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

package com.swirlds.platform.roster;

import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A Roster validator.
 */
public final class RosterValidator {
    private RosterValidator() {}

    /**
     * Check if a given Roster object is valid and usable by the platform,
     * or throw an InvalidRosterException.
     *
     * @param roster a roster to validate
     */
    public static void validate(@NonNull final Roster roster) {
        if (roster == null) {
            throw new InvalidRosterException("roster is null");
        }

        final List<RosterEntry> rosterEntries = roster.rosterEntries();

        if (rosterEntries.isEmpty()) {
            throw new InvalidRosterException("roster is empty");
        }

        if (rosterEntries.stream().noneMatch(re -> re.weight() > 0)) {
            throw new InvalidRosterException("roster weight is zero or negative");
        }

        final Set<Long> seenNodeIds = new HashSet<>();
        long lastSeenNodeId = 0;
        for (final RosterEntry re : rosterEntries) {
            if (seenNodeIds.contains(re.nodeId())) {
                throw new InvalidRosterException("duplicate node id: " + re.nodeId());
            }
            seenNodeIds.add(re.nodeId());

            if (re.nodeId() < lastSeenNodeId) {
                throw new InvalidRosterException("RosterEntries sort order is invalid. Found node id: " + re.nodeId()
                        + " following " + lastSeenNodeId);
            }
            lastSeenNodeId = re.nodeId();

            if (re.weight() < 0) {
                throw new InvalidRosterException("weight is negative for node id: " + re.nodeId());
            }

            // May want to also check if the bytes represent a valid certificate.
            if (re.gossipCaCertificate().length() == 0) {
                throw new InvalidRosterException("gossipCaCertificate is empty for NodeId " + re.nodeId());
            }

            if (re.tssEncryptionKey().length() == 0) {
                // This is a valid case for an un-keyed roster.
            } else {
                // May want to also check if the bytes represent a valid key.
                // For now, assume that a non-zero length is valid.
            }

            if (re.gossipEndpoint().isEmpty()) {
                throw new InvalidRosterException("gossipEndpoint is empty for NodeId " + re.nodeId());
            }

            validateServiceEndpoints(re.nodeId(), re.gossipEndpoint());
            validateServiceEndpoints(re.nodeId(), re.internalGossipEndpoint());
        }
    }

    private static void validateServiceEndpoints(final long nodeId, @NonNull final List<ServiceEndpoint> endpoints) {
        for (final ServiceEndpoint se : endpoints) {
            if (se.port() == 0) {
                throw new InvalidRosterException(
                        "gossipPort is zero for NodeId " + nodeId + " and ServiceEndpoint " + se);
            }

            if (!(se.domainName().isEmpty() ^ se.ipAddressV4().length() == 0)) {
                throw new InvalidRosterException(
                        "ServiceEndpoint must specify either a domainName or an ipAddressV4, but not both. For NodeId "
                                + nodeId + " found ServiceEndpoint " + se);
            }

            if (se.ipAddressV4().length() != 0 && se.ipAddressV4().length() != 4) {
                throw new InvalidRosterException("ServiceEndpoint ipAddressV4 must have a length of 4 bytes, found "
                        + se.ipAddressV4().length() + " bytes for nodeId " + nodeId);
            }
        }
    }
}
