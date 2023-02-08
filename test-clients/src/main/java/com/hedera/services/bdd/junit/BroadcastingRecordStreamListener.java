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
package com.hedera.services.bdd.junit;

import static com.hedera.node.app.hapi.utils.exports.recordstreaming.RecordStreamingUtils.isRecordFile;

import com.hedera.services.stream.proto.RecordStreamItem;
import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A small utility class that listens for record stream files and provides them to any subscribed
 * listeners.
 */
public class BroadcastingRecordStreamListener extends FileAlterationListenerAdaptor {
    private static final Logger log = LogManager.getLogger(BroadcastingRecordStreamListener.class);
    private final List<Consumer<RecordStreamItem>> listeners = new CopyOnWriteArrayList<>();

    /**
     * Subscribes a listener to receive record stream items.
     *
     * @param listener the listener to subscribe
     * @return a runnable that can be used to unsubscribe the listener
     */
    public Runnable subscribe(final Consumer<RecordStreamItem> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    @Override
    public void onFileCreate(final File file) {
        if (!isRecordFile(file.getName())) {
            return;
        }
        log.info(
                "Providing validators with access to record stream file {}",
                file.getAbsolutePath());
        final var contents = RecordStreamAccess.ensurePresentRecordFile(file.getAbsolutePath());
        contents.getRecordStreamItemsList().forEach(item -> listeners.forEach(l -> l.accept(item)));
    }

    public int numListeners() {
        return listeners.size();
    }
}
