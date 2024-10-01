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

package com.hedera.node.app.tss;

import com.hedera.hapi.node.tss.TssMessageTransactionBody;
import com.hedera.pbj.runtime.io.buffer.Bytes;

/**
 * This is yet to be implemented
 */
public interface TssCryptographyManager {
    /**
     * Submit TSS message transactions to the transaction pool
     */
    void submitTssMessageTransaction(TssMessageTransactionBody tssMessageTransactionBody);

    /**
     * Submit TSS vote transactions to the transaction pool
     */
    void submitVoteTransaction();

    /**
     * Key the candidate roster
     */
    void keyCandidateRoster();

    /**
     * Compute the ledger ID
     *
     * @return the ledger ID
     */
    Bytes computeLedgerId();

    /**
     * Submit the candidate roster to the platform for adoption when the candidate roster has enough key material
     * to generate the ledger id.
     */
    void submitCandidateRoster();
}
