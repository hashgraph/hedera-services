/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.state.forensics;

import com.hedera.node.app.service.mono.ServicesState;
import com.hedera.node.app.service.mono.context.domain.trackers.IssEventInfo;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.state.notifications.IssListener;
import com.swirlds.common.system.state.notifications.IssNotification;
import com.swirlds.common.utility.AutoCloseableWrapper;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class ServicesIssListener implements IssListener {
    private static final Logger log = LogManager.getLogger(ServicesIssListener.class);

    static final String ISS_ERROR_MSG_PATTERN =
            "In round %d, received a signed state from node %d with differing signature";
    static final String ISS_FALLBACK_ERROR_MSG_PATTERN =
            "In round %d, this node received a signed state from node %d differing signature";

    private final IssEventInfo issEventInfo;
    private final Platform platform;

    @Inject
    public ServicesIssListener(final IssEventInfo issEventInfo, final Platform platform) {
        this.issEventInfo = issEventInfo;
        this.platform = platform;
    }

    @Override
    public void notify(final IssNotification notice) {
        if (notice.getIssType() == IssNotification.IssType.OTHER_ISS) {
            // Another node is experiencing an ISS, but we are healthy.
            return;
        }

        final long round = notice.getRound();
        final long otherNodeId = notice.getOtherNodeId();
        try (final AutoCloseableWrapper<ServicesState> wrapper =
                platform.getLatestImmutableState()) {
            final ServicesState issState = wrapper.get();
            issEventInfo.alert(issState.getTimeOfLastHandledTxn());
            if (issEventInfo.shouldLogThisRound()) {
                issEventInfo.decrementRoundsToLog();
                final String msg = String.format(ISS_ERROR_MSG_PATTERN, round, otherNodeId);
                log.error(msg);
                issState.logSummary();
            }
        } catch (final Exception any) {
            final String fallbackMsg =
                    String.format(ISS_FALLBACK_ERROR_MSG_PATTERN, round, otherNodeId);
            log.warn(fallbackMsg, any);
        }
    }
}
