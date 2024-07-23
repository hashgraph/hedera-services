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
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LibraryLoaderTest {

    @Test
    void testInvalidParams() {
        assertThrows(
                NullPointerException.class,
                () -> LibraryLoader.create(null, Architecture.current(), OperatingSystem.current()));
        assertThrows(
                NullPointerException.class,
                () -> LibraryLoader.create(this.getClass(), null, OperatingSystem.current()));
        assertThrows(
                NullPointerException.class, () -> LibraryLoader.create(this.getClass(), Architecture.current(), null));
        assertThrows(NullPointerException.class, () -> LibraryLoader.create(null));
        assertThrows(NullPointerException.class, () -> LibraryLoader.create(this.getClass())
                .install(null));
        assertThrows(NullPointerException.class, () -> LibraryLoader.create(this.getClass())
                .install("hello", null));
        assertThrows(NullPointerException.class, () -> LibraryLoader.create(this.getClass())
                .install(null, Map.of()));
        assertThrows(NullPointerException.class, () -> LibraryLoader.create(
                        this.getClass(), getOtherArchitecture(), getOtherSo())
                .install("hello", null));
        assertThrows(NullPointerException.class, () -> LibraryLoader.create(
                        this.getClass(), getOtherArchitecture(), getOtherSo())
                .install(null, Map.of()));
    }

    @Test
    void testInstallDoesNotThrowException() {
        assertDoesNotThrow(() -> LibraryLoader.create(this.getClass()).install("greeter"));
    }

    @Test
    void testInvoke() {
        // Load native library hello.dll (Windows) or greeter.so (Linux) greeter.dylib (Mac)
        LibraryLoader.create(this.getClass()).install("greeter");

        assertDoesNotThrow(() -> new Greeter().getGreeting());
    }

    @Test
    void testResult() {
        LibraryLoader.create(this.getClass()).install("greeter");
        // Load native library hello.dll (Windows) or greeter.so (Linux) greeter.dylib (Mac)
        assertEquals("Hello, World from C++!", new Greeter().getGreeting());
    }

    @Test
    void testInvalidLib() {
        assertThrows(IllegalStateException.class, () -> LibraryLoader.create(this.getClass())
                .install("bye"));
    }

    @Test
    void testInvalidExtensionLib() {
        assertThrows(IllegalStateException.class, () -> LibraryLoader.create(this.getClass())
                .install("hello", Map.of(OperatingSystem.DARWIN, "X")));
    }

    @Test
    void testNoExtensionLib() {
        assertThrows(IllegalStateException.class, () -> LibraryLoader.create(this.getClass())
                .install("hello", Map.of()));
    }

    @Test
    void conditionallyLoad() {
        Architecture otherArch = getOtherArchitecture();
        OperatingSystem otherSo = getOtherSo();
        assertEquals(
                LibraryLoader.NO_OP_LOADER,
                LibraryLoader.create(this.getClass(), otherArch, OperatingSystem.current()));
        assertEquals(
                LibraryLoader.NO_OP_LOADER, LibraryLoader.create(this.getClass(), Architecture.current(), otherSo));
        assertEquals(LibraryLoader.NO_OP_LOADER, LibraryLoader.create(this.getClass(), otherArch, otherSo));

        assertDoesNotThrow(
                () -> LibraryLoader.create(this.getClass(), otherArch, otherSo).install("someNonExistentLib"));
    }

    private OperatingSystem getOtherSo() {
        return Arrays.stream(OperatingSystem.values())
                .filter(os -> os != OperatingSystem.current())
                .findFirst()
                .orElseThrow();
    }

    private Architecture getOtherArchitecture() {
        return Arrays.stream(Architecture.values())
                .filter(architecture -> architecture != Architecture.current())
                .findFirst()
                .orElseThrow();
    }
}
