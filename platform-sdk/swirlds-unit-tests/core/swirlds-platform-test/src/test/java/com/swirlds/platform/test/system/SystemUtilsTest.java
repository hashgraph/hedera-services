/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.system;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.platform.system.SystemExitCode;
import com.swirlds.platform.system.SystemExitUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class SystemUtilsTest {
    @Test
    @Disabled("Not longer supported in Java 21")
    void checkDefaultCharsetTest() {
        // System.setSecurityManager() is deprecated, but they do intend on providing an alternative in future release
        // for blocking System.exit(). Mentioned here: https://openjdk.java.net/jeps/411

        // set up the security manager to throw an exception when System.exit is called
        System.setSecurityManager(NoExitSecurityManager.SINGLETON);

        // test with non error reason
        assertThrows(
                SystemExitException.class,
                () -> SystemExitUtils.exitSystem(SystemExitCode.NO_ERROR),
                "the method should call System.exit and throw");
        // test with error reason
        assertThrows(
                SystemExitException.class,
                () -> SystemExitUtils.exitSystem(SystemExitCode.SAVED_STATE_NOT_LOADED),
                "the method should call System.exit and throw");

        // reset the security manager
        System.setSecurityManager(null);
    }
}
