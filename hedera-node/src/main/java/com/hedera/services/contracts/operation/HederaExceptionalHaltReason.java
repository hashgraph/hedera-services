/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.contracts.operation;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import com.hedera.services.state.merkle.MerkleAccount;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;

/** Hedera adapted {@link ExceptionalHaltReason} */
public class HederaExceptionalHaltReason {

    /**
     * Used when the EVM transaction accesses address that does not map to any existing
     * (non-deleted) account
     */
    public static final ExceptionalHaltReason INVALID_SOLIDITY_ADDRESS =
            HederaExceptionalHalt.INVALID_SOLIDITY_ADDRESS;
    /**
     * Used when {@link HederaSelfDestructOperation} is used and the beneficiary is specified to be
     * the same as the destructed account
     */
    public static final ExceptionalHaltReason SELF_DESTRUCT_TO_SELF =
            HederaExceptionalHalt.SELF_DESTRUCT_TO_SELF;
    /**
     * Used when there is no active signature for a given {@link
     * com.hedera.services.state.merkle.MerkleAccount} that has {@link
     * MerkleAccount#isReceiverSigRequired()} enabled and the account receives HBars
     */
    public static final ExceptionalHaltReason INVALID_SIGNATURE =
            HederaExceptionalHalt.INVALID_SIGNATURE;
    /** Used when the target of a {@code selfdestruct} is a token treasury. */
    public static final ExceptionalHaltReason CONTRACT_IS_TREASURY =
            HederaExceptionalHalt.CONTRACT_IS_TREASURY;
    /** Used when the target of a {@code selfdestruct} has positive fungible unit balances. */
    public static final ExceptionalHaltReason CONTRACT_STILL_OWNS_NFTS =
            HederaExceptionalHalt.CONTRACT_STILL_OWNS_NFTS;
    /** Used when the target of a {@code selfdestruct} has positive balances. */
    public static final ExceptionalHaltReason TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES =
            HederaExceptionalHalt.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;

    enum HederaExceptionalHalt implements ExceptionalHaltReason {
        INVALID_SOLIDITY_ADDRESS("Invalid account reference"),
        SELF_DESTRUCT_TO_SELF("Self destruct to the same address"),
        CONTRACT_IS_TREASURY("Token treasuries cannot be deleted"),
        INVALID_SIGNATURE("Invalid signature"),
        TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES(
                "Accounts with positive fungible token balances cannot be deleted"),
        CONTRACT_STILL_OWNS_NFTS("Accounts who own nfts cannot be deleted");

        final String description;

        HederaExceptionalHalt(final String description) {
            this.description = description;
        }

        @Override
        public String getDescription() {
            return description;
        }
    }
}
