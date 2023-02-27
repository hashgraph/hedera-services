/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.components.appcomm;

import static com.swirlds.common.formatting.StringFormattingUtils.addLine;

import com.swirlds.common.notification.NotificationEngine;

/**
 * Creates instances of {@link DefaultAppCommunicationComponent}
 */
public class DefaultAppCommunicationComponentFactory implements AppCommunicationComponentFactory {

    /**
     * The notification engine used to communicate with the application.
     * <p>
     * FUTURE WORK: The notification engine should eventually be created in this class and not passed in.
     */
    private final NotificationEngine notificationEngine;

    public DefaultAppCommunicationComponentFactory(final NotificationEngine notificationEngine) {
        this.notificationEngine = notificationEngine;
    }

    public AppCommunicationComponent build() {
        verifyInputs();
        return new DefaultAppCommunicationComponent(notificationEngine);
    }

    private void verifyInputs() {
        final StringBuilder errors = new StringBuilder();
        if (notificationEngine == null) {
            addLine(errors, "systemTransactionSubmitter must not be null");
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Unable to build StateManagementComponent:\n" + errors);
        }
    }
}
