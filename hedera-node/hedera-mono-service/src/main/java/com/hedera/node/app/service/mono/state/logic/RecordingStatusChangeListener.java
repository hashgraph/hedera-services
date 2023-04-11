/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.logic;

import com.hedera.node.app.service.mono.context.StateChildren;
import com.hedera.node.app.service.mono.utils.replay.ReplayAssetRecording;
import com.swirlds.common.notification.listeners.PlatformStatusChangeListener;
import com.swirlds.common.notification.listeners.PlatformStatusChangeNotification;
import com.swirlds.common.system.PlatformStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RecordingStatusChangeListener implements PlatformStatusChangeListener {
    public static final String FINAL_TOPICS_ASSET = "final-topics.json";

    private static final Logger log = LogManager.getLogger(RecordingStatusChangeListener.class);
    private final StateChildren stateChildren;
    private final ReplayAssetRecording assetRecording;
    private final PlatformStatusChangeListener delegate;

    public RecordingStatusChangeListener(
            @NonNull final StateChildren stateChildren,
            @NonNull final ReplayAssetRecording assetRecording,
            @NonNull final PlatformStatusChangeListener delegate) {
        this.assetRecording = assetRecording;
        this.stateChildren = stateChildren;
        this.delegate = delegate;
    }

    @Override
    public void notify(@NonNull final PlatformStatusChangeNotification notification) {
        delegate.notify(notification);
        if (notification.getNewStatus() == PlatformStatus.FREEZE_COMPLETE) {
            log.info("Now recording the final state children for replay verification");
        }
        // TODO - for each child of state we want to verify at the end of a replay test
        // (accounts, tokens, etc.), write a representation of this child to a file,
        // preferably using PBJ for leaf representations
    }
}
