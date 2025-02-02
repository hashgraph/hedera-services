/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.lambda;

import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.suites.contract.Utils.BYTECODE_EXTENSION;
import static com.hedera.services.bdd.suites.contract.Utils.LAMBDA_RESOURCE_PATH;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.dsl.annotations.Contract;
import com.hedera.services.bdd.spec.dsl.entities.SpecContract;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

// @Disabled
public class BytecodeResourcesTest {
    @HapiTest
    final Stream<DynamicTest> writePreInitializedBytecode(
            @Contract(contract = "OneTimeCodeTransferAllowance", implementsLambda = true) SpecContract contract) {
        return hapiTest(contract.getBytecode()
                .andAssert(op -> op.exposingBytecodeTo(bytes -> {
                    final var loc =
                            String.format(LAMBDA_RESOURCE_PATH, "OneTimeCodeTransferAllowance", BYTECODE_EXTENSION);
                    try {
                        Files.write(Paths.get(loc), bytes);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })));
    }
}
