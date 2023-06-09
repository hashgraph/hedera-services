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

package com.swirlds.common.system.platformstatus;

import com.swirlds.common.UniqueId;
import com.swirlds.common.system.platformstatus.statuslogic.ActiveStatusLogic;
import com.swirlds.common.system.platformstatus.statuslogic.BehindStatusLogic;
import com.swirlds.common.system.platformstatus.statuslogic.CatastrophicFailureStatusLogic;
import com.swirlds.common.system.platformstatus.statuslogic.CheckingStatusLogic;
import com.swirlds.common.system.platformstatus.statuslogic.DisconnectedStatusLogic;
import com.swirlds.common.system.platformstatus.statuslogic.FreezeCompleteStatusLogic;
import com.swirlds.common.system.platformstatus.statuslogic.FreezingStatusLogic;
import com.swirlds.common.system.platformstatus.statuslogic.ObservingStatusLogic;
import com.swirlds.common.system.platformstatus.statuslogic.PlatformStatusLogic;
import com.swirlds.common.system.platformstatus.statuslogic.ReconnectCompleteStatusLogic;
import com.swirlds.common.system.platformstatus.statuslogic.ReplayingEventsStatusLogic;
import com.swirlds.common.system.platformstatus.statuslogic.SavingFreezeStateStatusLogic;
import com.swirlds.common.system.platformstatus.statuslogic.StartingUpStatusLogic;
import com.swirlds.common.time.Time;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Objects;

/**
 * The status of the Platform
 * <p>
 * NOTE: not all of these statuses can currently be reached. The status state machine is still under development. In
 * such cases, the documentation will indicate that the status is "not in use"
 */
public enum PlatformStatus implements UniqueId {
    /**
     * The platform is starting up.
     */
    STARTING_UP(1, new StartingUpStatusLogic()),
    /**
     * The platform is gossiping, creating events, and accepting app transactions.
     */
    ACTIVE(2, new ActiveStatusLogic()),
    /**
     * The platform is not currently connected to any other computers on the network.
     * <p>
     * NOTE: This is still in use, but will be retired once the status state machine is complete.
     */
    @Deprecated(forRemoval = true)
    DISCONNECTED(3, new DisconnectedStatusLogic()),
    /**
     * The Platform does not have the latest state, and needs to reconnect. The platform is not gossiping.
     */
    BEHIND(4, new BehindStatusLogic()),
    /**
     * A freeze timestamp has been crossed, and the platform is in the process of freezing. The platform is gossiping
     * and creating events, but not accepting app transactions.
     */
    FREEZING(5, new FreezingStatusLogic()),
    /**
     * The platform has been frozen, and is idle.
     */
    FREEZE_COMPLETE(6, new FreezeCompleteStatusLogic()),
    /**
     * The platform is replaying events from the preconsensus event stream.
     * <p>
     * NOTE: not in use
     */
    REPLAYING_EVENTS(7, new ReplayingEventsStatusLogic()),
    /**
     * The platform has just started, and is observing the network. The platform is gossiping, but will not create
     * events.
     * <p>
     * NOTE: not in use
     */
    OBSERVING(8, new ObservingStatusLogic()),
    /**
     * The platform has started up or has finished reconnecting, and is now ready to rejoin the network. The platform is
     * gossiping and creating events, but not yet accepting app transactions.
     * <p>
     * NOTE: not in use
     */
    CHECKING(9, new CheckingStatusLogic()),
    /**
     * The platform has just finished reconnecting. The platform is gossiping, but is waiting to write a state to disk
     * before creating events or accepting app transactions.
     * <p>
     * NOTE: not in use
     */
    RECONNECT_COMPLETE(10, new ReconnectCompleteStatusLogic()),
    /**
     * The platform has encountered a failure, and is unable to continue. The platform is idle.
     * <p>
     * NOTE: not in use
     */
    CATASTROPHIC_FAILURE(11, new CatastrophicFailureStatusLogic()),
    /**
     * The platform is done gossiping, and is in the process of writing a final freeze state to disk.
     */
    SAVING_FREEZE_STATE(12, new SavingFreezeStateStatusLogic());

    /**
     * Unique ID of the enum value
     */
    private final int id;

    /**
     * An object encapsulating the logic to process {@link PlatformStatusAction}s while in this status
     */
    private final PlatformStatusLogic statusLogic;

    /**
     * Constructs an enum instance
     *
     * @param id unique ID of the instance
     */
    PlatformStatus(final int id, @NonNull final PlatformStatusLogic statusLogic) {
        this.id = id;
        this.statusLogic = Objects.requireNonNull(statusLogic);
    }

    @Override
    public int getId() {
        return id;
    }

    /**
     * Process a status action.
     * <p>
     * If the input action causes a status transition, then this method will return the new status. Otherwise, it will
     * return the same status as before processing the action.
     *
     * @param action          the status action that has occurred
     * @param statusStartTime the time at which the current status started
     * @param time            a source of time
     * @param config          the platform status config
     * @return the status after processing the action. may be the same status as before processing
     * @throws IllegalArgumentException if the input action is not expected in the current status
     */
    @NonNull
    public PlatformStatus processStatusAction(
            @NonNull final PlatformStatusAction action,
            @NonNull final Instant statusStartTime,
            @NonNull final Time time,
            @NonNull final PlatformStatusConfig config) {

        return statusLogic.processStatusAction(action, statusStartTime, time, config);
    }
}
