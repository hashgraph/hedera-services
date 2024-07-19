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

package com.hedera.nativesupport;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.nativesupport.jni.Greeter;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LibraryLoaderTest {

    @Test
    void testInstallDoesNotThrowException() {
        assertDoesNotThrow(() -> LibraryLoader.install("greeter"));
    }

    @Test
    void testLoadDoesNotThrowException() {
        Path p = LibraryLoader.install("greeter");
        // Load native library hello.dll (Windows) or greeter.so (Linux) greeter.dylib (Mac)
        assertDoesNotThrow(() -> System.load(p.toString()));
    }

    @Test
    void testInvoke() {
        Path p = LibraryLoader.install("greeter");
        // Load native library hello.dll (Windows) or greeter.so (Linux) greeter.dylib (Mac)
        System.load(p.toString());

        assertDoesNotThrow(() -> new Greeter().getGreeting());
    }

    @Test
    void testResult() {
        Path p = LibraryLoader.install("greeter");
        // Load native library hello.dll (Windows) or greeter.so (Linux) greeter.dylib (Mac)
        System.load(p.toString());

        assertEquals("Hello, World from C++!", new Greeter().getGreeting());
    }

    @Test
    void testInvalidLib() {
        assertThrows(IllegalStateException.class, () -> LibraryLoader.install("bye"));
    }

    @Test
    void testInvalidExtensionLib() {
        assertThrows(
                IllegalStateException.class, () -> LibraryLoader.install("hello", Map.of(OperatingSystem.DARWIN, "X")));
    }

    @Test
    void testNoExtensionLib() {
        assertThrows(IllegalStateException.class, () -> LibraryLoader.install("hello", Map.of()));
    }
}
