/**
 * The tests in this package "leak" in the sense that they can either,
 *
 * <ol>
 *   <li>Affect other tests running concurrently; or,
 *   <li><i>Be affected by</i> other tests running concurrently.
 * </ol>
 *
 * <p>An example of the first kind is a test like
 * {@link com.hedera.services.bdd.suites.file.FileUpdateSuite#chainIdChangesDynamically()},
 * {@link com.hedera.services.bdd.suites.leaky.LeakyEthereumTestsSuite#legacyUnprotectedEtxBeforeEIP155()}
 * and {@link com.hedera.services.bdd.suites.leaky.LeakyEthereumTestsSuite#legacyEtxAfterEIP155()}
 * that changes the network's EVM {@code chainid} to a non-standard value. Any concurrent {@code EthereumCall} that
 * uses the standard dev {@code 298} chainid will now fail. The updated {@code chainid} "leaked"
 * out! Any test that updates system files for properties, permissions, fees, or throttles can have
 * the same kind of non-deterministic effect.
 *
 * <p>An example of the second kind is a test like {@link
 * com.hedera.services.bdd.suites.leaky.LeakyContractTestsSuite#propagatesNestedCreations()} that
 * depends on no other entities being created while it is running. If a concurrent test creates an
 * entity between its initial contraction creation and its subsequent {@code propagate()} call, its
 * assertions on the created child contract id's will fail. Another example is {@link
 * com.hedera.services.bdd.suites.leaky.LeakyContractTestsSuite#payerCannotOverSendValue()}, which
 * requires a transaction to have reached consensus in a second. This might not happen if the
 * network is under heavy load.
 */
package com.hedera.services.bdd.suites.leaky;
