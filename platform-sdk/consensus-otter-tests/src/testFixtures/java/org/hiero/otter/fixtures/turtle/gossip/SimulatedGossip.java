// SPDX-License-Identifier: Apache-2.0
package org.hiero.otter.fixtures.turtle.gossip;

import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.hiero.consensus.event.IntakeEventCounter;
import org.hiero.consensus.gossip.impl.gossip.Gossip;
import org.hiero.consensus.gossip.impl.gossip.sync.protocol.SyncStatusChecker;
import org.hiero.consensus.model.event.PlatformEvent;
import org.hiero.consensus.model.gossip.SyncProgress;
import org.hiero.consensus.model.hashgraph.EventWindow;
import org.hiero.consensus.model.node.NodeId;
import org.hiero.consensus.model.status.PlatformStatus;
import org.hiero.consensus.wiring.framework.model.DeterministicWiringModel;
import org.hiero.consensus.wiring.framework.model.WiringModel;
import org.hiero.consensus.wiring.framework.wires.input.BindableInputWire;
import org.hiero.consensus.wiring.framework.wires.input.NoInput;
import org.hiero.consensus.wiring.framework.wires.output.StandardOutputWire;
import org.hiero.otter.fixtures.network.simulation.EventReceiver;
import org.hiero.otter.fixtures.network.simulation.SimulatedNetworkConnectivity;

/**
 * Simulates the {@link Gossip} subsystem for a group of nodes running on a {@link SimulatedNetworkConnectivity}.
 */
public class SimulatedGossip implements Gossip, EventReceiver {

    private final SimulatedNetworkConnectivity networkConnectivity;
    private final NodeId selfId;
    private IntakeEventCounter intakeEventCounter;

    private StandardOutputWire<PlatformEvent> eventOutput;
    private StandardOutputWire<SyncProgress> syncProgressOutput;

    /** The most recent event window of this node, used as the local window of every {@link SyncProgress}. */
    private volatile EventWindow ownEventWindow = EventWindow.getGenesisEventWindow();

    /** The most recent event window received from each peer. */
    private final Map<NodeId, EventWindow> lastPeerWindows = new ConcurrentHashMap<>();

    /** The wiring model used for this node. Used to determine if the node is running or halted. */
    private DeterministicWiringModel deterministicWiringModel;

    /** The latest platform status of this node. Gossip is only received in statuses that permit syncing. */
    private volatile PlatformStatus platformStatus = PlatformStatus.STARTING_UP;

    /**
     * Constructor.
     *
     * @param networkConnectivity the network connections on which this gossip system will run
     * @param selfId the ID of the node running this gossip system
     */
    public SimulatedGossip(
            @NonNull final SimulatedNetworkConnectivity networkConnectivity, @NonNull final NodeId selfId) {
        this.networkConnectivity = requireNonNull(networkConnectivity);
        this.selfId = requireNonNull(selfId);
    }

    /**
     * Add an intake event counter that gets incremented for all events that enter the intake pipeline.
     *
     * @param intakeEventCounter the intake event counter
     */
    public void provideIntakeEventCounter(@NonNull final IntakeEventCounter intakeEventCounter) {
        this.intakeEventCounter = requireNonNull(intakeEventCounter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void bind(
            @NonNull final WiringModel model,
            @NonNull final BindableInputWire<PlatformEvent, Void> eventInput,
            @NonNull final BindableInputWire<EventWindow, Void> eventWindowInput,
            @NonNull final StandardOutputWire<PlatformEvent> eventOutput,
            @NonNull final BindableInputWire<NoInput, Void> startInput,
            @NonNull final BindableInputWire<NoInput, Void> stopInput,
            @NonNull final BindableInputWire<NoInput, Void> clearInput,
            @NonNull final BindableInputWire<NoInput, Void> pauseInput,
            @NonNull final BindableInputWire<NoInput, Void> resumeInput,
            @NonNull final BindableInputWire<Duration, Void> systemHealthInput,
            @NonNull final BindableInputWire<PlatformStatus, Void> platformStatusInput,
            @NonNull final StandardOutputWire<SyncProgress> syncLagOutput) {

        this.eventOutput = requireNonNull(eventOutput);
        this.syncProgressOutput = requireNonNull(syncLagOutput);
        this.deterministicWiringModel = (DeterministicWiringModel) requireNonNull(model);
        eventInput.bindConsumer(event -> {
            // Self-created events have no sender until now; the network identifies the source by this field
            event.setSenderId(selfId);
            networkConnectivity.submitEvent(event);
        });
        eventWindowInput.bindConsumer(eventWindow -> {
            ownEventWindow = eventWindow;
            networkConnectivity.updateEventWindow(selfId, eventWindow);
            // Production reports on every sync with both windows current. Without this, the lag would stay at its
            // last value while this node catches up against peers whose windows no longer change.
            lastPeerWindows.forEach((peerId, peerWindow) ->
                    syncProgressOutput.forward(new SyncProgress(peerId, eventWindow, peerWindow)));
        });

        startInput.bindConsumer(ignored -> {});
        stopInput.bindConsumer(ignored -> {});
        clearInput.bindConsumer(ignored -> {});
        pauseInput.bindConsumer(ignored -> {});
        resumeInput.bindConsumer(ignored -> {});
        systemHealthInput.bindConsumer(ignored -> {});
        // A restarted node starts from scratch, like in production
        platformStatus = PlatformStatus.STARTING_UP;
        platformStatusInput.bindConsumer(status -> platformStatus = status);
    }

    /**
     * In production a node neither sends nor receives gossip in statuses that don't permit syncing.
     */
    private boolean canReceive() {
        return deterministicWiringModel.isRunning() && SyncStatusChecker.doesStatusPermitSync(platformStatus);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean receiveEvent(@NonNull final PlatformEvent event) {
        if (canReceive()) {
            forwardEvent(event);
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeId getNodeId() {
        return selfId;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Remembers the peer's window and reports the sync progress against that peer to the event creator.
     */
    @Override
    public boolean receiveEventWindow(@NonNull final NodeId sender, @NonNull final EventWindow eventWindow) {
        if (!canReceive()) {
            return false;
        }
        lastPeerWindows.put(sender, eventWindow);
        syncProgressOutput.forward(new SyncProgress(sender, ownEventWindow, eventWindow));
        return true;
    }

    private void forwardEvent(@NonNull final PlatformEvent event) {
        if (intakeEventCounter != null) {
            intakeEventCounter.eventEnteredIntakePipeline(event.getSenderId());
        }
        eventOutput.forward(event);
    }

    /**
     * Resets this node's gossip point so that it will receive all necessary events after a restart.
     */
    public void onRestart() {
        networkConnectivity.resetCursor(selfId);
        // The restarted event creator has no lag recorded; stale peer windows must not be replayed against it.
        lastPeerWindows.clear();
    }
}
