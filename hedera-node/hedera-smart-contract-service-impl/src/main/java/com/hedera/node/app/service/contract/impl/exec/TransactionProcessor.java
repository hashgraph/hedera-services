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

package com.hedera.node.app.service.contract.impl.exec;

import static com.hedera.hapi.node.base.ResponseCodeEnum.*;
import static com.hedera.node.app.service.contract.impl.hevm.HederaEvmTransactionResult.resourceExhaustionFrom;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.isEvmAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjToBesuAddress;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.contract.impl.exec.failure.ResourceExhaustedException;
import com.hedera.node.app.service.contract.impl.exec.gas.CustomGasCharging;
import com.hedera.node.app.service.contract.impl.exec.gas.GasCharges;
import com.hedera.node.app.service.contract.impl.exec.processors.CustomMessageCallProcessor;
import com.hedera.node.app.service.contract.impl.exec.utils.FrameBuilder;
import com.hedera.node.app.service.contract.impl.exec.utils.FrameRunner;
import com.hedera.node.app.service.contract.impl.hevm.*;
import com.hedera.node.app.service.contract.impl.state.HederaEvmAccount;
import com.hedera.node.app.spi.workflows.HandleException;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.function.Supplier;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;

/**
 * Modeled after the Besu {@code MainnetTransactionProcessor}, so that all four HAPI
 * contract operations ({@code ContractCall}, {@code ContractCreate}, {@code EthereumTransaction},
 * {@code ContractCallLocal}) can reduce to a single code path.
 */
public class TransactionProcessor {
    private final FrameBuilder frameBuilder;
    private final FrameRunner frameRunner;
    private final CustomGasCharging gasCharging;
    private final CustomMessageCallProcessor messageCall;
    private final ContractCreationProcessor contractCreation;

    public TransactionProcessor(
            @NonNull final FrameBuilder frameBuilder,
            @NonNull final FrameRunner frameRunner,
            @NonNull final CustomGasCharging gasCharging,
            @NonNull final CustomMessageCallProcessor messageCall,
            @NonNull final ContractCreationProcessor contractCreation) {
        this.frameBuilder = requireNonNull(frameBuilder);
        this.frameRunner = requireNonNull(frameRunner);
        this.gasCharging = requireNonNull(gasCharging);
        this.messageCall = requireNonNull(messageCall);
        this.contractCreation = requireNonNull(contractCreation);
    }

    /**
     * Process the given transaction, returning the result of running it to completion
     * and committing to the given updater.
     *
     * @param transaction     the transaction to process
     * @param updater         the world updater to commit to
     * @param feesOnlyUpdater if base commit fails, a fees-only updater
     * @param context         the context to use
     * @param tracer          the tracer to use
     * @param config          the node configuration
     * @return the result of running the transaction to completion
     */
    public HederaEvmTransactionResult processTransaction(
            @NonNull final HederaEvmTransaction transaction,
            @NonNull final HederaWorldUpdater updater,
            @NonNull final Supplier<HederaWorldUpdater> feesOnlyUpdater,
            @NonNull final HederaEvmContext context,
            @NonNull final HederaTracer tracer,
            @NonNull final Configuration config) {
        final InvolvedParties parties;
        final GasCharges gasCharges;
        try {
            // Compute the sender, relayer, and to address (throws if invalid)
            parties = setup(transaction, updater, config);
            if (transaction.isEthereumTransaction()) {
                parties.sender().incrementNonce();
            }
            // Charge gas and return intrinsic gas and relayer allowance used (throws on failure)
            gasCharges = gasCharging.chargeForGas(parties.sender(), parties.relayer(), context, updater, transaction);
        } catch (final HandleException failure) {
            return HederaEvmTransactionResult.abortFor(failure.getStatus());
        }

        // Build the initial frame for the transaction
        final var initialFrame = frameBuilder.buildInitialFrameWith(
                transaction,
                updater,
                context,
                config,
                parties.sender().getAddress(),
                parties.receiverAddress(),
                gasCharges.intrinsicGas());

        // Compute the result of running the frame to completion
        final HederaEvmTransactionResult result;
        try {
            result = frameRunner.runToCompletion(
                    transaction.gasLimit(), initialFrame, tracer, messageCall, contractCreation);
        } catch (ResourceExhaustedException e) {
            return resourceExhaustionFrom(transaction.gasLimit(), context.gasPrice(), e.getStatus());
        }
        // Adjust the pending commit based on the result
        gasCharging.maybeRefundGiven(
                transaction.unusedGas(result.gasUsed()),
                gasCharges.relayerAllowanceUsed(),
                parties.sender(),
                parties.relayer(),
                context,
                updater);
        initialFrame.getSelfDestructs().forEach(updater::deleteAccount);

        // Returns this result if we can commit it without resource exhaustion, otherwise returns a fees-only result
        return safeCommit(result, transaction, updater, feesOnlyUpdater, context);
    }

