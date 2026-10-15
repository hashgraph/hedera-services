// SPDX-License-Identifier: Apache-2.0
package org.hiero.otter.fixtures.network.simulation;

import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import org.hiero.consensus.model.node.NodeId;

/**
 * What the simulated network has transmitted but not yet delivered, held in a separate queue for every directed
 * connection. This is the storage that {@link InFlightEvents} and {@link InFlightEventWindows} share; how the queues are
 * drained into a receiver is up to each of them.
 *
 * <p>Each connection's queue is kept in arrival order, which {@link #add(NodeId, InTransit)} enforces rather than
 * arranges. Subclasses rely on that when they deliver.
 *
 * <p>This class is not safe to use from more than one thread.
 *
 * @param <T> what is held in flight
 */
abstract class InFlightConnections<T extends InTransit> {

    /**
     * Whether a connection is currently up, given the sender and then the receiver. Supplied by the owner of the
     * network topology, so that this class is concerned only with holding what is in flight and ordering it.
     */
    private final BiPredicate<NodeId, NodeId> connected;

    /**
     * What is in flight, keyed by the node that will receive it and then by the node that sent it. A node does not send
     * to itself, so a node's map holds no queue for itself.
     */
    private final Map<NodeId, Map<NodeId, ArrayDeque<T>>> connectionsByReceiver = new HashMap<>();

    /**
     * Every node, in node id order. Delivery walks the senders in this order so that what arrives at the same instant
     * is always handled in the same order and the simulation stays reproducible.
     */
    private final List<NodeId> sortedNodeIds = new ArrayList<>();

    /**
     * A read-only view of {@link #sortedNodeIds}, handed to subclasses without being copied on every call.
     */
    private final List<NodeId> sortedNodeIdsView = Collections.unmodifiableList(sortedNodeIds);

    /**
     * What an item is called in the message when it is added out of order, e.g. "event window".
     */
    private final String itemName;

    /**
     * Constructor.
     *
     * @param connected         reports whether the connection from a sender to a receiver is currently up
     * @param itemName          what an item is called in the message when it is added out of order
     */
    InFlightConnections(@NonNull final BiPredicate<NodeId, NodeId> connected, @NonNull final String itemName) {
        this.connected = requireNonNull(connected);
        this.itemName = requireNonNull(itemName);
    }

    /**
     * Adds a node to the network, giving it a queue for every node already present and each of them a queue for it.
     *
     * @param newNodeId the id of the node to add
     */
    public void addNode(@NonNull final NodeId newNodeId) {
        final Map<NodeId, ArrayDeque<T>> incomingConnections = new HashMap<>();
        for (final NodeId existingNodeId : sortedNodeIds) {
            incomingConnections.put(existingNodeId, new ArrayDeque<>());
            connectionsByReceiver.get(existingNodeId).put(newNodeId, new ArrayDeque<>());
        }
        connectionsByReceiver.put(newNodeId, incomingConnections);

        sortedNodeIds.add(newNodeId);
        Collections.sort(sortedNodeIds);
    }

    /**
     * Adds something that should be delivered to a receiver in the future.
     *
     * <p>It must not have an arrival time before the item added most recently for the same connection. Delivery relies
     * on each connection's queue being in arrival order, and appending is the only thing that puts it in that order.
     *
     * @param receiver the node the item is traveling to
     * @param item     the item, carrying the node that sent it and the time it arrives
     * @throws IllegalArgumentException if the item arrives before the one added most recently for the same connection
     */
    public void add(@NonNull final NodeId receiver, @NonNull final T item) {
        final ArrayDeque<T> connection = connectionsByReceiver.get(receiver).get(item.sender());

        final T previous = connection.peekLast();
        if (previous != null && item.arrivalTime().isBefore(previous.arrivalTime())) {
            throw new IllegalArgumentException(
                    ("The %s sent from %s to %s arrives at %s, which is before the %s ahead of it on that "
                                    + "connection arrives at %s. They would be delivered out of order.")
                            .formatted(
                                    itemName,
                                    item.sender(),
                                    receiver,
                                    item.arrivalTime(),
                                    itemName,
                                    previous.arrivalTime()));
        }

        connection.addLast(item);
    }

    /**
     * Discards everything in flight towards a node. Used when the node restarts and is about to be sent everything it
     * needs again.
     *
     * @param receiverId the node whose incoming items should be discarded
     */
    public void clearIncoming(@NonNull final NodeId receiverId) {
        connectionsByReceiver.get(receiverId).values().forEach(ArrayDeque::clear);
    }

    /**
     * Delivers what has arrived at the receiving node and can be delivered right now.
     *
     * @param now      the current time
     * @param receiver accepts what is delivered on behalf of the receiving node
     */
    abstract void deliverArrived(@NonNull Instant now, @NonNull EventReceiver receiver);

    /**
     * Gets what is in flight towards a node, keyed by the node that sent it. There is no entry for the receiver itself.
     *
     * @param receiverId the node the items are traveling to
     * @return the queue of each incoming connection, which the caller may drain
     */
    @NonNull
    final Map<NodeId, ArrayDeque<T>> incomingConnections(@NonNull final NodeId receiverId) {
        return connectionsByReceiver.get(receiverId);
    }

    /**
     * Gets every node in node id order, which is the order delivery walks the senders in.
     *
     * @return a read-only view of the node ids
     */
    @NonNull
    final List<NodeId> sortedNodeIds() {
        return sortedNodeIdsView;
    }

    /**
     * Reports whether a connection is currently up. This is deliberately not folded into
     * {@link #incomingConnections(NodeId)}, because asking is not free and a subclass may know it can put the question
     * off until it matters.
     *
     * @param sender   the node at the sending end of the connection
     * @param receiver the node at the receiving end of the connection
     * @return {@code true} if things can travel from the sender to the receiver
     */
    final boolean isUp(@NonNull final NodeId sender, @NonNull final NodeId receiver) {
        return connected.test(sender, receiver);
    }
}
