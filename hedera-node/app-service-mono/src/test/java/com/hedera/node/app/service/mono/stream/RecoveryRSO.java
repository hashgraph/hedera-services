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

package com.hedera.node.app.service.mono.stream;

/**
 * POJO to read a JSON object from the {@link RecordStreamRecoveryTest}'s "recovery stream" resource.
 */
public class RecoveryRSO {
    private String consensusTime;
    private String b64Record;
    private String b64Transaction;

    public String getConsensusTime() {
        return consensusTime;
    }

    public void setConsensusTime(String consensusTime) {
        this.consensusTime = consensusTime;
    }

    public String getB64Record() {
        return b64Record;
    }

    public void setB64Record(String b64Record) {
        this.b64Record = b64Record;
    }

    public String getB64Transaction() {
        return b64Transaction;
    }

    public void setB64Transaction(String b64Transaction) {
        this.b64Transaction = b64Transaction;
    }
}
