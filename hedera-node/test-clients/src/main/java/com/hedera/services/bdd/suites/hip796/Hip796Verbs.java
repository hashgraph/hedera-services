/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.hip796;

import static com.hedera.services.bdd.suites.HapiSuite.FUNGIBLE_INITIAL_SUPPLY;
import static com.hedera.services.bdd.suites.HapiSuite.TOKEN_UNDER_TEST;
import static java.util.stream.Collectors.toCollection;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hedera.services.bdd.spec.queries.token.HapiGetTokenInfo;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.suites.hip796.operations.TokenAttributeNames;
import com.hedera.services.bdd.suites.hip796.operations.TokenDefOperation;
import com.hedera.services.bdd.suites.hip796.operations.TokenFeature;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.stream.Stream;

/**
 * A family of {@link HapiSpecOperation}'s specialized for HIP-796.
 * <p>
 * There are two major differences between these verbs and the existing verbs found in e.g., {@link TxnVerbs}:
 * <ol>
 *     <li><i>Implied get-or-create semantics</i> - Unlike an operation factory like {@link TxnVerbs#tokenCreate(String)},
 *     which requires the new token's auto-renew and treasury accounts to already exist in the {@link HapiSpecRegistry},
 *     these verbs will create the accounts if they don't already exist.
 *     </li>
 *     <li><i>Opinionated naming conventions</i> - Instead of every supply key needed a custom name, we will
 *     make the supply key for token {@code "Acme"} always be {@code "Acme-SupplyKey"}; and so on. The name
 *     for partition "Red" of token "Charlie" will always be "Charlie|Red". And so on</li>
 * </ol>
 */
public class Hip796Verbs {
    private Hip796Verbs() {
        throw new UnsupportedOperationException();
    }

    /**
     * Several functions that should be provided by the {@link HapiSpec} HIP-796 management contract implementation.
     */
    public static final Function SAME_USER_PARTITION_MOVE_UNITS_FUNCTION =
            new Function("moveBetweenSameUserPartitions(address,address,address,int64)");

    public static final Function DIFFERENT_USER_PARTITION_MOVE_UNITS_FUNCTION =
            new Function("moveBetweenDifferentUserPartitions(address,address,address,address,int64)");
    public static final Function CREATE_PARTITION_FUNCTION = new Function("createPartition(address,string,string)");
    public static final Function DELETE_PARTITION_FUNCTION = new Function("deletePartition(address)");
    public static final Function UPDATE_PARTITION_FUNCTION =
            new Function("deletePartition(address,bool,string,bool,string)");
    public static final Function ROTATE_KEY_FUNCTION =
            new Function("rotateKey((uint256,(bool,address,bytes,bytes,address))");
    public static final Function REMOVE_KEY_FUNCTION = new Function("removeKey(uint256)");
    /**
     * Bit-masks that identify the HIP-796 key types.
     */
    public static final BigInteger LOCK_KEY_TYPE = BigInteger.valueOf(1 << 7);

    public static final BigInteger PARTITION_KEY_TYPE = BigInteger.valueOf(1 << 8);
    public static final BigInteger PARTITION_MOVE_KEY_TYPE = BigInteger.valueOf(1 << 9);

    // --- Token definition factories ---

    /**
     * Creates a non-fungible token with the given features and related entities and the default name
     * ({@code TOKEN_UNDER_TEST}).
     *
     * @param features the features to use
     * @return a {@link TokenDefOperation} that can be used to define the token
     */
    public static TokenDefOperation nonFungibleTokenWithFeatures(@NonNull final TokenFeature... features) {
        return nonFungibleTokenWithFeatures(TOKEN_UNDER_TEST, features);
    }

