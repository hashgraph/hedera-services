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

package com.hedera.node.app.service.contract.impl.utils;

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.exec.processors.ProcessorModule.EVM_ADDRESS_SIZE;
import static com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations.MISSING_ENTITY_NUMBER;
import static com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations.NON_CANONICAL_REFERENCE_NUMBER;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.proxyUpdaterFor;
import static com.hedera.node.app.spi.key.KeyUtils.isEmpty;
import static com.swirlds.common.utility.CommonUtils.unhex;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.contract.ContractLoginfo;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.streams.ContractStateChange;
import com.hedera.hapi.streams.ContractStateChanges;
import com.hedera.hapi.streams.StorageChange;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleHederaNativeOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations;
import com.hedera.node.app.service.contract.impl.state.StorageAccesses;
import com.hedera.node.app.spi.workflows.HandleException;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

/**
 * Some utility methods for converting between PBJ and Besu types and the various kinds of addresses and ids.
 */
public class ConversionUtils {
    public static final long EVM_ADDRESS_LENGTH_AS_LONG = 20L;
    public static final int EVM_ADDRESS_LENGTH_AS_INT = 20;
    public static final int NUM_LONG_ZEROS = 12;
    public static final long FEE_SCHEDULE_UNITS_PER_TINYCENT = 1000;
    private static final BigInteger MIN_LONG_VALUE = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger MAX_LONG_VALUE = BigInteger.valueOf(Long.MAX_VALUE);

    private ConversionUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * Given a list of {@link com.esaulpaugh.headlong.abi.Address}, returns their implied token ids.
     *
     * @param tokenAddresses the {@link com.esaulpaugh.headlong.abi.Address}es
     * @return the implied token ids
     */
    public static TokenID[] asTokenIds(@NonNull final com.esaulpaugh.headlong.abi.Address... tokenAddresses) {
        requireNonNull(tokenAddresses);
        final TokenID[] tokens = new TokenID[tokenAddresses.length];
        for (int i = 0; i < tokens.length; i++) {
            tokens[i] = asTokenId(tokenAddresses[i]);
        }
        return tokens;
    }

    /**
     * Given a numeric {@link AccountID}, returns its equivalent contract id.
     *
     * @param accountId the numeric {@link AccountID}
     * @return the equivalent account id
     */
    public static ContractID asNumericContractId(@NonNull final AccountID accountId) {
        return ContractID.newBuilder()
                .contractNum(accountId.accountNumOrThrow())
                .build();
    }

    /**
     * Given a {@link com.esaulpaugh.headlong.abi.Address}, returns its implied token id.
     *
     * <p><b>IMPORTANT:</b> Mono-service ignores the shard and realm, c.f. De
     * codingFacade#convertAddressBytesToTokenID(), so we continue to do that here; might
     * want to revisit this later
     *
     * @param address the {@link com.esaulpaugh.headlong.abi.Address}
     * @return the implied token id
     */
    public static TokenID asTokenId(@NonNull final com.esaulpaugh.headlong.abi.Address address) {
        return TokenID.newBuilder()
                .tokenNum(numberOfLongZero(explicitFromHeadlong(address)))
                .build();
    }

    /**
     * Given a validated {@link ContractCreateTransactionBody} and its pending id, returns the
     * corresponding {@link CryptoCreateTransactionBody} to dispatch.
     *
     * @param pendingId the pending id
     * @param body the {@link ContractCreateTransactionBody}
     * @return the corresponding {@link CryptoCreateTransactionBody}
     */
    public static CryptoCreateTransactionBody accountCreationFor(
            @NonNull final ContractID pendingId,
            @Nullable final com.hedera.pbj.runtime.io.buffer.Bytes evmAddress,
            @NonNull final ContractCreateTransactionBody body) {
        requireNonNull(body);
        requireNonNull(pendingId);
        final var builder = CryptoCreateTransactionBody.newBuilder()
                .maxAutomaticTokenAssociations(body.maxAutomaticTokenAssociations())
                .declineReward(body.declineReward())
                .memo(body.memo());
        if (body.hasAutoRenewPeriod()) {
            builder.autoRenewPeriod(body.autoRenewPeriodOrThrow());
        }
        if (body.hasStakedNodeId()) {
            builder.stakedNodeId(body.stakedNodeIdOrThrow());
        } else if (body.hasStakedAccountId()) {
            builder.stakedAccountId(body.stakedAccountIdOrThrow());
        }
        if (body.hasAdminKey() && !isEmpty(body.adminKeyOrThrow())) {
            builder.key(body.adminKeyOrThrow());
        } else {
            builder.key(Key.newBuilder().contractID(pendingId));
        }
        if (evmAddress != null) {
            builder.alias(evmAddress);
        }
        return builder.build();
    }

