/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.utils;

/** Minimal implementation of {@link Pause}. */
public enum SleepingPause implements Pause {
    SLEEPING_PAUSE;

    @Override
    public boolean forMs(long n) {
        try {
            Thread.sleep(n);
            return true;
        } catch (InterruptedException ignore) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
