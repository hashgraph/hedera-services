/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.state;

import com.swirlds.platform.state.iss.IssDetector;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.system.state.notifications.IssNotification;
import com.swirlds.platform.wiring.components.StateAndRound;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A helper class for testing the {@link com.swirlds.platform.state.iss.IssDetector}.
 */
public class IssDetectorTestHelper {
    private int selfIssCount = 0;
    private int catastrophicIssCount = 0;

    private final List<IssNotification> issNotificationList = new ArrayList<>();

    private final IssDetector issDetector;

    public IssDetectorTestHelper(@NonNull final IssDetector issDetector) {
        this.issDetector = Objects.requireNonNull(issDetector);
    }

    public void handleStateAndRound(@NonNull final StateAndRound stateAndRound) {
        trackIssNotifications(issDetector.handleStateAndRound(stateAndRound));
    }

    public void overridingState(@NonNull final ReservedSignedState state) {
        trackIssNotifications(issDetector.overridingState(state));
    }

    /**
     * Keeps track of all ISS notifications passed to this method over the course of a test, for the sake of validation
     *
     * @param notifications the list of ISS notifications to track. permitted to be null.
     */
    private void trackIssNotifications(@Nullable final List<IssNotification> notifications) {
        if (notifications == null) {
            return;
        }

        notifications.forEach(notification -> {
            if (notification.getIssType() == IssNotification.IssType.SELF_ISS) {
                selfIssCount++;
            } else if (notification.getIssType() == IssNotification.IssType.CATASTROPHIC_ISS) {
                catastrophicIssCount++;
            }

            issNotificationList.add(notification);
        });
    }

    /**
     * Get the number of self ISS notifications that have been observed.
     *
     * @return the number of self ISS notifications
     */
    public int getSelfIssCount() {
        return selfIssCount;
    }

    /**
     * Get the number of catastrophic ISS notifications that have been observed.
     *
     * @return the number of catastrophic ISS notifications
     */
    public int getCatastrophicIssCount() {
        return catastrophicIssCount;
    }

    /**
     * Get the list of all ISS notifications that have been observed.
     *
     * @return the list of all ISS notifications
     */
    public List<IssNotification> getIssNotificationList() {
        return issNotificationList;
    }
}
