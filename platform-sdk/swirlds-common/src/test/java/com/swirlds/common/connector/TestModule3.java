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

public class TestModule3 {

    private InputWithBackpressure<String> input;

    private Publisher<String> publisher;

    public TestModule3(ConnectionFactory connectionFactory) {
        this.input = connectionFactory.createAsyncInputWithBackpressure(this::onMessage, 2);
        this.publisher = connectionFactory.createPublisher();
    }

    private void onMessage(String message) {
        System.out.println("3 received message: " + message + " - will forward it in 2 seconds");
        try {
            Thread.sleep(2_000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        publisher.publish(message);
    }

    public InputWithBackpressure<String> getInput() {
        return input;
    }

    public Output<String> getOutput() {
        return publisher;
    }
}
