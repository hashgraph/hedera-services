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

package com.swirlds.common.threading;

import com.swirlds.common.threading.locks.internal.AcquiredOnTry;
import com.swirlds.common.threading.locks.locked.MaybeLocked;
import com.swirlds.common.utility.CommonUtils;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * A lock that prevents 2 nodes from doing 2 syncs at the same time
 */
public class SyncLock {
    private final Lock lock;
    private final Consumer<Boolean> onObtained;
    private final Consumer<Boolean> onClose;
    private final MaybeLocked outBoundObtained;
    private final MaybeLocked inBoundObtained;

    public SyncLock(final Consumer<Boolean> onObtained, final Consumer<Boolean> onClose) {
        this(new ReentrantLock(), onObtained, onClose);
    }

    public SyncLock(final Lock lock, final Consumer<Boolean> onObtained, final Consumer<Boolean> onClose) {
        this.lock = CommonUtils.throwArgNull(lock, "lock");
        this.onObtained = CommonUtils.throwArgNull(onObtained, "onObtained");
        this.onClose = CommonUtils.throwArgNull(onClose, "onClose");
        this.outBoundObtained = new AcquiredOnTry(this::closeOutbound);
        this.inBoundObtained = new AcquiredOnTry(this::closeInbound);
    }

    public Lock getLock() {
        return lock;
    }

    private void closeOutbound() {
        onClose.accept(true);
        lock.unlock();
    }

    private void closeInbound() {
        onClose.accept(false);
        lock.unlock();
    }

    public MaybeLocked tryLock(final boolean isOutbound) {
        if (lock.tryLock()) {
            onObtained.accept(isOutbound);
            return isOutbound ? outBoundObtained : inBoundObtained;
        }
        return MaybeLocked.NOT_ACQUIRED;
    }
}
