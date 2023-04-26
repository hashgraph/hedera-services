/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.merkle.synchronization.internal;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.synchronization.settings.ReconnectSettingsFactory;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * A wrapper object for a node that the sending synchronizer intends to send to the receiver.
 * These objects form a shadow tree that contains nodes that 1) have not yet received learner responses
 * and 2) have been or are about to be sent by the teacher.
 */
public class NodeToSend {

    /**
     * A node that may be sent. Will not be sent if the learner tells the teacher that it has the node already.
     */
    private final MerkleNode node;

    /**
     * True if a response (positive or negative) has been received from the learner.
     */
    private volatile boolean responseReceived;

    /**
     * If true then the learner already has this node, if false then it must be sent.
     */
    private volatile boolean responseStatus;

    /**
     * If it comes time to send this node, but a response has not yet been heard from the learner, make sure that this
     * amount of time has passed (since the query was sent) before sending the node.
     */
    private final long unconditionalSendTimeMilliseconds;

    /**
     * Wrappers around the children of this node. This list is only populated once the children have had a lesson
     * sent for them.
     */
    private final List<NodeToSend> children;

    /**
     * Create an object that represents a node that may be sent in the future.
     *
     * @param node
     * 		the node that may be sent.
     */
    public NodeToSend(final MerkleNode node) {
        this.node = node;
        this.responseReceived = false;
        this.responseStatus = false;
        if (node != null && !node.isLeaf()) {
            children = new LinkedList<>();
        } else {
            children = null;
        }

        unconditionalSendTimeMilliseconds =
                System.currentTimeMillis() + ReconnectSettingsFactory.get().getMaxAckDelayMilliseconds();
    }

    /**
     * Whenever the teacher sends a query to the learner about a node, it registers that node to its parent
     * in this shadow tree. This allows a positive response corresponding to an ancestor to propagate information
     * that can prevent the node from needing to be sent.
     *
     * @param child
     * 		the node that is now eligible for sending. It is called a child because it is the child of a node that
     * 		was just sent.
     */
    public synchronized void registerChild(final NodeToSend child) {
        if (children == null) {
            throw new IllegalStateException("can not add children to leaf node");
        }
        children.add(child);

        child.responseStatus = responseStatus;
    }

    private synchronized void addChildrenToQueue(final Queue<NodeToSend> queue) {
        queue.addAll(children);
    }

    /**
     * Get the merkle node that this object is wrapping.
     */
    public MerkleNode getNode() {
        return node;
    }

    /**
     * This method is called when the response for this node's query is received.
     *
     * @param learnerHasNode true if the learner has the node, otherwise false
     */
    public void registerResponse(final boolean learnerHasNode) {
        if (learnerHasNode) {
            cancelTransmission();
        }
        responseReceived = true;
    }

    /**
     * Return true if the learner has confirmed that it has the node in question
     * (i.e. the node returned by {@link #getNode()}).
     * @return
     */
    public boolean getResponseStatus() {
        return responseStatus;
    }

    /**
     * Wait for a resonse from the learner. Will return immediately if a response has already been received or
     * if an ancestor has received a positive response. May sleep a short period if neither are true.
     * There is no guarantee that a response will have been received when this method returns.
     */
    public void waitForResponse() {
        if (responseReceived || responseStatus) {
            return;
        }

        final long currentTime = System.currentTimeMillis();
        if (currentTime > unconditionalSendTimeMilliseconds) {
            return;
        }

        final long sleepTime = unconditionalSendTimeMilliseconds - currentTime;
        try {
            MILLISECONDS.sleep(sleepTime);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Cancel the transmission of this node and all of its descendants.
     */
    private void cancelTransmission() {
        final Queue<NodeToSend> queue = new LinkedList<>();
        queue.add(this);

        while (!queue.isEmpty()) {
            final NodeToSend next = queue.remove();
            if (next.responseStatus) {
                continue;
            }
            next.responseStatus = true;
            if (node != null && next.node != null && !next.node.isLeaf()) {
                next.addChildrenToQueue(queue);
            }
        }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, SHORT_PREFIX_STYLE)
                .append("response", responseReceived ? responseStatus : "?")
                .append("node", node)
                .toString();
    }
}
