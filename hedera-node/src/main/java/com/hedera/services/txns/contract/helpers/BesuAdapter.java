package com.hedera.services.txns.contract.helpers;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.protobuf.ByteString;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.store.contracts.HederaWorldUpdater;
import com.hedera.services.store.models.Account;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.fee.FeeBuilder;
import com.swirlds.common.CommonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.GasAndAccessedState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.feemarket.CoinbaseFeePriceCalculator;
import org.hyperledger.besu.ethereum.mainnet.BerlinTransactionGasCalculator;
import org.hyperledger.besu.ethereum.mainnet.TransactionGasCalculator;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.worldstate.GoQuorumMutablePrivateWorldStateUpdater;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.MainnetEVMs;
import org.hyperledger.besu.evm.account.AccountState;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.BerlinGasCalculator;
import org.hyperledger.besu.evm.precompile.MainnetPrecompiledContracts;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

import static com.hedera.services.utils.EntityIdUtils.asSolidityAddressHex;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;

@Singleton
public class BesuAdapter {

    private static final Logger LOG = LogManager.getLogger(BesuAdapter.class);

    private final UsagePricesProvider usagePrices;
    private final HbarCentExchange exchange;
    private final TransactionContext txnCtx;
    private final TransactionGasCalculator transactionGasCalculator;
    private final HederaWorldUpdater besuStateAdapter;

    @Inject
    public BesuAdapter(
            final UsagePricesProvider usagePrices,
            final HbarCentExchange exchange,
            final TransactionContext txnCtx,
            final HederaWorldUpdater besuStateAdapter
    ) {
        this.usagePrices = usagePrices;
        this.exchange = exchange;
        this.txnCtx = txnCtx;
        this.besuStateAdapter = besuStateAdapter;
        this.transactionGasCalculator = new BerlinTransactionGasCalculator();
    }

    public void executeTX(
            final boolean isContractCreation,
            Account sender,
            Account receiver,
            long gas,
            ByteString callData,
            long value,
            Instant consensusTime,
            final AccountID contractId) {

        Timestamp consensusTimeStamp = Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()).build();
        long callGasPrice;

