/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.threading.locks.locked;

import com.swirlds.common.threading.locks.AutoClosableLock;

/**
 * Returned by the {@link AutoClosableLock} when the caller is not sure if the lock has been acquired or not.
 */
public interface MaybeLocked extends Locked {
    /** A convenience singleton to return when the lock has not been acquired */
    MaybeLocked NOT_ACQUIRED = new MaybeLocked() {
        @Override
        public boolean isLockAcquired() {
            return false;
        }

        @Override
        public void close() {
            // do nothing
        }
    };

    /**
     * @return true if the lock has been acquired, false otherwise
     */
    boolean isLockAcquired();
}
