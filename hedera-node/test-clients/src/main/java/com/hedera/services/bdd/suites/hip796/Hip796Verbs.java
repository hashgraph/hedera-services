package com.hedera.services.bdd.suites.hip796;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hedera.services.bdd.spec.queries.token.HapiGetTokenInfo;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.suites.hip796.operations.TokenDefOperation;
import com.hedera.services.bdd.suites.hip796.operations.TokenFeature;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.hedera.services.bdd.suites.HapiSuite.FUNGIBLE_INITIAL_SUPPLY;
import static com.hedera.services.bdd.suites.HapiSuite.TOKEN_UNDER_TEST;

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
 *     for partition "Red" of token "Charlie" will always be "Charlie|Red", allowing us to trivial </li>
 * </ol>
 *
 * <p>For example, given the story:
 *
 * <b>General-1</b>: As a token-issuer, I want to create a fungible token definition with locking and/or partitioning capabilities.
 * <p>
 * We want a {@link com.hedera.services.bdd.spec.HapiSpec} like this,
 * <pre>
 *     {@code
 *         .given(
 *              // Create a token with no locking or partitioning with two pre-associated users
 *              fungibleTokenDefinition("Vanilla"),
 *              // Create a lockable token with the same two pre-associated users
 *              fungibleTokenDefinition("Lockable")
 *                  .lockable(),
 *              // Create a partitioned token with the same two pre-associated users
 *              nonFungibleTokenDefinition("Partitioned")
 *                  .partitionedInto(MUTABLE, "Red", "Blue")
 *         ).when().then(
 *              // Confirm that only the lockable token can be locked
 *              lockToken("Vanilla").hasKnownStatus(TOKEN_HAS_NO_LOCK_KEY),
 *              lockToken("Partitioned").hasKnownStatus(TOKEN_HAS_NO_LOCK_KEY),
 *              lockToken("Lockable"),
 *              unlockToken("Lockable"),
 *              // Confirm that only the partitioned token can have partitions added or removed
 *              addPartition("Vanilla", "Odd").hasKnownStatus(TOKEN_HAS_NO_PARTITION_KEY),
 *              addPartition("Lockable", "Odd").hasKnownStatus(TOKEN_HAS_NO_PARTITION_KEY),
 *              addPartition("Partitioned", "Odd"),
 *              removePartition("Partitioned", "Odd"),
 *         )
 *     }
 * </pre>
 */
public class Hip796Verbs {
    private Hip796Verbs() {
        throw new UnsupportedOperationException();
    }

    public static final Function SAME_USER_PARTITION_MOVE_UNITS_FUNCTION = new Function(
            "moveBetweenSameUserPartitions(address,address,address,int64)");
    public static final Function DIFFERENT_USER_PARTITION_MOVE_UNITS_FUNCTION = new Function(
            "moveBetweenDifferentUserPartitions(address,address,address,address,int64)");
    public static final Function CREATE_PARTITION_FUNCTION = new Function(
            "createPartition(address,string,string)");
    public static final Function DELETE_PARTITION_FUNCTION = new Function(
            "deletePartition(address)");
    public static final Function UPDATE_PARTITION_FUNCTION = new Function(
            "deletePartition(address,bool,string,bool,string)");
    // The lock, partition, and partition move keys are likely to have bit-masks
    // 1 << 7, 1 << 8, and 1 << 9 respectively
    public static final BigInteger LOCK_KEY_TYPE = BigInteger.valueOf(1 << 7);
    public static final BigInteger PARTITION_KEY_TYPE = BigInteger.valueOf(1 << 8);
    public static final BigInteger PARTITION_MOVE_KEY_TYPE = BigInteger.valueOf(1 << 9);
    public static final Function ROTATE_KEY_FUNCTION = new Function(
            "rotateKey((uint256,(bool,address,bytes,bytes,address))");
    public static final Function REMOVE_KEY_FUNCTION = new Function(
            "removeKey(uint256)");


    // --- Token definition factories ---
    public static TokenDefOperation nonFungibleTokenWithFeatures(@NonNull final TokenFeature... features) {
        return nonFungibleTokenWithFeatures(TOKEN_UNDER_TEST, features);
    }

