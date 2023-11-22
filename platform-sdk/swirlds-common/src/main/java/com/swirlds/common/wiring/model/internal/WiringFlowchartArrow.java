package com.swirlds.common.wiring.model.internal;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Describes an arrow in the wiring flowchart. Similar to an edge the wiring model, but not exactly the same. When
 * vertices are grouped together and collapsed, sometimes the arrows start and end at boxes that have a different name
 * than the original vertex. This object is used to detect duplicate edges in the wiring flowchart after grouping and
 * collapsing vertices.
 *
 * @param start    the box where the arrow starts
 * @param end      the box where the arrow ends
 * @param label    the label on the arrow
 * @param blocking whether the arrow represents a blocking edge
 */
public record WiringFlowchartArrow(@NonNull String start, @NonNull String end, @NonNull String label,
                                   boolean blocking) {
}
