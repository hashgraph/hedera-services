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

package com.swirlds.virtualmap.internal.merkle;

import static com.swirlds.virtualmap.internal.Path.INVALID_PATH;

import com.swirlds.virtualmap.internal.VirtualStateAccessor;

public class DummyVirtualStateAccessor implements VirtualStateAccessor {
    private long firstLeafPath = INVALID_PATH;
    private long lastLeafPath = INVALID_PATH;

    public DummyVirtualStateAccessor() {}

    @Override
    public String getLabel() {
        return "DummyLabel";
    }

    @Override
    public long getFirstLeafPath() {
        return firstLeafPath;
    }

    @Override
    public long getLastLeafPath() {
        return lastLeafPath;
    }

    @Override
    public void setFirstLeafPath(final long path) {
        this.firstLeafPath = path;
    }

    @Override
    public void setLastLeafPath(final long path) {
        this.lastLeafPath = path;
    }

    @Override
    public long size() {
        return lastLeafPath == INVALID_PATH ? 0 : (lastLeafPath - firstLeafPath + 1);
    }
}
