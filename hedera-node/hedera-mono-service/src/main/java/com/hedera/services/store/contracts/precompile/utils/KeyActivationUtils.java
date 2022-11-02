/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store.contracts.precompile.utils;

import static com.hedera.services.evm.store.contracts.HederaEvmWorldStateTokenAccount.TOKEN_PROXY_ACCOUNT_NONCE;

import com.hedera.services.ledger.TransferLogic;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import java.util.Optional;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;

public final class KeyActivationUtils {

    private KeyActivationUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Checks if a key implicit in a target address is active in the current frame using a {@link
     * KeyActivationTest}.
     *
     * <p>We massage the current frame a bit to ensure that a precompile being executed via delegate
     * call is tested as such. There are three cases.
     *
     * <ol>
     *   <li>The precompile is being executed via a delegate call, so the current frame's
     *       <b>recipient</b> (not sender) is really the "active" contract that can match a {@code
     *       delegatable_contract_id} key; or,
     *   <li>The precompile is being executed via a call, but the calling code was executed via a
     *       delegate call, so although the current frame's sender <b>is</b> the "active" contract,
     *       it must be evaluated using an activation test that restricts to {@code
     *       delegatable_contract_id} keys; or,
     *   <li>The precompile is being executed via a call, and the calling code is being executed as
     *       part of a non-delegate call.
     * </ol>
     *
     * <p>Note that because the {@link DecodingFacade} converts every address to its "mirror"
     * address form (as needed for e.g. the {@link TransferLogic} implementation), we can assume the
     * target address is a mirror address. All other addresses we resolve to their mirror form
     * before proceeding.
     *
     * @param frame current frame
     * @param target the element to test for key activation, in standard form
     * @param activationTest the function which should be invoked for key validation
     * @param ledgers the current Hedera world state
     * @param aliases the current Hedera contract aliases
     * @return whether the implied key is active
     */
    public static boolean validateKey(
            final MessageFrame frame,
            final Address target,
            final KeyActivationTest activationTest,
            final WorldLedgers ledgers,
            final ContractAliases aliases) {
        final var recipient = aliases.resolveForEvm(frame.getRecipientAddress());
        final var sender = aliases.resolveForEvm(frame.getSenderAddress());

        if (isDelegateCall(frame) && !isToken(frame, recipient)) {
            return activationTest.apply(true, target, recipient, ledgers);
        } else {
            final var parentFrame = getParentOf(frame);
            final var delegated = parentFrame.map(KeyActivationUtils::isDelegateCall).orElse(false);
            return activationTest.apply(delegated, target, sender, ledgers);
        }
    }

    static boolean isToken(final MessageFrame frame, final Address address) {
        final var account = frame.getWorldUpdater().get(address);
        if (account != null) {
            return account.getNonce() == TOKEN_PROXY_ACCOUNT_NONCE;
        }
        return false;
    }

    private static Optional<MessageFrame> getParentOf(final MessageFrame frame) {
        final var it = frame.getMessageFrameStack().iterator();

        if (it.hasNext()) {
            it.next();
        } else {
            return Optional.empty();
        }

        MessageFrame parentFrame;
        if (it.hasNext()) {
            parentFrame = it.next();
        } else {
            return Optional.empty();
        }

        return Optional.of(parentFrame);
    }

    private static boolean isDelegateCall(final MessageFrame frame) {
        final var contract = frame.getContractAddress();
        final var recipient = frame.getRecipientAddress();
        return !contract.equals(recipient);
    }
}
