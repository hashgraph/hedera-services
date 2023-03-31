/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.store.contracts.precompile.utils;

import static com.hedera.node.app.service.evm.store.contracts.HederaEvmWorldStateTokenAccount.TOKEN_PROXY_ACCOUNT_NONCE;

import com.google.common.primitives.Longs;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.ledger.accounts.ContractAliases;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.TokenCreatePrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.TokenUpdatePrecompile;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Arrays;
import java.util.Objects;
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
     * <p>Note that because the {@code DecodingFacade} converts every address to its "mirror"
     * address form (as needed for e.g. the {@code TransferLogic} implementation), we can assume the
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
            final ContractAliases aliases,
            @NonNull final HederaFunctionality function) {
        return internalValidateKey(frame, target, activationTest, null, ledgers, aliases, function);
    }

    public static boolean validateLegacyKey(
            final MessageFrame frame,
            final Address target,
            final LegacyKeyActivationTest legacyActivationTest,
            final WorldLedgers ledgers,
            final ContractAliases aliases,
            @NonNull final HederaFunctionality function) {
        return internalValidateKey(
                frame, target, null, legacyActivationTest, ledgers, aliases, function);
    }

    private static boolean internalValidateKey(
            final MessageFrame frame,
            final Address target,
            @Nullable final KeyActivationTest activationTest,
            @Nullable final LegacyKeyActivationTest legacyActivationTest,
            final WorldLedgers ledgers,
            final ContractAliases aliases,
            @NonNull final HederaFunctionality function) {
        final var recipient = aliases.resolveForEvm(frame.getRecipientAddress());
        final var sender = aliases.resolveForEvm(frame.getSenderAddress());

        if (isDelegateCall(frame) && !isToken(frame, recipient)) {
            if (activationTest != null) {
                return activationTest.apply(true, target, recipient, ledgers, function);
            } else {
                return Objects.requireNonNull(legacyActivationTest)
                        .apply(
                                true,
                                target,
                                recipient,
                                ledgers,
                                legacyActivationTestFor(frame),
                                function);
            }
        } else {
            final var parentFrame = getParentOf(frame);
            final boolean delegated =
                    parentFrame.map(KeyActivationUtils::isDelegateCall).orElse(false);
            if (activationTest != null) {
                return activationTest.apply(delegated, target, sender, ledgers, function);
            } else {
                return Objects.requireNonNull(legacyActivationTest)
                        .apply(
                                delegated,
                                target,
                                sender,
                                ledgers,
                                legacyActivationTestFor(frame),
                                function);
            }
        }
    }

    /**
     * Returns a predicate that checks whether any frame in the EVM stack <i>below the top</i> has a
     * recipient address of interest.
     *
     * <p>Only used when validating certain signatures in the {@link TokenCreatePrecompile} and
     * {@link TokenUpdatePrecompile}.
     *
     * @param frame the current frame
     * @return a predicate that tests if an address appears as recipient below this frame
     */
    static LegacyActivationTest legacyActivationTestFor(final MessageFrame frame) {
        return address -> {
            final var iter = frame.getMessageFrameStack().iterator();
            // We skip the frame at the top of the stack (recall that a deque representing
            // a stack stores the top at the front of its internal list)
            for (iter.next(); iter.hasNext(); ) {
                final var ancestor = iter.next();
                if (address.equals(ancestor.getRecipientAddress())) {
                    return true;
                }
            }
            return false;
        };
    }

    static boolean isToken(final MessageFrame frame, final Address address) {
        final var account = frame.getWorldUpdater().get(address);
        if (account != null) {
            return account.getNonce() == TOKEN_PROXY_ACCOUNT_NONCE;
        }
        return false;
    }

    /**
     * Given the current dynamic properties, the sender address from a frame executing a system
     * contract, and the logical function being executed in the system contract, returns if the
     * top-level HAPI sigs are available.
     *
     * @param senderAddress the sender address from the frame executing the system contract
     * @param function the logical function being executed in the system contract
     * @param dynamicProperties the current dynamic properties
     * @return if the top-level HAPI sigs are available
     */
    public static boolean areTopLevelSigsAvailable(
            @NonNull final Address senderAddress,
            @NonNull final HederaFunctionality function,
            @NonNull final GlobalDynamicProperties dynamicProperties) {
        Objects.requireNonNull(function);
        Objects.requireNonNull(senderAddress);
        Objects.requireNonNull(dynamicProperties);
        final var topLevelSigsAvailable =
                (dynamicProperties.contractsWithSpecialHapiSigsAccess().contains(senderAddress)
                        || numOf(senderAddress) <= dynamicProperties.maxNumWithHapiSigsAccess());
        return topLevelSigsAvailable
                && dynamicProperties.systemContractsWithTopLevelSigsAccess().contains(function);
    }

    private static long numOf(@NonNull final Address mirrorAddress) {
        return Longs.fromByteArray(Arrays.copyOfRange(mirrorAddress.toArrayUnsafe(), 12, 20));
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
