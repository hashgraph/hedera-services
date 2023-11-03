package com.hedera.services.bdd.suites.hip796;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;

/**
 *A family of {@link HapiSpecOperation}'s specialized for HIP-796.
 *
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
 *
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

    public static void main(String... args)  {
        System.out.println("Hello world");
    }

    public static HapiSpecOperation tokenDefinition(String token) {
        throw new AssertionError("Not implemented");
    }

    public static HapiSpecOperation fungibleTokenDefinition(String token) {
        throw new AssertionError("Not implemented");
    }
}
