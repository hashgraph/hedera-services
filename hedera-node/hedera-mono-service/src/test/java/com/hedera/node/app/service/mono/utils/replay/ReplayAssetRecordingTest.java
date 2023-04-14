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

package com.hedera.node.app.service.mono.utils.replay;

import static com.hedera.node.app.service.mono.pbj.PbjConverter.toB64Encoding;
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.hapi.node.state.consensus.Topic;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReplayAssetRecordingTest {
    @TempDir
    private File assetDir;

    private ReplayAssetRecording subject;

    @BeforeEach
    void setUp() {
        subject = new ReplayAssetRecording(assetDir);
    }

    @Test
    void jsonAppendRecreatesOnFirstTouchAndFirstTouchOnly() throws IOException {
        Files.write(Paths.get(assetDir.toString(), "foo.txt"), List.of("not-a", "not-b"));

        final var a = new Foo("a");
        final var b = new Foo("b");

        subject.appendJsonToAsset("foo.txt", a);
        subject.appendJsonToAsset("foo.txt", b);

        final var foos = subject.readJsonLinesFromReplayAsset("foo.txt", Foo.class);

        assertEquals(2, foos.size());
        assertEquals(a, foos.get(0));
        assertEquals(b, foos.get(1));
    }

    @Test
    void canRemoveAsset() throws IOException {
        Files.write(Paths.get(assetDir.toString(), "foo.txt"), List.of("not-a", "not-b"));

        subject.restartReplayAsset("foo.txt");

        assertFalse(Files.exists(Paths.get(assetDir.toString(), "foo.txt")));
    }

    @Test
    void plaintextAppendRecreatesOnFirstTouchAndFirstTouchOnly() throws IOException {
        Files.write(Paths.get(assetDir.toString(), "foo.txt"), List.of("not-a", "not-b"));

        final var a = Topic.newBuilder().memo("a").build();
        final var b = Topic.newBuilder().memo("b").build();

        subject.appendPlaintextToAsset("foo.txt", toB64Encoding(a, Topic.class));
        subject.appendPlaintextToAsset("foo.txt", toB64Encoding(b, Topic.class));

        final var encodedFoos = subject.readPlaintextLinesFromReplayAsset("foo.txt");
        final var foos = subject.readPbjEncodingsFromReplayAsset("foo.txt", Topic.PROTOBUF);

        assertEquals(2, encodedFoos.size());
        assertEquals("QgFh", encodedFoos.get(0));
        assertEquals("QgFi", encodedFoos.get(1));

        assertEquals(2, foos.size());
        assertEquals(a, foos.get(0));
        assertEquals(b, foos.get(1));
    }

    private static class Foo {
        private String bar;

        public Foo() {}

        public Foo(@NonNull final String bar) {
            this.bar = bar;
        }

        public String getBar() {
            return bar;
        }

        public void setBar(String bar) {
            this.bar = bar;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Foo foo = (Foo) o;
            return Objects.equals(bar, foo.bar);
        }

        @Override
        public int hashCode() {
            return Objects.hash(bar);
        }
    }
}
