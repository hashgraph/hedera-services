package com.swirlds.platform.system.events;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

public class ParentDescriptors {
    /**
     * the self parent event descriptor
     */
    private final EventDescriptor selfParent;
    /**
     * the other parents' event descriptors
     */
    private final List<EventDescriptor> otherParents;

    /** a combined list of all parents, selfParent + otherParents */
    private final List<EventDescriptor> allParents;


    public ParentDescriptors(@Nullable final EventDescriptor selfParent, @NonNull final List<EventDescriptor> otherParents) {
        this.selfParent = selfParent;
        this.otherParents = otherParents;
    }
}
