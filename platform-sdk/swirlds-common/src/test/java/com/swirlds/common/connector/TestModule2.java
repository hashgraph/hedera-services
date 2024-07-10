/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.swirlds.common.connector;

public class TestModule2 {

    private Input<String> input;

    private Publisher<String> publisher;

    public TestModule2(ConnectionFactory connectionFactory) {
        this.input = connectionFactory.createSyncedInput(d -> onMessage(d));
        this.publisher = connectionFactory.createPublisher();
    }

    private void onMessage(String message) {
        System.out.println("2 received message: " + message + " - will forward it with double lenght directly");
        publisher.publish(message.repeat(2));
    }

    public Input<String> getInput() {
        return input;
    }

    public Output<String> getOutput() {
        return publisher;
    }
}
