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
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call.PricedResult.gasOnly;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.accountNumberForEvmReference;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.explicitFromHeadlong;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.isLongZeroAddress;
import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.contract.impl.exec.gas.CustomGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.HasCallAttempt;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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

    private static final GasCalculator noCalculationGasCalculator = new CustomGasCalculator();
    private static final ECRECPrecompiledContract ecPrecompile =
            new ECRECPrecompiledContract(noCalculationGasCalculator);

    private static final long HARDCODED_GAS_REQUIREMENT_GAS = 1_500_000L;

    enum SignatureType {
        Invalid,
        EC,
        ED
    }

    // From Ethereum yellow paper (for reference only):
    private static BigInteger secp256k1n =
            new BigInteger("115792089237316195423570985008687907852837564279074904382605163141518161494337");

    public IsAuthorizedRawCall(
            @NonNull final HasCallAttempt attempt,
            final Address address,
            @NonNull final byte[] messageHash,
            @NonNull final byte[] signature) {
        super(attempt.systemContractGasCalculator(), attempt.enhancement(), true);
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

        final var gasRequirement = gasCalculator.gasCostInTinybars(HARDCODED_GAS_REQUIREMENT_GAS);

        final var signatureType =
                switch (signature.length) {
                    case 65 -> SignatureType.EC;
                    case 64 -> SignatureType.ED;
                    default -> SignatureType.Invalid;
                };

        var accountNum = accountNumberForEvmReference(address, nativeOperations());
        if (!isValidAccount(accountNum, signatureType)) {
            return reversionWith(INVALID_ACCOUNT_ID, gasRequirement);
        }

        boolean authorized = true;

        // Validate parameters according to signature type
        if (authorized) {
            authorized = switch (signatureType) {
                case EC -> messageHash.length == 32;
                case ED -> true;
                case Invalid -> false;};
        }

        // Gotta have an account that the given address is an alias for
        final Optional<Account> account;
        if (authorized) {
            // TODO: Given code above account always exists
            account = Optional.ofNullable(enhancement.nativeOperations().getAccount(accountNum));
            authorized = account.isPresent();
        } else account = Optional.empty();

        // If ED then require a key on the account
        final Optional<Key> key;
        if (authorized && signatureType == SignatureType.ED) {
            key = Optional.ofNullable(account.get().key());
            authorized = key.isPresent();
        } else key = Optional.empty();

        // Key must be simple (for isAuthorizedRaw)
        if (authorized && key.isPresent()) {
            final Key ky = key.get();
            final boolean keyIsSimple = !ky.hasKeyList() && !ky.hasThresholdKey();
            authorized = keyIsSimple;
        }

        // Key must match signature type
        if (authorized && key.isPresent()) {
            authorized = switch (signatureType) {
                case ED -> key.get().hasEd25519();
                case EC -> false;
                default -> false;};
        }

        if (authorized) {
            authorized = switch (signatureType) {
                case EC -> validateEcSignature(address, frame);
                case ED -> validateEdSignature(account.get(), key.get());
                default -> false;};
        }

        final var result = authorized
                ? gasOnly(successResult(encodedAuthorizationOutput(authorized), gasRequirement), SUCCESS, true)
                : reversionWith(INVALID_SIGNATURE, gasRequirement);
        return result;
    }

    /** Validate EVM signature - EC key - via ECRECOVER */
    boolean validateEcSignature(@NonNull final Address address, @NonNull final MessageFrame frame) {

        // Call the ECRECOVER precompile directly (meaning, not by executing EVM bytecode, just by
        // using the class provided by Besu).

        final var input = formatEcrecoverInput(messageHash, signature);
        if (input.isEmpty()) return false;
        final var ecrecoverResult = ecPrecompile.computePrecompile(Bytes.wrap(input.get()), frame);

        // ECRECOVER's output here always has status SUCCESS.  But the result Bytes are either empty or the recovered
        // address.
        final var output = ecrecoverResult.getOutput();
        if (output.isEmpty()) return false;

        // ECRECOVER produced an address:  Must match our account's alias address
        final var recoveredAddress = output.slice(12); // LAST 20 bytes are where the EVM address is
        final var recoveredAddressAsInt = recoveredAddress.toBigInteger(ByteOrder.LITTLE_ENDIAN);
        final var givenAddressAsInt = address.value();
        final var addressesMatch = givenAddressAsInt.equals(recoveredAddressAsInt);

        return addressesMatch;
    }

    /** Validate (native Hedera) ED signature */
    boolean validateEdSignature(@NonNull final Account account, @NonNull final Key key) {
        return false; // TBD
    }

    /** Encode the _output_ of our system contract: it's a boolean */
    @NonNull
    ByteBuffer encodedAuthorizationOutput(final boolean authorized) {
        return IsAuthorizedRawTranslator.IS_AUTHORIZED_RAW.getOutputs().encodeElements(authorized);
    }

    /** Format our message hash and signature for input to the ECRECOVER precompile */
    @NonNull
    Optional<byte[]> formatEcrecoverInput(@NonNull final byte[] messageHash, @NonNull final byte[] signature) {
        // Signature:
        //   [ 0;  31]  r
        //   [32;  63]  s
        //   [64;  64]  v (but possibly 0..1 instead of 27..28)

        // From evm.codes, input to ECRECOVER:
        //   [ 0;  31]  hash
        //   [32;  63]  v == recovery identifier (27 or 28)
        //   [64;  95]  r == x-value ∈ (0, secp256k1n);
        //   [96; 127]  s ∈ (0; sep256k1n ÷ 2 + 1)

        if (messageHash.length != 32 || signature.length != 65) return Optional.empty();
        final var ov = reverseV(signature[64]);
        if (ov.isEmpty()) return Optional.empty();

        byte[] r = new byte[128];
        System.arraycopy(messageHash, 0, r, 0, 32);
        System.arraycopy(signature, 0, r, 64, 32);
        System.arraycopy(signature, 32, r, 96, 32);
        byte v = signature[64];
        r[63] = ov.get();

        return Optional.of(r);
    }

    /** Make sure v ∈ {27, 28} - but after EIP-155 it might come in with a chain id ... */
    Optional<Byte> reverseV(final byte v) {

        // Odd, seems that the specification of ECRECOVER wasn't updated for EIP-155 - Besu
        // (at least) really wants v ∈ {27, 28}.

        if (v == 0 || v == 1) return Optional.of((byte) (v + 27));
        if (v == 27 || v == 28) return Optional.of(v);
        if (v >= 35) {
            // EIP-155 case - low bit is _opposite_ of parity (35 is magic number for encoding chain id)
            final var parity = ~v & 1;
            return Optional.of((byte) (parity + 27));
        }
        return Optional.empty();
    }

    @NonNull
    boolean isValidAccount(final long accountNum, @NonNull final SignatureType signatureType) {
        // If the account num is negative, it is invalid
        if (accountNum < 0) {
            return false;
        }

        // If the signature is for an ecdsa key, the HIP states that the account must have an evm address rather than a
        // long zero address
        if (signatureType == SignatureType.EC) {
            return !isLongZeroAddress(explicitFromHeadlong(address));
        }

        return true;
    }
}
