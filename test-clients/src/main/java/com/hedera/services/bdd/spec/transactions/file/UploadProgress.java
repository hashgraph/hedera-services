/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.spec.transactions.file;

public class UploadProgress {
    private boolean[] finishedAppends;

    public void initializeFor(final int appendsRequired) {
        finishedAppends = new boolean[appendsRequired];
    }

    public void markFinished(final int i) {
        assertInitialized();
        if (i < 0 || i >= finishedAppends.length) {
            throw new IllegalArgumentException(
                    "Only " + finishedAppends.length + " appends to do, cannot mark " + i);
        }
        finishedAppends[i] = true;
    }

    public boolean[] finishedAppends() {
        assertInitialized();
        return finishedAppends;
    }

    public int finishedAppendPrefixLength() {
        int i;
        for (i = finishedAppends.length - 1; i >= 0 && !finishedAppends[i]; i--) {
            // No-op
        }
        if (i == -1) {
            return 0;
        } else {
            for (int j = 0; j <= i; j++) {
                if (!finishedAppends[j]) {
                    return -1;
                }
            }
            return i + 1;
        }
    }

    public boolean isInitialized() {
        return finishedAppends != null;
    }

    private void assertInitialized() {
        if (!isInitialized()) {
            throw new IllegalStateException("Upload progress not initialized");
        }
    }
}
