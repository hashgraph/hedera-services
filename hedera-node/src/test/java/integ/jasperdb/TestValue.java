/*
 * (c) 2016-2021 Swirlds, Inc.
 *
 * This software is the confidential and proprietary information of
 * Swirlds, Inc. ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Swirlds.
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. SWIRLDS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

package integ.jasperdb;

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualValue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class TestValue implements VirtualValue {
    public static final int BYTES = 25; // Just picking a number
    private String s;

    public TestValue() { }

    TestValue(String s) {
        this.s = s;
    }

    @Override
    public long getClassId() {
        return 0x155bb9565ebfad3aL;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeUTF(s);
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        s = in.readUTF();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestValue Element = (TestValue) o;
        return Objects.equals(s, Element.s);
    }

    @Override
    public int hashCode() {
        return Objects.hash(s);
    }

    @Override
    public String toString() {
        return "VFCValue{ " + s + " }";
    }

    @Override
    public void serialize(ByteBuffer buffer) throws IOException {
        buffer.putInt(s.length());
        buffer.put(s.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void deserialize(ByteBuffer buffer, int version) throws IOException {
        final var length = buffer.getInt();
        final var bytes = new byte[length];
        buffer.get(bytes);
        s = new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public TestValue copy() {
        return new TestValue(s);
    }

    @Override
    public VirtualValue asReadOnly() {
        return this; // No setters on this thing, just don't deserialize...
    }

    @Override
    public void release() {

    }
}