    /**
     * Creates a non-fungible token with the given features and related entities and the given name.
     *
     * @param token the token to create
     * @param features the features to use
     * @return a {@link TokenDefOperation} that can be used to define the token
     */
    public static TokenDefOperation nonFungibleTokenWithFeatures(
            @NonNull final String token, @NonNull final TokenFeature... features) {
        // It never makes sense to create a non-fungible token without a supply key
        final var featuresWithSupplyKey = Stream.concat(
                        Stream.of(TokenFeature.SUPPLY_MANAGEMENT), Arrays.stream(features))
                .collect(toCollection(() -> EnumSet.noneOf(TokenFeature.class)))
                .toArray(TokenFeature[]::new);
        return tokenWithFeatures(token, TokenType.NON_FUNGIBLE_UNIQUE, featuresWithSupplyKey);
    }

    /**
     * Creates a fungible token with the given features and related entities and the default name
     * ({@code TOKEN_UNDER_TEST}).
     *
     * @param features the features to use
     * @return a {@link TokenDefOperation} that can be used to define the token
     */
    public static TokenDefOperation fungibleTokenWithFeatures(@NonNull final TokenFeature... features) {
        return fungibleTokenWithFeatures(TOKEN_UNDER_TEST, features);
    }

    /**
     * Creates a fungible token with the given features and related entities and the given name.
     *
     * @param token the token to create
     * @param features the features to use
     * @return a {@link TokenDefOperation} that can be used to define the token
     */
    public static TokenDefOperation fungibleTokenWithFeatures(
            @NonNull final String token, @NonNull final TokenFeature... features) {
        return tokenWithFeatures(token, TokenType.FUNGIBLE_COMMON, features);
    }

    // --- Lock management verbs ---
    /**
     * Locks the given amount of units of the given token in the given account.
     *
     * @param account the account to lock the units in
     * @param token the token to lock the units of
     * @param amount the amount of units to lock
     * @return a {@link HapiTxnOp} that can be used to lock the units
     */
    public static HapiTxnOp lockUnits(@NonNull final String account, @NonNull final String token, final long amount) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Unlocks the given amount of units of the given token in the given account.
     *
     * @param account the account to unlock the units in
     * @param token the token to unlock the units of
     * @param amount the amount of units to unlock
     * @return a {@link HapiTxnOp} that can be used to unlock the units
     */
    public static HapiTxnOp unlockUnits(@NonNull final String account, @NonNull final String token, final long amount) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Locks the given serial numbers of the given token in the given account.
     *
     * @param account the account to lock the serial numbers in
     * @param token the token to lock the serial numbers of
     * @param serialNos the serial numbers to lock
     * @return a {@link HapiTxnOp} that can be used to lock the serial numbers
     */
    public static HapiSpecOperation lockNfts(
            @NonNull final String account, @NonNull final String token, final long... serialNos) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Unlocks the given serial numbers of the given token in the given account.
     *
     * @param account the account to unlock the serial numbers in
     * @param token the token to unlock the serial numbers of
     * @param serialNos the serial numbers to unlock
     * @return a {@link HapiTxnOp} that can be used to unlock the serial numbers
     */
    public static HapiSpecOperation unlockNfts(
            @NonNull final String account, @NonNull final String token, final long... serialNos) {
        throw new AssertionError("Not implemented");
    }

    // --- Partition management verbs ---