    public static TokenDefOperation nonFungibleTokenWithFeatures(@NonNull final String token, @NonNull final TokenFeature... features) {
        // It never makes sense to create a non-fungible token without a supply key
        final var featuresWithSupplyKey = Stream.concat(Stream.of(TokenFeature.SUPPLY_MANAGEMENT), Arrays.stream(features))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(TokenFeature.class)))
                .toArray(new TokenFeature[0]);
        return tokenWithFeatures(token, TokenType.NON_FUNGIBLE_UNIQUE, featuresWithSupplyKey);
    }

    public static TokenDefOperation fungibleTokenWithFeatures(@NonNull final TokenFeature... features) {
        return fungibleTokenWithFeatures(TOKEN_UNDER_TEST, features);
    }

    public static TokenDefOperation fungibleTokenWithFeatures(@NonNull final String token, @NonNull final TokenFeature... features) {
        return tokenWithFeatures(token, TokenType.FUNGIBLE_COMMON, features);
    }

    public static TokenDefOperation tokenWithFeatures(
            @NonNull final String token,
            @NonNull final TokenType type,
            @NonNull final TokenFeature... features) {
        final var def = new TokenDefOperation(token, type, features);
        if (type == TokenType.FUNGIBLE_COMMON) {
            def.initialSupply(FUNGIBLE_INITIAL_SUPPLY);
        } else {

        }
        return def;
    }

    // --- Lock management verbs ---
    public static HapiTxnOp lockUnits(
            @NonNull final String account,
            @NonNull final String token,
            final long amount) {
        throw new AssertionError("Not implemented");
    }

    public static HapiTxnOp unlockUnits(
            @NonNull final String account,
            @NonNull final String token,
            final long amount) {
        throw new AssertionError("Not implemented");
    }

    public static HapiSpecOperation lockNfts(
            @NonNull final String account,
            @NonNull final String token,
            final long... serialNos) {
        throw new AssertionError("Not implemented");
    }

    public static HapiSpecOperation unlockNfts(
            @NonNull final String account,
            @NonNull final String token,
            final long... serialNos) {
        throw new AssertionError("Not implemented");
    }

    // --- Partition management verbs ---
    public static HapiSpecOperation deletePartition(@NonNull final String partitionToken) {
        throw new AssertionError("Not implemented");
    }

    public static HapiSpecOperation addPartition(@NonNull final String partitionToken) {
        throw new AssertionError("Not implemented");
    }

    // --- Inter-partition management verbs ---
    public static HapiCryptoTransfer moveUnitsBetweenSameUserPartitions(
            @NonNull final String account,
            @NonNull final String fromPartitionToken,
            @NonNull final String toPartitionToken,
            final long amount) {
        throw new AssertionError("Not implemented");
    }

    public static HapiCryptoTransfer moveNftsBetweenSameUserPartitions(
            @NonNull final String account,
            @NonNull final String fromPartitionToken,
            @NonNull final String toPartitionToken,
            final long... serialNos) {
        throw new AssertionError("Not implemented");
    }

    public static HapiCryptoTransfer moveUnitsBetweenDifferentUserPartitions(
            @NonNull final String fromAccount,
            @NonNull final String fromPartitionToken,
            @NonNull final String toAccount,
            @NonNull final String toPartitionToken,
            final long amount) {
        throw new AssertionError("Not implemented");
    }

    public static HapiCryptoTransfer moveNftsBetweenDifferentUserPartitions(
            @NonNull final String fromAccount,
            @NonNull final String fromPartitionToken,
            @NonNull final String toAccount,
            @NonNull final String toPartitionToken,
            final long... serialNos) {
        throw new AssertionError("Not implemented");
    }

    // --- GetTokenInfo query specializations --
    public static HapiGetTokenInfo assertPartitionInheritedExpectedProperties(@NonNull final String partition) {
        return assertPartitionInheritedExpectedProperties(partition, TOKEN_UNDER_TEST);
    }

    public static HapiGetTokenInfo assertPartitionInheritedExpectedProperties(@NonNull final String partition, @NonNull final String token) {
        throw new AssertionError("Not implemented");
    }
}
