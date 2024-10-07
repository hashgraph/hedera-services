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

import com.hedera.hapi.node.state.roster.LedgerId;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hederahashgraph.api.proto.java.TssMessage;
import com.hederahashgraph.api.proto.java.TssMessageMapKey;
import com.hederahashgraph.api.proto.java.TssVote;
import com.hederahashgraph.api.proto.java.TssVoteMapKey;
import com.swirlds.common.exceptions.NotImplementedException;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class TssStateManager {

    private Map<Roster, TssMessageMapKey> tssMessageMap;
    private Map<Roster, TssVoteMapKey> tssVoteMap;
    private Roster activeRoster;
    private LedgerId ledgerId;
    private TssCryptographyManager tssCryptographyManager;

    public TssStateManager(@NonNull final TssCryptographyManager tssCryptographyManager) {
        this.tssCryptographyManager = Objects.requireNonNull(tssCryptographyManager);
        this.tssMessageMap = new HashMap<>();
        this.tssVoteMap = new HashMap<>();
    }

    public void handleStartup() {
        // Read all rosters, TssMessageTransactions, and TssVoteTransactions from state
        // and populate the TssStateManager

        // Verify ledger ID
        if (activeRosterHasTssKeyMaterial()) {
            verifyLedgerId();
        }

        // Adopt candidate roster if conditions are met
        if (conditionsMetForCandidateRoster()) {
            adoptCandidateRoster();
        }

        // Set ledger ID if it does not exist
        if (ledgerId == null && candidateRosterHasLedgerId()) {
            setLedgerIdFromCandidateRoster();
        }

        // Send data to TssCryptographyManager
        sendDataToTssCryptographyManager();
    }

    private boolean activeRosterHasTssKeyMaterial() {
        // Implement logic to check if active roster has TSS key material
        throw new NotImplementedException();
    }

    private void verifyLedgerId() {
        // Implement logic to compute and verify ledger ID
        throw new NotImplementedException();
    }

    private boolean conditionsMetForCandidateRoster() {
        // Implement logic to check if conditions are met for adopting candidate roster
        throw new NotImplementedException();
    }

    private void adoptCandidateRoster() {
        // Implement logic to adopt candidate roster and cleanup state
        throw new NotImplementedException();
    }

    private boolean candidateRosterHasLedgerId() {
        // Implement logic to check if candidate roster has a ledger ID
        return false;
    }

    private void setLedgerIdFromCandidateRoster() {
        // Implement logic to set ledger ID from candidate roster
        throw new NotImplementedException();
    }

    private void sendDataToTssCryptographyManager() {
        // Send active roster, candidate roster, TssMessageTransactions, and TssVoteTransactions to
        // TssCryptographyManager
        throw new NotImplementedException();
    }

    public void handleTssMessageTransaction(@NonNull final TssMessage tssMessage) {
        throw new NotImplementedException();
    }

    public void handleTssMessageTransaction(Bytes transaction) {
        throw new NotImplementedException();
    }

    public void handleTssVoteTransaction(@NonNull final TssVote tssVote) {
        throw new NotImplementedException();
    }

    public void handleTssVoteTransaction(Bytes transaction) {
        throw new NotImplementedException();
    }

    public void setCandidateRoster(Roster candidateRoster) {
        // Erase existing candidate roster and related data
        clearCandidateRosterData(candidateRoster);

        // Set the new candidate roster
        this.activeRoster = candidateRoster;

        // Send the candidate roster to TssCryptographyManager
        tssCryptographyManager.keyCandidateRoster(candidateRoster);
    }

    public void clearCandidateRosterData(Roster roster) {
        // Clear related data from the state
        tssMessageMap.remove(roster);
        tssVoteMap.remove(roster);
    }
}
