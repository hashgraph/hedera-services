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

import com.swirlds.common.connector.impl.ConnectionFactoryImpl;
import org.junit.jupiter.api.Test;

public class ConnectorTest {

    @Test
    void test1() throws InterruptedException {
        ConnectionFactory factory = new ConnectionFactoryImpl();

        final TestModule1 module1 = new TestModule1(factory);
        final TestModule2 module2 = new TestModule2(factory);
        final TestModule3 module3 = new TestModule3(factory);
        final TestModule4 module4 = new TestModule4(factory);

        final Connection<String> connection1 = factory.connect(module1.getRandomStringOutput(), module2.getInput());
        final Connection<String> connection2 = factory.connectForOffer(module2.getOutput(), module3.getInput());
        final Connection<String> connection3 = factory.connect(module3.getOutput(), module4.getStringInput());
        factory.connect(module1.getCurrentTimeOutput(), module4.getLocalDateTimeInput());

        Thread.sleep(10_000);
        connection1.close();
        connection2.close();
        connection3.close();
        Thread.sleep(10_000);
    }
}
