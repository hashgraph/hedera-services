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

package com.hedera.node.app.tss.impl;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.platform.state.NodeId;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import com.hederahashgraph.api.proto.java.TssMessageTransaction;
import com.hederahashgraph.api.proto.java.TssVoteTransaction;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TssCryptographyManager {
    private final NodeId nodeId;
    private final byte[] tssEncryptionKey;
    private final int maxSharesPerNode;
    private final boolean keyActiveRoster;
    private boolean createNewLedgerId;
    private byte[] activeRosterHash;
    private byte[] candidateRosterHash;

    private final Map<byte[], Roster> rosters;
    private final Map<byte[], Map<NodeId, Integer>> shareCounts;
    private final Map<byte[], List<PrivateShare>> privateShares;
    private final Map<byte[], List<PublicShare>> publicShares;
    private final Map<byte[], PairingPublicKey> ledgerIds;
    private final Map<byte[], List<TssMessageTransaction>> tssMessages;
    private final Map<byte[], List<TssVoteTransaction>> tssVotes;
    private final Set<byte[]> votingClosed;

    public TssCryptographyManager(NodeId nodeId, int maxSharesPerNode, boolean keyActiveRoster) {
        this.nodeId = nodeId;
        this.tssEncryptionKey = getTssEncryptionKey(nodeId.id());
        this.maxSharesPerNode = maxSharesPerNode;
        this.keyActiveRoster = keyActiveRoster;
        this.createNewLedgerId = false; // Default to false as per proposal

        this.rosters = new HashMap<>();
        this.shareCounts = new HashMap<>();
        this.privateShares = new HashMap<>();
        this.publicShares = new HashMap<>();
        this.ledgerIds = new HashMap<>();
        this.tssMessages = new HashMap<>();
        this.tssVotes = new HashMap<>();
        this.votingClosed = new HashSet<>();
    }

    private final Map<Long, byte[]> tssEncryptionKeys = new HashMap<>();

    public byte[] loadRoster(Roster roster) {
        byte[] rosterHash = RosterUtils.toByteArray(roster);
        for (RosterEntry entry : roster.rosterEntries()) {
            tssEncryptionKeys.put(entry.nodeId(), entry.tssEncryptionKey().toByteArray());
        }
        return rosterHash;
    }

    private byte[] computeRosterHash(Roster roster) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] rosterBytes = loadRoster(roster);
            return digest.digest(rosterBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    public byte[] getTssEncryptionKey(long nodeId) {
        return tssEncryptionKeys.get(nodeId);
    }

    /**
     * Set the active roster and begin the key generation process for it.
     * @param roster The active roster to be set.
     */
    public void setActiveRoster(Roster roster) {
        byte[] rosterHash = loadRoster(roster);
        if (this.rosters.containsKey(rosterHash)) {
            throw new IllegalStateException("Active roster already set!");
        }

        this.activeRosterHash = rosterHash;
        this.rosters.put(rosterHash, roster);

        // Compute the share counts per node for the active roster
        computeShareCounts(rosterHash, roster);

        // If keyActiveRoster is true and no key material exists, generate key material
        if (keyActiveRoster && !this.publicShares.containsKey(rosterHash)) {
            generateKeyMaterialForRoster(rosterHash, roster);
        }

        this.createNewLedgerId = true; // We are generating a new Ledger ID
    }

    /**
     * Set the candidate roster and begin the key generation process for it.
     * @param roster The candidate roster to be set.
     */
    public void keyCandidateRoster(Roster roster) {
        byte[] newCandidateRosterHash = loadRoster(roster);

        // Check if there is already a candidate roster and replace it if necessary
        if (this.candidateRosterHash != null
                && !java.util.Arrays.equals(this.candidateRosterHash, newCandidateRosterHash)) {

            // Clear old candidate roster data
            clearCandidateRosterData(this.candidateRosterHash);
        }

        this.candidateRosterHash = newCandidateRosterHash;
        this.rosters.put(newCandidateRosterHash, roster);

        // Compute the share counts per node for the candidate roster
        computeShareCounts(newCandidateRosterHash, roster);

        // If active roster doesn't have key material and keyActiveRoster is not true, generate key material
        if (!keyActiveRoster && !this.publicShares.containsKey(newCandidateRosterHash)) {
            generateKeyMaterialForRoster(newCandidateRosterHash, roster);
        }

        this.createNewLedgerId = true; // We are generating a new Ledger ID
    }

    private void computeShareCounts(byte[] rosterHash, Roster roster) {
        // Logic to compute and store share counts per node for the roster
        Map<NodeId, Integer> sharesForRoster = new HashMap<>();
        for (RosterEntry entry : roster.rosterEntries()) {
            long nodeId = entry.nodeId();
            sharesForRoster.put(new NodeId(nodeId), 0); // Initially, no shares for any node
        }
        this.shareCounts.put(rosterHash, sharesForRoster);
    }

    private void generateKeyMaterialForRoster(byte[] rosterHash, Roster roster) {
        // Logic to generate private and public shares, submit TssMessageTransactions
        List<PrivateShare> generatedPrivateShares = generatePrivateShares(roster);
        List<PublicShare> generatedPublicShares = generatePublicShares(generatedPrivateShares);

        // Store generated shares
        this.privateShares.put(rosterHash, generatedPrivateShares);
        this.publicShares.put(rosterHash, generatedPublicShares);

        // Submit TssMessageTransactions for the generated shares
        submitTssMessageTransactions(rosterHash, generatedPublicShares);
    }

    private List<PrivateShare> generatePrivateShares(Roster roster) {
        // Placeholder logic for generating private shares
        return List.of(); // Return empty list for now
    }

    private List<PublicShare> generatePublicShares(List<PrivateShare> privateShares) {
        // Placeholder logic for generating public shares from private shares
        return List.of(); // Return empty list for now
    }

    private void submitTssMessageTransactions(byte[] rosterHash, List<PublicShare> publicShares) {
        // Placeholder logic for submitting TSS message transactions to the network
    }

    private void clearCandidateRosterData(byte[] candidateRosterHash) {
        // Clear out old candidate roster's associated data
        this.rosters.remove(candidateRosterHash);
        this.shareCounts.remove(candidateRosterHash);
        this.privateShares.remove(candidateRosterHash);
        this.publicShares.remove(candidateRosterHash);
        this.tssMessages.remove(candidateRosterHash);
        this.tssVotes.remove(candidateRosterHash);
        this.votingClosed.remove(candidateRosterHash);
    }

    // Additional methods to handle TssMessageTransactions and TssVoteTransactions can go here
}

/* Mock Crypto classes to be implemented later */
class PrivateShare {
    private final byte[] shareData;

    public PrivateShare(byte[] shareData) {
        this.shareData = shareData;
    }

    public byte[] getShareData() {
        return shareData;
    }
}

class PublicShare {
    private final byte[] shareData;

    public PublicShare(byte[] shareData) {
        this.shareData = shareData;
    }

    public byte[] getShareData() {
        return shareData;
    }
}

class PairingPublicKey {
    private final byte[] publicKeyData;

    public PairingPublicKey(byte[] publicKeyData) {
        this.publicKeyData = publicKeyData;
    }

    public byte[] getPublicKeyData() {
        return publicKeyData;
    }
}

class RosterUtils {
    private static final Codec<Roster> ROSTER_CODEC = Roster.PROTOBUF;

    public static byte[] toByteArray(Roster roster) {
        try (ByteArrayOutputStream bout = new ByteArrayOutputStream()) {
            WritableStreamingData out = new WritableStreamingData(bout);
            ROSTER_CODEC.write(roster, out);
            return bout.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize Roster", e);
        }
    }
}