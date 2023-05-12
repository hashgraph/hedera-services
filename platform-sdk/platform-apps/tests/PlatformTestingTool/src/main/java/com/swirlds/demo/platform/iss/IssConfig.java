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

package com.swirlds.demo.platform.iss;

import java.time.Duration;
import java.time.Instant;

public class IssConfig {
    private int issSecondsAfterStart;

    public int getIssSecondsAfterStart() {
        return issSecondsAfterStart;
    }

    public void setIssSecondsAfterStart(int issSecondsAfterStart) {
        this.issSecondsAfterStart = issSecondsAfterStart;
    }

    public boolean shouldSendIssTransaction(Instant startTime) {
        return Duration.between(startTime, Instant.now()).getSeconds() > issSecondsAfterStart;
    }
}
