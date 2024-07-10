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

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Executors;

public class TestModule1 {

    private final Publisher<String> randomStringPublisher;

    private final Publisher<LocalDateTime> currentTimePublisher;

    private final Publisher<Long> countUpPublisher;

    public TestModule1(ConnectionFactory connectionFactory) {
        randomStringPublisher = connectionFactory.createPublisher();
        currentTimePublisher = connectionFactory.createPublisher();
        countUpPublisher = connectionFactory.createPublisher();

        Executors.newSingleThreadExecutor().submit(() -> {
            while (true) {
                randomStringPublisher.publish(UUID.randomUUID().toString());
                currentTimePublisher.publish(LocalDateTime.now());
                countUpPublisher.publish(System.currentTimeMillis());
                Thread.sleep(100);
            }
        });
    }

    public Output<String> getRandomStringOutput() {
        return randomStringPublisher;
    }

    public Output<LocalDateTime> getCurrentTimeOutput() {
        return currentTimePublisher;
    }

    public Output<Long> getCountUpOutput() {
        return countUpPublisher;
    }
}
