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
import com.swirlds.virtualmap.VirtualKey;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class TestKey implements VirtualKey {
    public static final int BYTES = 25; // Just picking a number
    private String s;

    public TestKey() { }

    TestKey(String s) {
        this.s = s;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public void serialize(ByteBuffer buffer) throws IOException {
        buffer.putInt(s.length());
        buffer.put(s.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void deserialize(ByteBuffer buffer, int version) throws IOException {
        final int length = buffer.getInt();
        final byte[] bytes = new byte[length];
        buffer.get(bytes);
        s = new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public boolean equals(ByteBuffer buffer, int version) throws IOException {
        final int length = buffer.getInt();
        if (length != s.length()) return false;

        final byte[] bytes = new byte[length];
        buffer.get(bytes);
        final var tmp = new String(bytes, StandardCharsets.UTF_8);

        return s.equals(tmp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(s);
    }

    @Override
    public String toString() {
        return "VFCKey{ " + s + " }";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestKey Element = (TestKey) o;
        return Objects.equals(s, Element.s);
    }

    @Override
    public long getClassId() {
        return 0x155bb9565ebfad3bL;
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        final var length = Integer.BYTES + (s.length() * 2);
        final var buff = ByteBuffer.allocate(length); // this is wrong for some unicode characters
        serialize(buff);
        out.writeInt(length);
        out.write(buff.array());
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        final var length = in.readInt();
        final var bytes = new byte[length];
        in.read(bytes);
        deserialize(ByteBuffer.wrap(bytes), version);
    }
}