    /**
     * Given a {@link BigInteger}, returns either its long value or zero if it is out-of-range.
     *
     * @param value the {@link BigInteger}
     * @return its long value or zero if it is out-of-range
     */
    public static long asExactLongValueOrZero(@NonNull final BigInteger value) {
        requireNonNull(value);
        if (value.compareTo(MIN_LONG_VALUE) < 0 || value.compareTo(MAX_LONG_VALUE) > 0) {
            return 0L;
        }
        return value.longValueExact();
    }

    /**
     * Given a {@link AccountID}, returns its address as a headlong address.
     * @param accountID
     * @return
     */
    public static com.esaulpaugh.headlong.abi.Address headlongAddressOf(@NonNull final AccountID accountID) {
        requireNonNull(accountID);
        final var integralAddress = accountID.hasAccountNum()
                ? asEvmAddress(accountID.accountNum())
                : accountID.alias().toByteArray();
        return asHeadlongAddress(integralAddress);
    }

    /**
     * Given a {@link ContractID}, returns its address as a headlong address.
     * @param contractId
     * @return
     */
    public static com.esaulpaugh.headlong.abi.Address headlongAddressOf(@NonNull final ContractID contractId) {
        requireNonNull(contractId);
        final var integralAddress = contractId.hasContractNum()
                ? asEvmAddress(contractId.contractNum())
                : contractId.evmAddress().toByteArray();
        return asHeadlongAddress(integralAddress);
    }

    /**
     * Given a {@link TokenID}, returns its address as a headlong address.
     * @param tokenId
     * @return
     */
    public static com.esaulpaugh.headlong.abi.Address headlongAddressOf(@NonNull final TokenID tokenId) {
        requireNonNull(tokenId);
        return asHeadlongAddress(asEvmAddress(tokenId.tokenNum()));
    }

    /**
     * Given an account, returns its "priority" address as a headlong address.
     *
     * @param account the account
     * @return the headlong address
     */
    public static com.esaulpaugh.headlong.abi.Address headlongAddressOf(@NonNull final Account account) {
        requireNonNull(account);
        return asHeadlongAddress(explicitAddressOf(account));
    }

    /**
     * Given an explicit 20-byte array, converts it to a headlong address.
     *
     * @param explicit the explicit address
     * @return the headlong address
     */
    public static com.esaulpaugh.headlong.abi.Address asHeadlongAddress(@NonNull final byte[] explicit) {
        requireNonNull(explicit);
        final var integralAddress = Bytes.wrap(explicit).toUnsignedBigInteger();
        return com.esaulpaugh.headlong.abi.Address.wrap(
                com.esaulpaugh.headlong.abi.Address.toChecksumAddress(integralAddress));
    }

    /**
     * Given a list of Besu {@link Log}s, converts them to a list of PBJ {@link ContractLoginfo}.
     *
     * @param logs the Besu {@link Log}s
     * @return the PBJ {@link ContractLoginfo}s
     */
    public static List<ContractLoginfo> pbjLogsFrom(@NonNull final List<Log> logs) {
        final List<ContractLoginfo> pbjLogs = new ArrayList<>();
        for (final var log : logs) {
            pbjLogs.add(pbjLogFrom(log));
        }
        return pbjLogs;
    }

    /**
     * Returns the 2-byte chain id as a byte array.
     *
     * @param chainId the chain id
     * @return the chain id as a byte array
     */
    public static byte[] asChainIdBytes(final int chainId) {
        final var bytes = new byte[2];
        bytes[0] = (byte) (chainId >> 8);
        bytes[1] = (byte) chainId;
        return bytes;
    }

