/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.contracts.operation;

import static com.hedera.node.app.service.mono.utils.EntityIdUtils.numOfMirror;

import com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.contracts.sources.EvmSigsVerifier;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.function.BiPredicate;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.SelfDestructOperation;

/**
 * Hedera adapted version of the {@link SelfDestructOperation}.
 *
 * <p>Performs an existence check on the beneficiary {@link Address} Halts the execution of the EVM
 * transaction with {@link HederaExceptionalHaltReason#INVALID_SOLIDITY_ADDRESS} if the account does
 * not exist, or it is deleted.
 *
 * <p>Halts the execution of the EVM transaction with {@link
 * HederaExceptionalHaltReason#SELF_DESTRUCT_TO_SELF} if the beneficiary address is the same as the
 * address being destructed
 */
public class HederaSelfDestructOperation extends SelfDestructOperation {
    private final TransactionContext txnCtx;
    private final BiPredicate<Address, MessageFrame> addressValidator;
    private final EvmSigsVerifier sigsVerifier;

    public HederaSelfDestructOperation(
            final GasCalculator gasCalculator,
            final TransactionContext txnCtx,
            final BiPredicate<Address, MessageFrame> addressValidator,
            final EvmSigsVerifier sigsVerifier) {
        super(gasCalculator);
        this.txnCtx = txnCtx;
        this.addressValidator = addressValidator;
        this.sigsVerifier = sigsVerifier;
    }

    @Override
    public OperationResult execute(final MessageFrame frame, final EVM evm) {
        final var updater = (HederaStackedWorldStateUpdater) frame.getWorldUpdater();
        final var beneficiaryAddress = Words.toAddress(frame.getStackItem(0));
        final var toBeDeleted = frame.getRecipientAddress();
        if (!addressValidator.test(beneficiaryAddress, frame)) {
            return reversionWith(null, HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS);
        }
        final var beneficiary = updater.get(beneficiaryAddress);

        final var exceptionalHaltReason = reasonToHalt(toBeDeleted, beneficiaryAddress, updater, frame);
        if (exceptionalHaltReason != null) {
            return reversionWith(beneficiary, exceptionalHaltReason);
        }
        final var tbdNum = numOfMirror(updater.permissivelyUnaliased(toBeDeleted.toArrayUnsafe()));
        final var beneficiaryNum = numOfMirror(updater.permissivelyUnaliased(beneficiaryAddress.toArrayUnsafe()));
        txnCtx.recordBeneficiaryOfDeleted(tbdNum, beneficiaryNum);

        return super.execute(frame, evm);
    }

    @Nullable
    private ExceptionalHaltReason reasonToHalt(
            final Address toBeDeleted,
            final Address beneficiaryAddress,
            final HederaStackedWorldStateUpdater updater,
            final MessageFrame frame) {
        if (toBeDeleted.equals(beneficiaryAddress)) {
            return HederaExceptionalHaltReason.SELF_DESTRUCT_TO_SELF;
        }
        if (updater.contractIsTokenTreasury(toBeDeleted)) {
            return HederaExceptionalHaltReason.CONTRACT_IS_TREASURY;
        }
        if (updater.contractHasAnyBalance(toBeDeleted)) {
            return HederaExceptionalHaltReason.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
        }
        if (updater.contractOwnsNfts(toBeDeleted)) {
            return HederaExceptionalHaltReason.CONTRACT_STILL_OWNS_NFTS;
        }
        if (!HederaOperationUtil.isSigReqMetFor(beneficiaryAddress, frame, sigsVerifier)) {
            return HederaExceptionalHaltReason.INVALID_SIGNATURE;
        }
        return null;
    }

    private OperationResult reversionWith(final Account beneficiary, final ExceptionalHaltReason reason) {
        final long cost = gasCalculator().selfDestructOperationGasCost(beneficiary, Wei.ONE);
        return new OperationResult(cost, reason);
    }
}
