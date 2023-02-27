/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.linking;

import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.EventImpl;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * A {@link GossipEvent} whose parents may or may not be missing. A parent is considered missing if it is non-ancient
 * AND it has not been received and validated. A parent might have been received but is an orphan itself, in that case
 * it is still considered missing. If any parent is missing, then this child is considered an orphan.
 */
public final class ChildEvent {
    private final EventImpl child;
    private boolean missingSelfParent;
    private boolean missingOtherParent;

    /**
     * @param child
     * 		the event to wrap
     * @param missingSelfParent
     * 		is the event's self-parent missing?
     * @param missingOtherParent
     * 		is the event's other-parent missing?
     * @param selfParent
     * 		the event's self-parent, may be null if there is no parent, or it is not found
     * @param otherParent
     * 		the event's other-parent, may be null if there is no parent, or it is not found
     */
    public ChildEvent(
            final GossipEvent child,
            final boolean missingSelfParent,
            final boolean missingOtherParent,
            final EventImpl selfParent,
            final EventImpl otherParent) {
        this.child = new EventImpl(child, selfParent, otherParent);
        this.missingSelfParent = missingSelfParent;
        this.missingOtherParent = missingOtherParent;
    }

    /**
     * @return true if this event is an orphan
     */
    public boolean isOrphan() {
        return missingSelfParent || missingOtherParent;
    }

    /**
     * @return the number of parents that are considered missing
     */
    public int numParentsMissing() {
        return (missingSelfParent ? 1 : 0) + (missingOtherParent ? 1 : 0);
    }

    /**
     * Updates the state of this child with a parent. The parent mey not have been found, it might have become ancient,
     * in which case it is no longer missing and don't even need it.
     *
     * @param hash
     * 		the hash of the previously missing parent
     * @param parent
     * 		the parent, null if it has become ancient and was never received
     * @throws IllegalArgumentException
     * 		in case the supplied parent is not missing, or not a parent of this event at all
     */
    public void parentNoLongerMissing(final Hash hash, final EventImpl parent) {
        if (missingSelfParent && hash.equals(child.getHashedData().getSelfParentHash())) {
            this.child.setSelfParent(parent);
            this.missingSelfParent = false;
            return;
        }
        if (missingOtherParent && hash.equals(child.getHashedData().getOtherParentHash())) {
            this.child.setOtherParent(parent);
            this.missingOtherParent = false;
            return;
        }
        throw new IllegalArgumentException(
                String.format("%s is not a missing parent of %s", hash.toShortString(), this));
    }

    /**
     * @return a new instance that describes this child's self-parent
     */
    public ParentDescriptor buildSelfParentDescriptor() {
        return new ParentDescriptor(
                child.getHashedData().getSelfParentGen(), child.getHashedData().getSelfParentHash());
    }

    /**
     * @return a new instance that describes this child's other-parent
     */
    public ParentDescriptor buildOtherParentDescriptor() {
        return new ParentDescriptor(
                child.getHashedData().getOtherParentGen(), child.getHashedData().getOtherParentHash());
    }

    /**
     * @return the child
     */
    public EventImpl getChild() {
        return child;
    }

    /**
     * @return true if this child has a missing self-parent
     */
    public boolean isMissingSelfParent() {
        return missingSelfParent;
    }

    /**
     * @return true if this child has a missing other-parent
     */
    public boolean isMissingOtherParent() {
        return missingOtherParent;
    }

    /**
     * @return the hash of the child
     */
    public Hash getHash() {
        return child.getHashedData().getHash();
    }

    @Override
    public int hashCode() {
        return getHash().hashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final ChildEvent that = (ChildEvent) o;

        return getHash().equals(that.getHash());
    }

    /**
     * @return a string describing which parents are missing, if any
     */
    public String missingParentsString() {
        if (numParentsMissing() == 2) {
            return "both parents";
        }
        if (missingSelfParent) {
            return "self-parent";
        }
        if (missingOtherParent) {
            return "other-parent";
        }
        return "none parents";
    }

    /**
     * This child will be an orphan forever, so clear its parents for garbage collection
     */
    public void orphanForever() {
        child.clear();
        missingSelfParent = true;
        missingOtherParent = true;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("child", child)
                .append("missingSelfParent", missingSelfParent)
                .append("missingOtherParent", missingOtherParent)
                .toString();
    }
}