    /**
     * Creates a partition with the given name for the given token, where the given key is the result
     * of calling {@link TokenAttributeNames#partition(String, String)} (or if the token is the default
     * {@code TOKEN_UNDER_TEST}, simply {@link TokenAttributeNames#partition(String)}).
     *
     * @param partitionToken the partition to create
     * @return a {@link HapiSpecOperation} that can be used to create the partition
     */
    public static HapiSpecOperation addPartition(@NonNull final String partitionToken) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Deletes the given partition for the given token, where the given key is the result
     * of calling {@link TokenAttributeNames#partition(String, String)} (or if the token is the default
     * {@code TOKEN_UNDER_TEST}, simply {@link TokenAttributeNames#partition(String)}).
     *
     * @param partitionToken the partition to delete
     * @return a {@link HapiSpecOperation} that can be used to delete the partition
     */
    public static HapiSpecOperation deletePartition(@NonNull final String partitionToken) {
        throw new AssertionError("Not implemented");
    }

    // --- Inter-partition management verbs ---
    /**
     * Convenience factory to move the given amount of units of the given token between two partitions
     * of the same token in the same account.
     *
     * @param account the account to move the units in
     * @param fromPartitionToken the partition to move the units from
     * @param toPartitionToken the partition to move the units to
     * @param amount the amount of units to move
     * @return a {@link HapiCryptoTransfer} that can be used to move the units
     *
     */
    public static HapiCryptoTransfer moveUnitsBetweenSameUserPartitions(
            @NonNull final String account,
            @NonNull final String fromPartitionToken,
            @NonNull final String toPartitionToken,
            final long amount) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Convenience factory to move the given serial numbers of the given token between two partitions
     * of the same token in the same account.
     *
     * @param account the account to move the serial numbers in
     * @param fromPartitionToken the partition to move the serial numbers from
     * @param toPartitionToken the partition to move the serial numbers to
     * @param serialNos the serial numbers to move
     * @return a {@link HapiCryptoTransfer} that can be used to move the serial numbers
     */
    public static HapiCryptoTransfer moveNftsBetweenSameUserPartitions(
            @NonNull final String account,
            @NonNull final String fromPartitionToken,
            @NonNull final String toPartitionToken,
            final long... serialNos) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Convenience factory to move the given amount of units of the given token between two partitions
     * of the same token in different accounts.
     *
     * @param fromAccount the account to move the units from
     * @param fromPartitionToken the partition to move the units from
     * @param toAccount the account to move the units to
     * @param toPartitionToken the partition to move the units to
     * @param amount the amount of units to move
     * @return a {@link HapiCryptoTransfer} that can be used to move the units
     */
    public static HapiCryptoTransfer moveUnitsBetweenDifferentUserPartitions(
            @NonNull final String fromAccount,
            @NonNull final String fromPartitionToken,
            @NonNull final String toAccount,
            @NonNull final String toPartitionToken,
            final long amount) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Convenience factory to move the given serial numbers of the given token between two partitions
     * of the same token in different accounts.
     *
     * @param fromAccount the account to move the serial numbers from
     * @param fromPartitionToken the partition to move the serial numbers from
     * @param toAccount the account to move the serial numbers to
     * @param toPartitionToken the partition to move the serial numbers to
     * @param serialNos the serial numbers to move
     * @return a {@link HapiCryptoTransfer} that can be used to move the serial numbers
     */
    public static HapiCryptoTransfer moveNftsBetweenDifferentUserPartitions(
            @NonNull final String fromAccount,
            @NonNull final String fromPartitionToken,
            @NonNull final String toAccount,
            @NonNull final String toPartitionToken,
            final long... serialNos) {
        throw new AssertionError("Not implemented");
    }

    // --- GetTokenInfo query specializations --

    /**
     * Asserts that the given partition of the default {@code TOKEN_UNDER_TEST} has the expected inherited properties.
     *
     * @param partition the partition to check
     * @return a {@link HapiGetTokenInfo} that can be used to check the partition
     */
    public static HapiGetTokenInfo assertPartitionInheritedExpectedProperties(@NonNull final String partition) {
        return assertPartitionInheritedExpectedProperties(partition, TOKEN_UNDER_TEST);
    }

    /**
     * Asserts that the given partition of the given token has the expected inherited properties.
     *
     * @param partition the partition to check
     * @param token the token to check
     * @return a {@link HapiGetTokenInfo} that can be used to check the partition
     */
    public static HapiGetTokenInfo assertPartitionInheritedExpectedProperties(
            @NonNull final String partition, @NonNull final String token) {
        throw new AssertionError("Not implemented");
    }

    // --- Internal helpers ---
    private static TokenDefOperation tokenWithFeatures(
            @NonNull final String token, @NonNull final TokenType type, @NonNull final TokenFeature... features) {
        final var def = new TokenDefOperation(token, type, features);
        if (type == TokenType.FUNGIBLE_COMMON) {
            def.initialSupply(FUNGIBLE_INITIAL_SUPPLY);
        }
        return def;
    }
}
