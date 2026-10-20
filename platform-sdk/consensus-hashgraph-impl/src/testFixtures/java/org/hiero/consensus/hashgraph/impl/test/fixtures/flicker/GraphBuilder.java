// SPDX-License-Identifier: Apache-2.0
package org.hiero.consensus.hashgraph.impl.test.fixtures.flicker;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.hiero.consensus.crypto.DefaultEventHasher;
import org.hiero.consensus.crypto.EventHasher;
import org.hiero.consensus.model.event.PlatformEvent;
import org.hiero.consensus.model.node.NodeId;
import org.hiero.consensus.model.roster.RosterWrapper;
import org.hiero.consensus.model.test.fixtures.event.TestingEventBuilder;

/**
 * Turns a list of {@link EventSpec} into built events, in order.
 * <p>
 * Specs must be listed bottom-up: an event may only name parents defined above it in the list. That is also the order
 * the events must be fed to {@link FlickerIntake}, so the returned list is the feed order.
 * <p>
 * <b>Four fields are pinned on every event</b> rather than left to {@link TestingEventBuilder}'s defaults. All four
 * are hashed content, and hashes feed the final tie-break in consensus order, so a random value means a fixture that
 * silently orders differently between runs:
 * <ul>
 *     <li><b>birth round</b> — the default is {@code max(parent birth rounds) + random(0..2)}, which would drift the
 *         ancient window. Pinned to 1, so nothing in a small graph goes ancient.</li>
 *     <li><b>time created</b> — the default advances a random 1-99ms from the <i>self-parent</i>, drawn from the
 *         supplied {@link Random}. That satisfies {@code ConsensusLinker}'s self-parent ordering check either way, so
 *         the pinning is not about validity; it is about reproducibility and derivability. Event timestamps feed round
 *         timestamps and the consensus order a fixture asserts, and a value drawn from the shared {@code Random} shifts
 *         for every later event when a row is inserted. Derived instead from the self-parent by a fixed
 *         {@value #TIME_STEP_MILLIS}ms step, with a genesis event offset by its creator index. A timestamp therefore
 *         reads as <i>height in its own column, plus who made it</i> — {@code a0} is the base, {@code a1} is +100ms,
 *         {@code b0} is +1ms — and the two components cannot collide while a graph has fewer creators than the step.
 *         Deriving from the self-parent rather than from list position is what bounds the effect of an edit. A
 *         timestamp then depends on the creator's own chain and nothing else, so inserting an event perturbs exactly
 *         the events that <i>descend</i> from it — which a structural edit was always going to change — rather than
 *         everything below the insertion point in the file. {@code nGen} is structural in the same way
 *         ({@code max(parents) + 1}) and the other hashed fields are constants, so the descendant set is the whole
 *         blast radius.</li>
 *     <li><b>transactions</b> — the default is two random 4-byte app transactions. Pinned to none.</li>
 *     <li><b>coin</b> — the default is drawn from the supplied {@link Random}. That makes every hash in the graph
 *         depend on the exact <i>sequence</i> of builder calls rather than on the seed alone: insert one row anywhere
 *         and every later event draws a different coin, and therefore hashes differently. Pinned to 0, which is what
 *         lets a graph be edited without disturbing the events around the edit.</li>
 * </ul>
 * With all four pinned, nothing the builder still draws from the {@code Random} — the signature and the placeholder
 * hash — reaches the content hash, so the seed no longer determines any hash in the graph.
 * <p>
 * <b>Events are hashed as they are built</b>, before being used as anyone's parent. {@code TestingEventBuilder}
 * installs a random placeholder hash and takes a parent's descriptor from whatever hash the parent carries at the
 * time, while both intake paths overwrite that placeholder with the real content hash. Without hashing here, every
 * child would claim a parent hash that no longer resolves and the orphan buffer would hold the whole graph back.
 */
public final class GraphBuilder {

    private static final EventHasher HASHER = new DefaultEventHasher();
    private static final Instant BASE_TIME = Instant.ofEpochSecond(1_700_000_000L);
    private static final long TIME_STEP_MILLIS = 100;

    private GraphBuilder() {}

    /**
     * Build the events a graph specifies.
     *
     * @param specs  the events, bottom-up; each may only name parents defined above it
     * @param random the source of randomness; supply a fixed seed for a reproducible graph
     * @param roster the roster whose node ids the {@code creator} indices refer to
     * @return the built events in the same order, each paired with its name
     */
    @NonNull
    public static List<NamedEvent> build(
            @NonNull final List<EventSpec> specs, @NonNull final Random random, @NonNull final RosterWrapper roster) {

        final List<NodeId> nodeIds = roster.nodeIds();
        final Map<String, PlatformEvent> builtByName = new LinkedHashMap<>();
        final List<NamedEvent> ordered = new ArrayList<>(specs.size());

        for (final EventSpec spec : specs) {
            if (spec.creator() < 0 || spec.creator() >= nodeIds.size()) {
                throw new IllegalArgumentException("'%s' names creator index %d, but the roster has %d nodes"
                        .formatted(spec.name(), spec.creator(), nodeIds.size()));
            }
            if (builtByName.containsKey(spec.name())) {
                throw new IllegalArgumentException("'%s' is defined twice".formatted(spec.name()));
            }

            final PlatformEvent selfParent =
                    spec.selfParent() == null ? null : resolve(builtByName, spec.selfParent(), spec.name());

            final TestingEventBuilder builder = new TestingEventBuilder(random)
                    .setCreatorId(nodeIds.get(spec.creator()))
                    .setBirthRound(1)
                    .setTransactionBytes(List.of())
                    .setCoin(0)
                    .setTimeCreated(timeCreated(selfParent, spec.creator()));

            if (selfParent != null) {
                builder.setSelfParent(selfParent);
            }
            if (spec.otherParents().length > 0) {
                builder.setOtherParents(Arrays.stream(spec.otherParents())
                        .map(parent -> resolve(builtByName, parent, spec.name()))
                        .toList());
            }

            final PlatformEvent event = HASHER.hashEvent(builder.build());
            builtByName.put(spec.name(), event);
            ordered.add(new NamedEvent(spec.name(), event));
        }
        return List.copyOf(ordered);
    }

    /**
     * The time an event is created: one fixed step after its self-parent, or the base time offset by the creator's
     * index if it has none.
     *
     * @param selfParent the event's self-parent, or null if it is a genesis event
     * @param creator    the creator's index into the roster
     * @return the time created
     */
    @NonNull
    private static Instant timeCreated(@Nullable final PlatformEvent selfParent, final int creator) {
        return selfParent == null
                ? BASE_TIME.plusMillis(creator)
                : selfParent.getTimeCreated().plusMillis(TIME_STEP_MILLIS);
    }

    @NonNull
    private static PlatformEvent resolve(
            @NonNull final Map<String, PlatformEvent> builtByName,
            @NonNull final String parent,
            @NonNull final String child) {

        final PlatformEvent event = builtByName.get(parent);
        if (event == null) {
            throw new IllegalArgumentException(
                    "'%s' names parent '%s', which is not defined above it".formatted(child, parent));
        }
        return event;
    }
}
