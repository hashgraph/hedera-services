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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.PlatformEvent;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommonPcesWriterTest {

    private PcesFileManager fileManager;
    private CommonPcesWriter commonPcesWriter;
    private PcesMutableFile pcesMutableFile;

    @BeforeEach
    void setUp() throws Exception {
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final PlatformContext platformContext = mock(PlatformContext.class);
        when(platformContext.getConfiguration()).thenReturn(configuration);

        fileManager = mock(PcesFileManager.class);
        final PcesFile pcesFile = mock(PcesFile.class);
        when(fileManager.getNextFileDescriptor(anyLong(), anyLong())).thenReturn(pcesFile);
        pcesMutableFile = mock(PcesMutableFile.class);
        when(pcesFile.getMutableFile(anyBoolean(), anyBoolean())).thenReturn(pcesMutableFile);

        // Initialize CommonPcesWriter with mocks
        commonPcesWriter = new CommonPcesWriter(platformContext, fileManager, true);
    }

    @Test
    void testBeginStreamingNewEvents() {
        commonPcesWriter.beginStreamingNewEvents();
        assertTrue(commonPcesWriter.isStreamingNewEvents(), "New event streaming should start.");
    }

    @Test
    void testBeginStreamingNewEventsAlreadyStreaming() {
        commonPcesWriter.beginStreamingNewEvents();
        // Expect a log error but no exception thrown
        commonPcesWriter.beginStreamingNewEvents();
    }

    @Test
    void testRegisterDiscontinuity() throws IOException {
        commonPcesWriter.beginStreamingNewEvents();
        commonPcesWriter.prepareOutputStream(mock(PlatformEvent.class));
        commonPcesWriter.registerDiscontinuity(10L);

        // Verify file closing and file manager interactions
        verify(fileManager, times(1)).registerDiscontinuity(10L);
        verify(pcesMutableFile, times(1)).close();
        verify(fileManager, times(1)).registerDiscontinuity(10L);
    }

    @Test
    void testUpdateNonAncientEventBoundary() {
        EventWindow mockWindow = mock(EventWindow.class);
        when(mockWindow.getAncientThreshold()).thenReturn(100L);

        commonPcesWriter.updateNonAncientEventBoundary(mockWindow);

        assertEquals(100L, commonPcesWriter.getNonAncientBoundary(), "Non-ancient boundary should be updated.");
    }

    @Test
    void testSetMinimumAncientIdentifierToStore() throws IOException {
        commonPcesWriter.beginStreamingNewEvents();
        commonPcesWriter.setMinimumAncientIdentifierToStore(50L);

        verify(fileManager, times(1)).pruneOldFiles(50L);
    }

    @Test
    void testPrepareOutputStreamCreatesNewFile() throws IOException {
        PlatformEvent mockEvent = mock(PlatformEvent.class);
        when(mockEvent.getAncientIndicator(any())).thenReturn(150L);

        boolean fileClosed = commonPcesWriter.prepareOutputStream(mockEvent);
        assertFalse(fileClosed, "A new file should have been created but not closed.");
    }

    @Test
    void testCloseCurrentMutableFile() throws IOException {
        commonPcesWriter.beginStreamingNewEvents();
        commonPcesWriter.prepareOutputStream(mock(PlatformEvent.class));
        commonPcesWriter.closeCurrentMutableFile();
        verify(pcesMutableFile, times(1)).close();
    }
}
