/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.reconnect;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class TestValue {

    private String s;

    public TestValue(String s) {
        this.s = s;
    }

    public TestValue(final ReadableSequentialData in) {
        final int len = in.readInt();
        final byte[] bytes = new byte[len];
        in.readBytes(bytes);
        this.s = new String(bytes, StandardCharsets.UTF_8);
    }

    public String getValue() {
        return s;
    }

    public int getSizeInBytes() {
        final byte[] value = s.getBytes(StandardCharsets.UTF_8);
        return Integer.BYTES + value.length;
    }

    public void writeTo(final WritableSequentialData out) {
        final byte[] value = s.getBytes(StandardCharsets.UTF_8);
        out.writeInt(value.length);
        out.writeBytes(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestValue other = (TestValue) o;
        return Objects.equals(s, other.s);
    }

    @Override
    public int hashCode() {
        return Objects.hash(s);
    }

    @Override
    public String toString() {
        return "TestValue{ " + s + " }";
    }
}
