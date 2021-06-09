/**
 * Provides the {@link com.hedera.services.state.merkle.virtual.VirtualMap}, a merkle node with
 * an inner, virtualized merkle tree, encapsulated by a map-like interface.
 *
 * <p>This implementation is specifically designed to fit the needs of Smart Contracts for
 * Hedera Hashgraph. Each Smart Contract may have storage, handled as key/value pairs. Each
 * key and each value are an unsigned 256-bit word. The {@link com.hedera.services.state.merkle.virtual.VirtualKey}
 * represents these keys and the {@link com.hedera.services.state.merkle.virtual.VirtualValue}
 * represents the values.</p>
 *
 * <p>Smart Contracts present some particular design challenges:</p>
 * <ul>
 *     <li>Many contracts require non-trivial storage. ERC-20 contracts may require megabytes of storage</li>
 *     <li>Due to gas limits, only about 25 SSTORE (put) operations are supported in a single contract call</li>
 *     <li>Contracts may read state from anywhere within their megabytes of storage</li>
 *     <li>Contracts need very fast TPS or excessive contract usage will cripple the network</li>
 *     <li>A hash of the entire blob must be stored with the signed-state in the "real" merkle tree</li>
 * </ul>
 *
 * <p>While many excellent solutions exist for reading and writing binary data, without a Merkle-tree like
 * structure it is not possible to efficiently hash the data. And while a Merkle-tree like data structure
 * is excellent for hashing, we cannot possibly represent the entire contract state as a Merkle tree
 * due to the costs in storing it in memory long term, or in reading it all from disk for a single
 * contract execution. The solution therefore is quite natural, we need a virtual Merkle tree where nodes
 * are stored on disk and "realized" into memory on demand.</p>
 *
 * <p>In other words, we need a solution that scales with the number of get/put operations and not with
 * the size of the data storage. A memory-mapped virtual merkle tree is a good fit.</p>
 *
 * <p>The challenge with this solution is to reduce the cost of reading and writing individual nodes
 * so that we can handle many thousands of contract executions per second. The first key is to use an
 * efficient memory-mapped file solution. Java has supported memory-mapped files since at least Java 1.4.
 * While not perfect, it is well known and very fast. The second key is to "realize" only those nodes
 * that are necessary. In practice, this means we only need ot realize the leaves, until the moment we
 * hash, at which point we need to read any saved hashes for parent nodes as well. This design leads to
 * incredibly efficient use of memory and a very high TPS.</p>
 *
 * <p>This API is centered on the {@link com.hedera.services.state.merkle.virtual.VirtualMap}. It is
 * map-like but does not implement the java.util.Map API because the java Map API defines more
 * capability than is necessary for the VirtualMap. Technically the VirtualMap implementation is
 * powerful enough to implement the entire java.util.Map API, but the implementation of some of the
 * API would not perform well and therefore should not be made available.</p>
 */
package com.hedera.services.state.merkle.virtual;