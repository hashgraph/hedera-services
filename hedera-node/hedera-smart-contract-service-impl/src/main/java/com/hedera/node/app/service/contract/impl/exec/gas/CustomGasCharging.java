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

package com.hedera.node.app.service.contract.impl.exec.gas;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hedera.node.app.service.contract.impl.exec.gas.GasCharges.ZERO_CHARGES;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.contract.impl.hevm.HederaEvmContext;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmTransaction;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.state.HederaEvmAccount;
import com.hedera.node.app.spi.workflows.HandleException;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

/**
 * Implements the Hedera gas charging logic. The main difference from Besu here is that we can have a
 * "relayer" account from an {@code EthereumTransaction}, in addition to the standard EOA {@code sender}
 * account.
 *
 * <p>As with Besu, the sender offers a gas price for the transaction; and combined with the gas limit
 * for the transaction, this implies a total cost for gas the sender is willing to pay.
 *
 * <p>So the relayer may offer a <i>gas allowance</i> to help pay for gas, in addition to what
 * the sender has offered. The relayer's gas allowance is used only when the network's gas cost is
 * greater than what the sender has offered.
 */
@Singleton
public class CustomGasCharging {
    private final GasCalculator gasCalculator;

    @Inject
    public CustomGasCharging(@NonNull final GasCalculator gasCalculator) {
        this.gasCalculator = gasCalculator;
    }

    /**
     * If the actual gas used by a transaction merits a refund, then do the refund using
     * relayer-aware logic. The relayer gets refund priority, so any charged allowance
     * is first refunded, with the sender only getting a refund if there is any left over.
     *
     * @param unusedGas the actual gas used by the transaction
     * @param allowanceUsed the amount of the relayer's gas allowance used
     * @param sender the sender account
     * @param relayer the relayer account, if present
     * @param context the context of the transaction, including the network gas price
     * @param worldUpdater the world updater for the transaction
     */
    public void maybeRefundGiven(
            final long unusedGas,
            final long allowanceUsed,
            @NonNull final HederaEvmAccount sender,
            @Nullable final HederaEvmAccount relayer,
            @NonNull final HederaEvmContext context,
            @NonNull final HederaWorldUpdater worldUpdater) {
        requireNonNull(sender);
        requireNonNull(context);
        requireNonNull(worldUpdater);
        if (context.isNoopGasContext() || unusedGas == 0) {
            return;
        }
        final var refund = unusedGas * context.gasPrice();
        if (allowanceUsed > 0) {
            requireNonNull(relayer);
            worldUpdater.refundFee(relayer.hederaId(), Math.min(allowanceUsed, refund));
            if (refund > allowanceUsed) {
                worldUpdater.refundFee(sender.hederaId(), refund - allowanceUsed);
            }
        } else {
            worldUpdater.refundFee(sender.hederaId(), refund);
        }
    }

    /**
     * Tries to charge gas for the given transaction based on the pre-fetched sender and relayer accounts,
     * within the given context and world updater.
     *
     * @param sender  the sender account
     * @param relayer the relayer account
     * @param context the context of the transaction, including the network gas price
     * @param worldUpdater the world updater for the transaction
     * @param transaction the transaction to charge gas for
     * @return the result of the gas charging
     * @throws HandleException if the gas charging fails for any reason
     */
    public GasCharges chargeForGas(
            @NonNull final HederaEvmAccount sender,
            @Nullable final HederaEvmAccount relayer,
            @NonNull final HederaEvmContext context,
            @NonNull final HederaWorldUpdater worldUpdater,
            @NonNull final HederaEvmTransaction transaction) {
        if (context.isNoopGasContext()) {
            return ZERO_CHARGES;
        }
        final var intrinsicGas = gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, transaction.isCreate());
        validateTrue(transaction.gasLimit() >= intrinsicGas, INSUFFICIENT_GAS);
        if (transaction.isEthereumTransaction()) {
            final var allowanceUsed =
                    chargeWithRelayer(sender, requireNonNull(relayer), context, worldUpdater, transaction);
            return new GasCharges(intrinsicGas, allowanceUsed);
        } else {
            chargeWithOnlySender(sender, context, worldUpdater, transaction);
            return new GasCharges(intrinsicGas, 0L);
        }
    }

    private void chargeWithOnlySender(
            @NonNull final HederaEvmAccount sender,
            @NonNull final HederaEvmContext context,
            @NonNull final HederaWorldUpdater worldUpdater,
            @NonNull final HederaEvmTransaction transaction) {
        final var gasCost = transaction.gasCostGiven(context.gasPrice());
        final var upfrontCost = transaction.upfrontCostGiven(context.gasPrice());
        // We validate up-front cost here just for consistency with existing code
        validateTrue(sender.getBalance().toLong() >= upfrontCost, INSUFFICIENT_PAYER_BALANCE);
        validateAndCharge(gasCost, sender, worldUpdater);
    }

    private long chargeWithRelayer(
            @NonNull final HederaEvmAccount sender,
            @NonNull final HederaEvmAccount relayer,
            @NonNull final HederaEvmContext context,
            @NonNull final HederaWorldUpdater worldUpdater,
            @NonNull final HederaEvmTransaction transaction) {
        final var gasCost = transaction.gasCostGiven(context.gasPrice());
        if (transaction.requiresFullRelayerAllowance()) {
            validateTrue(transaction.maxGasAllowance() >= gasCost, INSUFFICIENT_TX_FEE);
            validateAndCharge(gasCost, requireNonNull(relayer), worldUpdater);
            return gasCost;
        } else if (transaction.offeredGasPrice() >= context.gasPrice()) {
            validateAndCharge(gasCost, sender, worldUpdater);
            return 0L;
        } else {
            final var relayerGasCost = gasCost - transaction.offeredGasCost();
            validateTrue(transaction.maxGasAllowance() >= relayerGasCost, INSUFFICIENT_TX_FEE);
            validateAndCharge(
                    transaction.offeredGasCost(), relayerGasCost, sender, requireNonNull(relayer), worldUpdater);
            return relayerGasCost;
        }
    }

    private void validateAndCharge(
            final long amount, @NonNull final HederaEvmAccount payer, @NonNull final HederaWorldUpdater worldUpdater) {
        validateTrue(payer.getBalance().toLong() >= amount, INSUFFICIENT_PAYER_BALANCE);
        worldUpdater.collectFee(payer.hederaId(), amount);
    }

    private void validateAndCharge(
            final long aAmount,
            final long bAmount,
            @NonNull final HederaEvmAccount aPayer,
            @NonNull final HederaEvmAccount bPayer,
            @NonNull final HederaWorldUpdater worldUpdater) {
        validateTrue(aPayer.getBalance().toLong() >= aAmount, INSUFFICIENT_PAYER_BALANCE);
        validateTrue(bPayer.getBalance().toLong() >= bAmount, INSUFFICIENT_PAYER_BALANCE);
        worldUpdater.collectFee(aPayer.hederaId(), aAmount);
        worldUpdater.collectFee(bPayer.hederaId(), bAmount);
    }
}
