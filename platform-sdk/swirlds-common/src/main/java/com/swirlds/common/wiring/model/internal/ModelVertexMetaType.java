package com.swirlds.common.wiring.model.internal;

/**
 * The type of a vertex in a wiring flowchart. Although the original graph will be constructed of SCHEDULER vertices
 * alone, when generating the flowchart, verticies will be added, removed and combined. New verticies that do not
 * directly correspond to a scheduler will be of type SUBSTITUTION or GROUP.
 */
public enum ModelVertexMetaType {
    /**
     * A vertex that corresponds to a scheduler.
     */
    SCHEDULER,
    /**
     * A vertex that is used as a stand-in for a substituted edge.
     */
    SUBSTITUTION,
    /**
     * A vertex that is used as a stand-in for a group of vertices.
     */
    GROUP
}