    /**
     * Wraps the first 32 bytes of the given SHA-384 {@link com.swirlds.common.crypto.Hash hash} in a Besu {@link Hash}.
     *
     * @param sha384Hash the SHA-384 hash
     * @return the first 32 bytes as a Besu {@link Hash}
     */
    public static org.hyperledger.besu.datatypes.Hash ethHashFrom(
            @NonNull final com.hedera.pbj.runtime.io.buffer.Bytes sha384Hash) {
        requireNonNull(sha384Hash);
        final byte[] prefixBytes = new byte[32];
        sha384Hash.getBytes(0, prefixBytes, 0, prefixBytes.length);
        return org.hyperledger.besu.datatypes.Hash.wrap(Bytes32.wrap(prefixBytes));
    }

    /**
     * Given a list of {@link StorageAccesses}, converts them to a PBJ {@link ContractStateChanges}.
     *
     * @param storageAccesses the {@link StorageAccesses}
     * @return the PBJ {@link ContractStateChanges}
     */
    public static ContractStateChanges asPbjStateChanges(@NonNull final List<StorageAccesses> storageAccesses) {
        final List<ContractStateChange> allStateChanges = new ArrayList<>();
        for (final var storageAccess : storageAccesses) {
            final List<StorageChange> changes = new ArrayList<>();
            for (final var access : storageAccess.accesses()) {
                changes.add(new StorageChange(
                        tuweniToPbjBytes(access.key()),
                        tuweniToPbjBytes(access.value()),
                        access.isReadOnly() ? null : tuweniToPbjBytes(requireNonNull(access.writtenValue()))));
            }
            allStateChanges.add(new ContractStateChange(
                    ContractID.newBuilder()
                            .contractNum(storageAccess.contractNumber())
                            .build(),
                    changes));
        }
        return new ContractStateChanges(allStateChanges);
    }

    /**
     * Given a Besu {@link Log}, converts it a PBJ {@link ContractLoginfo}.
     *
     * @param log the Besu {@link Log}
     * @return the PBJ {@link ContractLoginfo}
     */
    public static ContractLoginfo pbjLogFrom(@NonNull final Log log) {
        final var loggerNumber = numberOfLongZero(log.getLogger());
        final List<com.hedera.pbj.runtime.io.buffer.Bytes> loggedTopics = new ArrayList<>();
        for (final var topic : log.getTopics()) {
            loggedTopics.add(tuweniToPbjBytes(topic));
        }
        return ContractLoginfo.newBuilder()
                .contractID(ContractID.newBuilder().contractNum(loggerNumber))
                .data(tuweniToPbjBytes(log.getData()))
                .topic(loggedTopics)
                .bloom(bloomFor(log))
                .build();
    }

    /**
     * Given a {@link MessageFrame}, returns the long-zero address of its {@code contract} address.
     *
     * @param frame the {@link MessageFrame}
     * @return the long-zero address of the {@code contract} address
     */
    public static long hederaIdNumOfContractIn(@NonNull final MessageFrame frame) {
        requireNonNull(frame);
        return hederaIdNumberIn(frame, frame.getContractAddress());
    }

    /**
     * Given a {@link MessageFrame}, returns the long-zero address of its {@code originator} address.
     *
     * @param frame the {@link MessageFrame}
     * @return the long-zero address of the {@code originator} address
     */
    public static long hederaIdNumOfOriginatorIn(@NonNull final MessageFrame frame) {
        requireNonNull(frame);
        return hederaIdNumberIn(frame, frame.getOriginatorAddress());
    }

    /**
     * Given a {@link MessageFrame}, returns the id number of the given address's Hedera id.
     *
     * @param frame   the {@link MessageFrame}
     * @param address the address to get the id number of
     * @return the id number of the given address's Hedera id
     */
    public static long hederaIdNumberIn(@NonNull final MessageFrame frame, @NonNull final Address address) {
        return isLongZero(address)
                ? numberOfLongZero(address)
                : proxyUpdaterFor(frame).getHederaContractId(address).contractNumOrThrow();
    }

    /**
     * Given a {@link MessageFrame}, returns the long-zero address of its {@code recipient} address.
     *
     * @param frame the {@link MessageFrame}
     * @return the long-zero address of the {@code recipient} address
     */
    public static Address longZeroAddressOfRecipient(@NonNull final MessageFrame frame) {
        return longZeroAddressIn(frame, frame.getRecipientAddress());
    }

