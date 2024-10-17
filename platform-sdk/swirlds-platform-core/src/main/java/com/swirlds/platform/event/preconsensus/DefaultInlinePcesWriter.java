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

package com.swirlds.platform.event.preconsensus;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.PlatformEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DefaultInlinePcesWriter implements InlinePcesWriter {
    private static final Logger logger = LogManager.getLogger(DefaultInlinePcesWriter.class);

    private final CommonPcesWriter commonPcesWriter;
    /**
     * Constructor
     *
     * @param platformContext the platform context
     * @param fileManager     manages all preconsensus event stream files currently on disk
     */
    public DefaultInlinePcesWriter(
            @NonNull final PlatformContext platformContext, @NonNull final PcesFileManager fileManager) {
        Objects.requireNonNull(platformContext, "platformContext is required");
        Objects.requireNonNull(fileManager, "fileManager is required");
        commonPcesWriter = new CommonPcesWriter(platformContext, fileManager);
    }

    @Override
    public void beginStreamingNewEvents() {
        commonPcesWriter.beginStreamingNewEvents();
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public PlatformEvent writeEvent(@NonNull PlatformEvent event) {
        if (event.getStreamSequenceNumber() == PlatformEvent.NO_STREAM_SEQUENCE_NUMBER) {
            throw new IllegalStateException("Event must have a valid stream sequence number");
        }

        // if we aren't streaming new events yet, assume that the given event is already durable
        if (!commonPcesWriter.isStreamingNewEvents()) {
            commonPcesWriter.setLastWrittenEvent(event.getStreamSequenceNumber());
            commonPcesWriter.setLastFlushedEvent(event.getStreamSequenceNumber());
            return event;
        }

        // don't do anything with ancient events
        if (event.getAncientIndicator(commonPcesWriter.getFileType()) < commonPcesWriter.getNonAncientBoundary()) {
            return null;
        }

        try {
            final boolean fileClosed = commonPcesWriter.prepareOutputStream(event);
            commonPcesWriter.getCurrentMutableFile().writeEvent(event);
            commonPcesWriter.setLastWrittenEvent(event.getStreamSequenceNumber());

            commonPcesWriter.getCurrentMutableFile().flush();
            commonPcesWriter.setLastFlushedEvent(commonPcesWriter.getLastWrittenEvent());

            return event;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerDiscontinuity(@NonNull Long newOriginRound) {
        commonPcesWriter.registerDiscontinuity(newOriginRound);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateNonAncientEventBoundary(@NonNull EventWindow nonAncientBoundary) {
        commonPcesWriter.updateNonAncientEventBoundary(nonAncientBoundary);
    }

    @Override
    public void setMinimumAncientIdentifierToStore(@NonNull final Long minimumAncientIdentifierToStore) {
        commonPcesWriter.setMinimumAncientIdentifierToStore(minimumAncientIdentifierToStore);
    }
}