    private HederaEvmTransactionResult safeCommit(
            @NonNull final HederaEvmTransactionResult result,
            @NonNull final HederaEvmTransaction transaction,
            @NonNull final HederaWorldUpdater updater,
            @NonNull final Supplier<HederaWorldUpdater> feesOnlyUpdater,
            @NonNull final HederaEvmContext context) {
        try {
            updater.commit();
        } catch (ResourceExhaustedException e) {
            // TODO - increment sender nonce and re-charge gas using feesOnlyUpdater
            return resourceExhaustionFrom(transaction.gasLimit(), context.gasPrice(), e.getStatus());
        }
        return result;
    }

    private record InvolvedParties(
            @NonNull HederaEvmAccount sender, @Nullable HederaEvmAccount relayer, @NonNull Address receiverAddress) {}

    /**
     * Given an input {@link HederaEvmTransaction}, the {@link HederaWorldUpdater} for the transaction, and the
     * current node {@link Configuration}, sets up the transaction and returns the three "involved parties":
     * <ol>
     *     <li>The sender account.</li>
     *     <li>The (possibly missing) relayer account.</li>
     *     <li>The "to" address receiving the top-level call.</li>
     * </ol>
     *
     * <p>Note that if the transaction is a {@code CONTRACT_CREATION}, setup includes calling either
     * {@link HederaWorldUpdater#setupCreate(Address)} or
     * {@link HederaWorldUpdater#setupAliasedCreate(Address, Address)}.
     *
     * @param transaction the transaction to set up
     * @param updater the updater for the transaction
     * @param config the current node configuration
     * @return the involved parties determined while setting up the transaction
     */
    private InvolvedParties setup(
            @NonNull final HederaEvmTransaction transaction,
            @NonNull final HederaWorldUpdater updater,
            @NonNull final Configuration config) {

        final var sender = updater.getHederaAccount(transaction.senderId());
        validateTrue(sender != null, INVALID_ACCOUNT_ID);
        HederaEvmAccount relayer = null;
        if (transaction.isEthereumTransaction()) {
            relayer = updater.getHederaAccount(requireNonNull(transaction.relayerId()));
            validateTrue(relayer != null, INVALID_ACCOUNT_ID);
        }
        if (transaction.isCreate()) {
            final Address to;
            if (transaction.isEthereumTransaction()) {
                to = Address.contractAddress(sender.getAddress(), sender.getNonce());
                // Top-level creates "originate" from the zero address
                updater.setupAliasedCreate(Address.ZERO, to);
            } else {
                to = updater.setupCreate(Address.ZERO);
            }
            return new InvolvedParties(sender, relayer, to);
        } else {
            final var to = updater.getHederaAccount(transaction.contractIdOrThrow());
            if (maybeLazyCreate(transaction, to, config)) {
                // Presumably these checks _could_ be done later as part of the message
                // call, but historically we have failed fast when they do not pass
                validateTrue(transaction.hasValue(), INVALID_CONTRACT_ID);
                final var alias = transaction.contractIdOrThrow().evmAddressOrThrow();
                validateTrue(isEvmAddress(alias), INVALID_CONTRACT_ID);
                return new InvolvedParties(sender, relayer, pbjToBesuAddress(alias));
            } else {
                validateTrue(to != null, INVALID_CONTRACT_ID);
                return new InvolvedParties(sender, relayer, requireNonNull(to).getAddress());
            }
        }
    }

    private boolean maybeLazyCreate(
            @NonNull final HederaEvmTransaction transaction,
            @Nullable final HederaEvmAccount to,
            @NonNull final Configuration config) {
        return to == null && transaction.isEthereumTransaction() && messageCall.isImplicitCreationEnabled(config);
    }
}