    /**
     * Given an EVM address (possibly long-zero), returns the number of the corresponding Hedera entity
     * within the given {@link HandleHederaNativeOperations}; or {@link HederaNativeOperations#MISSING_ENTITY_NUMBER}
     * if the address does not correspond to a known Hedera entity; or {@link HederaNativeOperations#NON_CANONICAL_REFERENCE_NUMBER}
     * if the address references an account by its "non-priority" long-zero address.
     *
     * @param address       the EVM address
     * @param nativeOperations the {@link HandleHederaNativeOperations} to use for resolving aliases
     * @return the number of the corresponding Hedera entity, if it exists and has this priority address
     */
    public static long accountNumberForEvmReference(
            @NonNull final com.esaulpaugh.headlong.abi.Address address,
            @NonNull final HederaNativeOperations nativeOperations) {
        final var explicit = explicitFromHeadlong(address);
        final var number = maybeMissingNumberOf(explicit, nativeOperations);
        if (number == MISSING_ENTITY_NUMBER) {
            return MISSING_ENTITY_NUMBER;
        } else {
            final var account = nativeOperations.getAccount(number);
            if (account == null) {
                return MISSING_ENTITY_NUMBER;
            } else if (!Arrays.equals(explicit, explicitAddressOf(account))) {
                return NON_CANONICAL_REFERENCE_NUMBER;
            }
            return number;
        }
    }

    /**
     * Given an EVM address (possibly long-zero), returns the number of the corresponding Hedera entity
     * within the given {@link HandleHederaNativeOperations}; or {@link HederaNativeOperations#MISSING_ENTITY_NUMBER}
     * if the address is not long-zero and does not correspond to a known Hedera entity.
     *
     * @param address       the EVM address
     * @param nativeOperations the {@link HandleHederaNativeOperations} to use for resolving aliases
     * @return the number of the corresponding Hedera entity, if it exists
     */
    public static long maybeMissingNumberOf(
            @NonNull final Address address, @NonNull final HederaNativeOperations nativeOperations) {
        return maybeMissingNumberOf(address.toArrayUnsafe(), nativeOperations);
    }

    /**
     * Given a long-zero EVM address, returns the implied Hedera entity number.
     *
     * @param address the EVM address
     * @return the implied Hedera entity number
     */
    public static long numberOfLongZero(@NonNull final Address address) {
        return address.toUnsignedBigInteger().longValueExact();
    }

    /**
     * Given an EVM address, returns whether it is long-zero.
     *
     * @param address the EVM address
     * @return whether it is long-zero
     */
    public static boolean isLongZero(@NonNull final Address address) {
        return isLongZeroAddress(address.toArrayUnsafe());
    }

    /**
     * Converts an EVM address to a PBJ {@link com.hedera.pbj.runtime.io.buffer.Bytes} alias.
     *
     * @param address the EVM address
     * @return the PBJ bytes alias
     */
    public static com.hedera.pbj.runtime.io.buffer.Bytes aliasFrom(@NonNull final Address address) {
        return com.hedera.pbj.runtime.io.buffer.Bytes.wrap(address.toArrayUnsafe());
    }

    /**
     * Converts a number to a long zero address.
     *
     * @param number the number to convert
     * @return the long zero address
     */
    public static Address asLongZeroAddress(final long number) {
        return Address.wrap(Bytes.wrap(asEvmAddress(number)));
    }

    /**
     * Converts a Tuweni bytes to a PBJ bytes.
     *
     * @param bytes the Tuweni bytes
     * @return the PBJ bytes
     */
    public static @NonNull com.hedera.pbj.runtime.io.buffer.Bytes tuweniToPbjBytes(@NonNull final Bytes bytes) {
        return com.hedera.pbj.runtime.io.buffer.Bytes.wrap(requireNonNull(bytes).toArrayUnsafe());
    }

    /**
     * Converts an EVM address to a PBJ {@link ContractID} with alias instead of id number.
     *
     * @param address the EVM address
     * @return the PBJ {@link ContractID}
     */
    public static ContractID asEvmContractId(@NonNull final Address address) {
        return ContractID.newBuilder().evmAddress(tuweniToPbjBytes(address)).build();
    }

    /**
     * Converts a long-zero address to a PBJ {@link ContractID} with id number instead of alias.
     *
     * @param address the EVM address
     * @return the PBJ {@link ContractID}
     */
    public static ContractID asNumberedContractId(@NonNull final Address address) {
        if (!isLongZero(address)) {
            throw new IllegalArgumentException("Cannot extract id number from address " + address);
        }
        return ContractID.newBuilder().contractNum(numberOfLongZero(address)).build();
    }