        try {
            callGasPrice = getContractCallGasPriceInTinyBars(consensusTimeStamp);
        } catch (Exception e1) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("ContractCall gas coefficient could not be found in fee schedule " + e1.getMessage());
            }
            externalizeResult(TransactionProcessingResult.invalid(
                    ValidationResult.invalid(
                            TransactionInvalidReason.INTERNAL_ERROR,
                            "Internal Error in Hedera - "
                                    + "ContractCall gas coefficient could not be found in fee schedule "
                                    + e1.getMessage())));
            return;
        }

        Bytes payload = Bytes.EMPTY;
        if (callData != null
                && !callData.isEmpty()) {
            payload = Bytes.fromHexString(CommonUtils.hex(callData.toByteArray()));
        }

        var transaction = new Transaction(
                0,
                Wei.of(callGasPrice),
                gas,
                Optional.of(receiver.getEvmAddress()),
                Wei.of(value),
                null,
                payload,
                sender.getEvmAddress(),
                Optional.empty());

        /* --- Execute the TX and persist the updated models --- */
        var result = processTX(
                isContractCreation,
                besuStateAdapter,
                Address.fromHexString(asSolidityAddressHex(txnCtx.submittingNodeAccount())),
                txnCtx.consensusTime().getEpochSecond(),
                transaction,
                isContractCreation ? Address.fromHexString(asSolidityAddressHex(contractId)) : null
        );

        /* --- Externalises the result of the transaction to the transaction record service. --- */
        externalizeResult(result);
    }

    private TransactionProcessingResult processTX(
            final boolean isContractCreation,
            final WorldUpdater worldUpdater,
            final Address miningBeneficiary,
            final long timestamp,
            final Transaction transaction,
            final Address contractAddress
    ) {

        try {

            LOG.trace("Starting execution of {}", transaction);
            final var blockHeader = new ProcessableBlockHeader(
                    Hash.EMPTY,
                    miningBeneficiary,
                    Difficulty.ONE,
                    0,
                    transaction.getGasLimit(),
                    timestamp,
                    1L);

            var gasCalculator = new BerlinGasCalculator();

            final EvmAccount sender = worldUpdater.getOrCreateSenderAccount(transaction.getSender());

            final var senderMutableAccount = sender.getMutable();

            final Wei upfrontGasCost = transaction.getUpfrontGasCost(transaction.getGasPrice().orElse(Wei.of(1)));
            final Wei previousBalance = senderMutableAccount.decrementBalance(upfrontGasCost);
            LOG.trace(
                    "Deducted sender {} upfront gas cost {} ({} -> {})",
                    transaction.getSender(),
                    upfrontGasCost,
                    previousBalance,
                    sender.getBalance());

            final GasAndAccessedState gasAndAccessedState =
                    transactionGasCalculator.transactionIntrinsicGasCostAndAccessedState(transaction);
            final Gas intrinsicGas = gasAndAccessedState.getGas();
            final Gas gasAvailable = Gas.of(transaction.getGasLimit()).minus(intrinsicGas);
            LOG.trace(
                    "Gas available for execution {} = {} - {} (limit - intrinsic)",
                    gasAvailable,
                    transaction.getGasLimit(),
                    intrinsicGas);

            final EVM evm = MainnetEVMs.berlin();
            final PrecompileContractRegistry precompileContractRegistry = new PrecompileContractRegistry();
            MainnetPrecompiledContracts.populateForIstanbul(
                    precompileContractRegistry, evm.getGasCalculator());

            final Deque<MessageFrame> messageFrameStack = new ArrayDeque<>();

            final MessageFrame.Builder commonMessageFrameBuilder =
                    MessageFrame.builder()
                            .messageFrameStack(messageFrameStack)
                            .maxStackSize(1024)
                            .worldUpdater(worldUpdater.updater())
                            .initialGas(Gas.of(transaction.getGasLimit()))
                            .originator(transaction.getSender())
                            .gasPrice(transaction.getGasPrice().orElse(Wei.of(1)))
                            .sender(transaction.getSender())
                            .value(transaction.getValue())
                            .apparentValue(transaction.getValue())
                            .blockHeader(blockHeader)
                            .depth(0)
                            .completer(__ -> {})
                            .miningBeneficiary(miningBeneficiary)
                            .blockHashLookup(h -> null)
                            .accessListWarmAddresses(gasAndAccessedState.getAccessListAddressSet())
                            .accessListWarmStorage(gasAndAccessedState.getAccessListStorageByAddress());

            final MessageFrame initialFrame;

            if (isContractCreation) {
                initialFrame =
                        commonMessageFrameBuilder
                                .type(MessageFrame.Type.CONTRACT_CREATION)
                                .address(contractAddress)
                                .contract(contractAddress)
                                .inputData(Bytes.EMPTY)
                                .code(new Code(transaction.getPayload()))
                                .build();
            } else {
                final Address to = transaction.getTo().get();
                final Optional<org.hyperledger.besu.evm.account.Account> maybeContract =
                        Optional.ofNullable(worldUpdater.get(to));
                initialFrame =
                        commonMessageFrameBuilder
                                .type(MessageFrame.Type.MESSAGE_CALL)
                                .address(to)
                                .contract(to)
                                .inputData(transaction.getPayload())
                                .code(new Code(maybeContract.map(AccountState::getCode).orElse(Bytes.EMPTY)))
                                .build();
            }

            messageFrameStack.addFirst(initialFrame);

            final MessageCallProcessor mcp = new MessageCallProcessor(evm, precompileContractRegistry);
            while (!messageFrameStack.isEmpty()) {
                final MessageFrame messageFrame = messageFrameStack.peek();
                mcp.process(messageFrame, OperationTracer.NO_TRACING);
                if (messageFrame.getExceptionalHaltReason().isPresent()) {
                    //todo add to result? messageFrame.getExceptionalHaltReason().get()
                }
                if (messageFrame.getRevertReason().isPresent()) {
                    //todo add to result? new String(messageFrame.getRevertReason().get().toArray(), StandardCharsets.UTF_8))
                }
            }

            if (initialFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
                worldUpdater.commit();
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace(
                        "Gas used by transaction: {}, by message call/contract creation: {}",
                        () -> Gas.of(transaction.getGasLimit()).minus(initialFrame.getRemainingGas()),
                        () -> gasAvailable.minus(initialFrame.getRemainingGas()));
            }

            //Refund the sender by what we should and pay the miner fee (note that we're doing them one
            // after the other so that if it is the same account somehow, we end up with the right result)
            final Gas selfDestructRefund =
                    gasCalculator.getSelfDestructRefundAmount().times(initialFrame.getSelfDestructs().size());
            final Gas refundGas = initialFrame.getGasRefund().plus(selfDestructRefund);
            final Gas refunded = refunded(transaction, initialFrame.getRemainingGas(), refundGas);
            final Wei refundedWei = refunded.priceFor(transaction.getGasPrice().get());

            senderMutableAccount.incrementBalance(refundedWei);

            final Gas gasUsedByTransaction =
                    Gas.of(transaction.getGasLimit()).minus(initialFrame.getRemainingGas());

            if (!worldUpdater.getClass().equals(GoQuorumMutablePrivateWorldStateUpdater.class)) {
                // if this is not a private GoQuorum transaction we have to update the coinbase
                final MutableAccount coinbase = worldUpdater.getOrCreate(miningBeneficiary).getMutable();
                final Gas coinbaseFee = Gas.of(transaction.getGasLimit()).minus(refunded);
                if (blockHeader.getBaseFee().isPresent()) {
                    final Wei baseFee = Wei.of(blockHeader.getBaseFee().get());
                    if (transaction.getGasPrice().get().compareTo(baseFee) < 0) {
                        return TransactionProcessingResult.failed(
                                gasUsedByTransaction.toLong(),
                                refunded.toLong(),
                                ValidationResult.invalid(
                                        TransactionInvalidReason.TRANSACTION_PRICE_TOO_LOW,
                                        "transaction price must be greater than base fee"),
                                Optional.empty());
                    }
                }
                final CoinbaseFeePriceCalculator coinbaseCalculator = CoinbaseFeePriceCalculator.frontier();
                final Wei coinbaseWeiDelta =
                        coinbaseCalculator.price(coinbaseFee, transaction.getGasPrice().get(), blockHeader.getBaseFee());

                coinbase.incrementBalance(coinbaseWeiDelta);
            }

            initialFrame.getSelfDestructs().forEach(worldUpdater::deleteAccount);

            if (initialFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
                return TransactionProcessingResult.successful(
                        initialFrame.getLogs(),
                        gasUsedByTransaction.toLong(),
                        refunded.toLong(),
                        initialFrame.getOutputData(),
                        ValidationResult.valid()
                );
            } else {
                return TransactionProcessingResult.failed(
                        gasUsedByTransaction.toLong(),
                        refunded.toLong(),
                        //todo
                        ValidationResult.invalid(TransactionInvalidReason.valueOf("test")),
//                        validationResult,
                        initialFrame.getRevertReason());
            }
        } catch (final RuntimeException re) {
            LOG.error("Critical Exception Processing Transaction", re);
            return TransactionProcessingResult.invalid(
                    ValidationResult.invalid(
                            TransactionInvalidReason.INTERNAL_ERROR,
                            "Internal Error in Besu - " + re));
        }
    }

    private long getContractCallGasPriceInTinyBars(Timestamp at) {
        return gasPriceTinyBarsGiven(ContractCall, at);
    }

    private long gasPriceTinyBarsGiven(HederaFunctionality function, Timestamp at) {
        FeeData prices = usagePrices.defaultPricesGiven(function, at);
        long feeInTinyCents = prices.getServicedata().getGas() / 1000;
        long feeInTinyBars = FeeBuilder.getTinybarsFromTinyCents(exchange.rate(at), feeInTinyCents);
        return Math.max(1L, feeInTinyBars);
    }

    private void externalizeResult(TransactionProcessingResult result) {
        var contractFunctionResult = ContractFunctionResult.newBuilder()
                .setGasUsed(result.getEstimateGasUsedByTransaction())
                .setErrorMessage(result.getRevertReason().toString());
        Optional.ofNullable(result.getOutput().toArray())
                .map(ByteString::copyFrom)
                .ifPresent(contractFunctionResult::setContractCallResult);

        txnCtx.setCallResult(contractFunctionResult.build());
    }

    protected Gas refunded(
            final Transaction transaction, final Gas gasRemaining, final Gas gasRefund) {
        // Integer truncation takes care of the the floor calculation needed after the divide.
        final Gas maxRefundAllowance =
                Gas.of(transaction.getGasLimit())
                        .minus(gasRemaining)
                        .dividedBy(transactionGasCalculator.getMaxRefundQuotient());
        final Gas refundAllowance = maxRefundAllowance.min(gasRefund);
        return gasRemaining.plus(refundAllowance);
    }
}
