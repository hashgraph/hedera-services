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

package com.swirlds.platform.components.state;

import com.swirlds.common.system.state.notifications.IssNotification;

public class TestIssConsumer {

    private long issRound;
    private IssNotification.IssType type;
    private Long issNodeId;
    private int numInvocations;

    public TestIssConsumer() {
        reset();
    }

    public void consume(final long round, final IssNotification.IssType issType, final Long issNodeId) {
        this.issRound = round;
        this.type = issType;
        this.issNodeId = issNodeId;
        numInvocations++;
    }

    public long getIssRound() {
        return issRound;
    }

    public IssNotification.IssType getIssType() {
        return type;
    }

    public Long getIssNodeId() {
        return issNodeId;
    }

    public int getNumInvocations() {
        return numInvocations;
    }

    public void reset() {
        issNodeId = null;
        type = null;
        issRound = -1;
        numInvocations = 0;
    }
}
