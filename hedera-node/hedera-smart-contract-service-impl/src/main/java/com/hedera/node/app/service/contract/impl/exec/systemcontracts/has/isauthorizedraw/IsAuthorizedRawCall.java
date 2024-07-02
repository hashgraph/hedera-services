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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.isauthorizedraw;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call.PricedResult.gasOnly;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.accountNumberForEvmReference;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.explicitFromHeadlong;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.isLongZeroAddress;
import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.service.contract.impl.exec.gas.CustomGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.HasCallAttempt;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.ByteBuffer;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.precompile.ECRECPrecompiledContract;

/** HIP-632 method: `isAuthorizedRaw` */
public class IsAuthorizedRawCall extends AbstractCall {

    private final VerificationStrategy verificationStrategy;
    private final AccountID sender;
    private final Address address;
    private final byte[] messageHash;
    private final byte[] signature;

    private GasCalculator noCalculationGasCalculator = new CustomGasCalculator();

    private static long HARDCODED_GAS_REQUIREMENT_GAS = 1_500_000L;

    public IsAuthorizedRawCall(
            @NonNull final HasCallAttempt attempt,
            final Address address,
            @NonNull final byte[] messageHash,
            @NonNull final byte[] signature) {
        super(attempt.systemContractGasCalculator(), attempt.enhancement(), false);
        this.address = requireNonNull(address);
        this.messageHash = requireNonNull(messageHash);
        this.signature = requireNonNull(signature);

        this.verificationStrategy = attempt.defaultVerificationStrategy();
        this.sender = attempt.senderId();
    }

    @NonNull
    @Override
    public PricedResult execute(@NonNull final MessageFrame frame) {
        requireNonNull(frame);

        var accountNum = accountNumberForEvmReference(address, nativeOperations());
        if (isInvalidAccount(accountNum)) {
            return gasOnly(
                    revertResult(INVALID_ACCOUNT_ID, gasCalculator.viewGasRequirement()), INVALID_ACCOUNT_ID, true);
        }

        boolean authorized = true;

        final Optional<byte[]> key = getAccountKey(accountNum);
        if (key.isEmpty()) authorized = false;

        if (authorized) {
            authorized = switch (signature.length) {
                case 65 -> validateEcSignature(key);
                case 64 -> validateEdSignature(key);
                default -> false;};
        }

        final var gasRequirement = gasCalculator.gasCostInTinybars(HARDCODED_GAS_REQUIREMENT_GAS);

        final var result = authorized
                ? gasOnly(successResult(encodedAuthorizationOutput(authorized), gasRequirement), SUCCESS, false)
                : reversionWith(INVALID_SIGNATURE, gasRequirement);
        return result;
    }

    /** Return the one-and-only simple key for the Hedera address/account */
    @NonNull
    Optional<byte[]> getAccountKey(long address) {
        return Optional.empty();
    }

    /** Validate EVM signature - EC key - via ECRECOVER */
    private boolean validateEcSignature(@NonNull Optional<byte[]> key) {
        final var ecPrecompile = new ECRECPrecompiledContract(noCalculationGasCalculator);
        final Bytes input = formatEcrecoverInput(messageHash, signature);
        return true;
    }

    /** Validate (native Hedera) ED signature */
    private boolean validateEdSignature(@NonNull Optional<byte[]> key) {
        return false;
    }

    @NonNull
    ByteBuffer encodedAuthorizationOutput(final boolean authorized) {
        return IsAuthorizedRawTranslator.IS_AUTHORIZED_RAW.getOutputs().encodeElements(authorized);
    }

    @NonNull
    Bytes formatEcrecoverInput(@NonNull final byte[] messageHash, @NonNull final byte[] signature) {
        return null;
    }

    @NonNull
    boolean isInvalidAccount(final long accountNum) {
        // If the account num is negative, it is invalid
        if (accountNum < 0) {
            return true;
        }

        // If the signature is for an ecdsa key, the HIP states that the account must have an evm address rather than a
        // long zero address
        if (signature.length == 65) {
            return isLongZeroAddress(explicitFromHeadlong(address));
        }

        return false;
    }
}
