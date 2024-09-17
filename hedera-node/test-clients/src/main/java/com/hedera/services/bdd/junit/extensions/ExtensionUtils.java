/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.junit.extensions;

import static org.junit.platform.commons.support.AnnotationSupport.isAnnotated;

import com.hedera.services.bdd.junit.EmbeddedHapiTest;
import com.hedera.services.bdd.junit.GenesisHapiTest;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.LeakyEmbeddedHapiTest;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import com.hedera.services.bdd.junit.RepeatableHapiTest;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.reflect.Method;
import java.util.Optional;
import org.junit.jupiter.api.extension.ExtensionContext;

public class ExtensionUtils {
    public static Optional<Method> hapiTestMethodOf(@NonNull final ExtensionContext extensionContext) {
        return extensionContext.getTestMethod().filter(ExtensionUtils::isHapiTest);
    }

    private static boolean isHapiTest(@NonNull final Method method) {
        return isAnnotated(method, HapiTest.class)
                || isAnnotated(method, LeakyHapiTest.class)
                || isAnnotated(method, GenesisHapiTest.class)
                || isAnnotated(method, EmbeddedHapiTest.class)
                || isAnnotated(method, RepeatableHapiTest.class)
                || isAnnotated(method, LeakyEmbeddedHapiTest.class);
    }
}
