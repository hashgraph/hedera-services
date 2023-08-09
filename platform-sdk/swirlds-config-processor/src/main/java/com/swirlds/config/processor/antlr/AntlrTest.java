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

package com.swirlds.config.processor.antlr;

import com.swirlds.config.processor.ConfigDataRecordDefinition;
import com.swirlds.config.processor.DocumentationFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

public class AntlrTest {

    public static void main(String[] args) throws Exception {

        final String path =
                "/Users/hendrikebbers/git/hedera-services/platform-sdk/swirlds-common/src/main/java/com/swirlds/common/config/StateConfig.java";
        final String content = Files.readString(Path.of(path));
        final List<ConfigDataRecordDefinition> definitions = AntlrConfigRecordParser.parse(content);

        IntStream.range(0, definitions.size()).forEach(i -> {
            try {
                ConfigDataRecordDefinition recordDefinition = definitions.get(i);
                System.out.println("Creating doc for " + recordDefinition.simpleClassName());
                DocumentationFactory.doWork(
                        recordDefinition, Path.of(System.getProperty("user.dir"), "test-config-" + i + ".md"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
