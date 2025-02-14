// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.event.orphan;

import com.swirlds.platform.system.events.EventDescriptorWrapper;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * A missing parent event and the orphans that are missing it.
 *
 * @param parent  the parent event
 * @param orphans the orphans that are missing the parent
 */
record ParentAndOrphans(@NonNull EventDescriptorWrapper parent, @NonNull List<OrphanedEvent> orphans) {}
