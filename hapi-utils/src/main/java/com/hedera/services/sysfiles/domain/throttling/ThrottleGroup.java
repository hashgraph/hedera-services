/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.sysfiles.domain.throttling;

import java.util.ArrayList;
import java.util.List;

public class ThrottleGroup<E extends Enum<E>> {
    int opsPerSec;
    long milliOpsPerSec;
    List<E> operations = new ArrayList<>();

    public ThrottleGroup() {
        // Needed by Jackson
    }

    public ThrottleGroup(final long milliOpsPerSec, final List<E> operations) {
        this.milliOpsPerSec = milliOpsPerSec;
        this.operations = operations;
    }

    public void setMilliOpsPerSec(long milliOpsPerSec) {
        this.milliOpsPerSec = milliOpsPerSec;
    }

    public int getOpsPerSec() {
        return opsPerSec;
    }

    public void setOpsPerSec(int opsPerSec) {
        this.opsPerSec = opsPerSec;
    }

    public List<E> getOperations() {
        return operations;
    }

    public void setOperations(List<E> operations) {
        this.operations = operations;
    }

    public long getMilliOpsPerSec() {
        return milliOpsPerSec;
    }

    public long impliedMilliOpsPerSec() {
        return milliOpsPerSec > 0 ? milliOpsPerSec : 1_000 * opsPerSec;
    }
}
