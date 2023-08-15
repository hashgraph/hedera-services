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

package com.swirlds.platform.state.signed;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.formatting.TextTable;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.state.PlatformData;
import com.swirlds.platform.state.PlatformState;
import java.time.Instant;

/**
 * Basic record object to carry information useful for signed state validation.
 * @param round the minimum round to be considered a valid state
 * @param numberOfConsensusEvents the count of all consensus events in history from an earlier state
 * @param consensusTimestamp The consensus timestamp from an earlier state
 * @param lastTransactionTimestamp The last transaction timestamp from an earlier state
 * @param addressBookHash The address book hash value for the current address book (mostly used for diagnostics).
 * @param consensusEventsRunningHash The running hash of the consensus event hashes throughout history
 * @param epochHash The epoch hash from an earlier state.
 */
public record SignedStateValidationData(
        long round,
        long numberOfConsensusEvents,
        Instant consensusTimestamp,
        Instant lastTransactionTimestamp,
        Hash addressBookHash,
        Hash consensusEventsRunningHash,
        Hash epochHash) {

    public SignedStateValidationData(final PlatformData that, final AddressBook addressBook) {
        this(
                that.getRound(),
                that.getNumEventsCons(),
                that.getConsensusTimestamp(),
                that.getLastTransactionTimestamp(),
                addressBook == null ? null : addressBook.getHash(),
                that.getHashEventsCons(),
                that.getEpochHash());
    }

    /**
     * Informational method used for diagnostics.
     * This method constructs a {@link String} containing the critical attributes of this data object.
     * The original use is during reconnect to produce useful information sent to diagnostic event output.
     * @return a {@link String} containing the core data from this object, in human-readable form.
     * @see PlatformState#getInfoString()
     * @see PlatformData#getInfoString()
     */
    public String getInfoString() {
        final TextTable infoTable = new TextTable();
        infoTable.addRow("Round", round);
        infoTable.addRow("Number of consensus events", numberOfConsensusEvents);
        infoTable.addRow(
                "Consensus events running hash",
                consensusEventsRunningHash == null ? "null" : consensusEventsRunningHash.toMnemonic());
        infoTable.addRow("Consensus timestamp", consensusTimestamp);
        infoTable.addRow("Last timestamp", lastTransactionTimestamp);
        infoTable.addRow("Epoch hash", epochHash == null ? "null" : epochHash.toMnemonic());

        return infoTable.toString();
    }
}
