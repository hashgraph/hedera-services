package com.swirlds.common.wiring.model.internal;

import static com.swirlds.common.wiring.model.internal.ModelVertexMetaType.GROUP;

import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A vertex that represents a nexted group of vertices.
 */
public class GroupVertex implements ModelVertex {

    /**
     * The name of the vertex.
     */
    private final String name;

    /**
     * The outgoing edges of this vertex.
     */
    private final List<ModelEdge> outgoingEdges = new ArrayList<>();

    /**
     * Vertices that are contained within this group.
     */
    private final List<ModelVertex> subVertices;

    public GroupVertex(
            @NonNull final String name,
            @NonNull final List<ModelVertex> subVertices) {

        this.name = Objects.requireNonNull(name);
        this.subVertices = Objects.requireNonNull(subVertices);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String getName() {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public TaskSchedulerType getType() {
        // TODO
        return TaskSchedulerType.DIRECT;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ModelVertexMetaType getMetaType() {
        return GROUP;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isInsertionIsBlocking() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void connectToEdge(@NonNull final ModelEdge edge) {

    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public List<ModelEdge> getOutgoingEdges() {
        return outgoingEdges;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addSubstitutedInput(@NonNull final String input) {
        throw new IllegalStateException("Un-collapsed groups never have inputs");
    }

    /**
     * Get the vertices that are contained within this group.
     *
     * @return the vertices that are contained within this group
     */
    public List<ModelVertex> getSubVertices() {
        return subVertices;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void render(@NonNull final StringBuilder sb) {
        // TODO indentation
        sb.append("subgraph ").append(getName()).append("\n");

        subVertices.stream().sorted().forEachOrdered(vertex -> vertex.render(sb));

        sb.append("end\n");

        // TODO subgraph style
    }
}