    /**
     * Throws a {@link HandleException} if the given status is not {@link ResponseCodeEnum#SUCCESS}.
     *
     * @param status the status
     */
    public static void throwIfUnsuccessful(@NonNull final ResponseCodeEnum status) {
        if (status != SUCCESS) {
            throw new HandleException(status);
        }
    }

    /**
     * Converts a PBJ bytes to Tuweni bytes.
     *
     * @param bytes the PBJ bytes
     * @return the Tuweni bytes
     */
    public static @NonNull Bytes pbjToTuweniBytes(@NonNull final com.hedera.pbj.runtime.io.buffer.Bytes bytes) {
        if (bytes.length() == 0) {
            return Bytes.EMPTY;
        }
        return Bytes.wrap(clampedBytes(bytes, 0, Integer.MAX_VALUE));
    }

    /**
     * Returns whether the given alias is an EVM address.
     *
     * @param alias the alias
     * @return whether it is an EVM address
     */
    public static boolean isEvmAddress(@Nullable final com.hedera.pbj.runtime.io.buffer.Bytes alias) {
        return alias != null && alias.length() == EVM_ADDRESS_LENGTH_AS_LONG;
    }

    /**
     * Converts a PBJ bytes to a Besu address.
     *
     * @param bytes the PBJ bytes
     * @return the Besu address
     * @throws IllegalArgumentException if the bytes are not 20 bytes long
     */
    public static @NonNull Address pbjToBesuAddress(@NonNull final com.hedera.pbj.runtime.io.buffer.Bytes bytes) {
        return Address.wrap(Bytes.wrap(clampedBytes(bytes, EVM_ADDRESS_LENGTH_AS_INT, EVM_ADDRESS_LENGTH_AS_INT)));
    }

    /**
     * Converts a PBJ bytes to a Besu hash.
     *
     * @param bytes the PBJ bytes
     * @return the Besu hash
     * @throws IllegalArgumentException if the bytes are not 32 bytes long
     */
    public static @NonNull Hash pbjToBesuHash(@NonNull final com.hedera.pbj.runtime.io.buffer.Bytes bytes) {
        return Hash.wrap(Bytes32.wrap(clampedBytes(bytes, 32, 32)));
    }

    /**
     * Converts a PBJ bytes to a Tuweni UInt256.
     *
     * @param bytes the PBJ bytes
     * @return the Tuweni bytes
     * @throws IllegalArgumentException if the bytes are more than 32 bytes long
     */
    public static @NonNull UInt256 pbjToTuweniUInt256(@NonNull final com.hedera.pbj.runtime.io.buffer.Bytes bytes) {
        return (bytes.length() == 0) ? UInt256.ZERO : UInt256.fromBytes(Bytes32.wrap(clampedBytes(bytes, 0, 32)));
    }

    /**
     * Returns the PBJ bloom for a list of Besu {@link Log}s.
     *
     * @param logs the Besu {@link Log}s
     * @return the PBJ bloom
     */
    public static com.hedera.pbj.runtime.io.buffer.Bytes bloomForAll(@NonNull final List<Log> logs) {
        return com.hedera.pbj.runtime.io.buffer.Bytes.wrap(
                LogsBloomFilter.builder().insertLogs(logs).build().toArray());
    }

    private static byte[] clampedBytes(
            @NonNull final com.hedera.pbj.runtime.io.buffer.Bytes bytes, final int minLength, final int maxLength) {
        final var length = Math.toIntExact(requireNonNull(bytes).length());
        if (length < minLength) {
            throw new IllegalArgumentException("Expected at least " + minLength + " bytes, got " + bytes);
        }
        if (length > maxLength) {
            throw new IllegalArgumentException("Expected at most " + maxLength + " bytes, got " + bytes);
        }
        final byte[] data = new byte[length];
        bytes.getBytes(0, data);
        return data;
    }

    /**
     * Given a long entity number, returns its 20-byte EVM address.
     *
     * @param num the entity number
     * @return its 20-byte EVM address
     */
    public static byte[] asEvmAddress(final long num) {
        final byte[] evmAddress = new byte[20];
        copyToLeftPaddedByteArray(num, evmAddress);
        return evmAddress;
    }

