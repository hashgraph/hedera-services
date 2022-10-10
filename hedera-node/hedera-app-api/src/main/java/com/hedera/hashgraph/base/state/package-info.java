/**
 * APIs for managing persistent state. In Hedera, data that is stored "in state" is replicated
 * perfectly across all nodes in the cluster. The state is used to compute a total state hash
 * that is gossiped between all nodes. If two nodes have different state hashes, they are said to
 * have "Inconsistent State Signatures", or an ISS. The state is also used for "reconnect", where
 * a node may have different state from other nodes (maybe it was down for a while, or is new
 * to join the network) and the system can synchronize state between nodes.
 * <p>
 * The state of the system is, ultimately, stored in a Merkle tree using an API defined by the
 * Hashgraph Platform. However, this implementation reality is not a detail that any of the
 * service implementations need to be aware of. Instead, all state stored in the system can
 * ultimately be broken down into simple key/value data structures. Indeed, the Hashgraph Platform
 * makes this easy with an API for in-memory k/v storage (MerkleMap) and on-disk k/v storage
 * (VirtualMap). The APIs in this package abstract those details from the service module
 * implementations, so they simply choose whether to use an in-memory state or on-disk state and
 * let the `hedera-app` module provide implementations that work with the Hashgraph Platform.
 * <p>
 * The state objects provided by the `hedera-app` module will buffer changes until the successful
 * conclusion of a transaction, and then "commit" those changes to the underlying merkle tree. The
 * `hedera-app` module is also responsible for handling "expiration" properly. By structuring the
 * code in the way we have, we make it possible for the `hedera-app` module to handle all
 * cross-cutting operations (like buffering, committing, expiration) while keeping the interfaces
 * simple and clean for service modules.
 * <p>
 * Each service module must deal with state migration. When a node is upgraded from version N to
 * version N+1, it may be that the state requires migration (not unlike a SQL-based application
 * needing to apply a database schema migration on upgrade). An instance of
 * {@link com.hedera.hashgraph.base.state.StateRegistry} will be passed to each Service implementation
 * in its constructor, allowing the Service implementation to handle the registration of, or migration
 * of, state owned by the service.
 * <p>
 * Ultimately, the Hedera application is responsible for managing the state in the merkle tree.
 * At the end of each round, a fast-copy is made of the merkle tree. When this happens, all future
 * queries are made against the new immutable copy while all transaction handling uses the new
 * mutable working state version of the tree.
 */
package com.hedera.hashgraph.base.state;