    private static void copyToLeftPaddedByteArray(long value, final byte[] dest) {
        for (int i = 7, j = dest.length - 1; i >= 0; i--, j--) {
            dest[j] = (byte) (value & 0xffL);
            value >>= 8;
        }
    }

    /**
     * Given an explicit 20-byte array, returns whether it is a long-zero address.
     *
     * @param explicit the explicit 20-byte array
     * @return whether it is a long-zero address
     */
    public static boolean isLongZeroAddress(final byte[] explicit) {
        for (int i = 0; i < NUM_LONG_ZEROS; i++) {
            if (explicit[i] != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Given a headlong address, returns its explicit 20-byte array.
     *
     * @param address the headlong address
     * @return its explicit 20-byte array
     */
    public static byte[] explicitFromHeadlong(@NonNull final com.esaulpaugh.headlong.abi.Address address) {
        return unhex(address.toString().substring(2));
    }

    /**
     * Given an explicit 20-byte addresss, returns its long value.
     *
     * @param explicit the explicit 20-byte address
     * @return its long value
     */
    public static long numberOfLongZero(@NonNull final byte[] explicit) {
        return longFrom(
                explicit[12],
                explicit[13],
                explicit[14],
                explicit[15],
                explicit[16],
                explicit[17],
                explicit[18],
                explicit[19]);
    }

    // too many arguments
    @SuppressWarnings("java:S107")
    private static long longFrom(
            final byte b1,
            final byte b2,
            final byte b3,
            final byte b4,
            final byte b5,
            final byte b6,
            final byte b7,
            final byte b8) {
        return (b1 & 0xFFL) << 56
                | (b2 & 0xFFL) << 48
                | (b3 & 0xFFL) << 40
                | (b4 & 0xFFL) << 32
                | (b5 & 0xFFL) << 24
                | (b6 & 0xFFL) << 16
                | (b7 & 0xFFL) << 8
                | (b8 & 0xFFL);
    }

    private static com.hedera.pbj.runtime.io.buffer.Bytes bloomFor(@NonNull final Log log) {
        return com.hedera.pbj.runtime.io.buffer.Bytes.wrap(
                LogsBloomFilter.builder().insertLog(log).build().toArray());
    }

    private static Address longZeroAddressIn(@NonNull final MessageFrame frame, @NonNull final Address address) {
        return isLongZero(address)
                ? address
                : asLongZeroAddress(
                        proxyUpdaterFor(frame).getHederaContractId(address).contractNumOrThrow());
    }

    private static long maybeMissingNumberOf(
            @NonNull final byte[] explicit, @NonNull final HederaNativeOperations nativeOperations) {
        if (isLongZeroAddress(explicit)) {
            return longFrom(
                    explicit[12],
                    explicit[13],
                    explicit[14],
                    explicit[15],
                    explicit[16],
                    explicit[17],
                    explicit[18],
                    explicit[19]);
        } else {
            final var alias = com.hedera.pbj.runtime.io.buffer.Bytes.wrap(explicit);
            return nativeOperations.resolveAlias(alias);
        }
    }

    private static byte[] explicitAddressOf(@NonNull final Account account) {
        requireNonNull(account);
        return account.alias().length() == EVM_ADDRESS_SIZE
                ? account.alias().toByteArray()
                : asEvmAddress(account.accountIdOrThrow().accountNumOrThrow());
    }

    /**
     * Given a headlong address, converts it to a Besu {@link Address}.
     *
     * @param address the headlong address
     * @return the Besu {@link Address}
     */
    public static Address fromHeadlongAddress(@NonNull final com.esaulpaugh.headlong.abi.Address address) {
        requireNonNull(address);
        return Address.fromHexString(address.toString());
    }

    public static long fromTinycentsToTinybars(final ExchangeRate exchangeRate, final long tinycents) {
        return fromAToB(BigInteger.valueOf(tinycents), exchangeRate.hbarEquiv(), exchangeRate.centEquiv())
                .longValueExact();
    }

    public static @NonNull BigInteger fromAToB(@NonNull final BigInteger aAmount, final int bEquiv, final int aEquiv) {
        return aAmount.multiply(BigInteger.valueOf(bEquiv)).divide(BigInteger.valueOf(aEquiv));
    }
}
