package com.hedera.services.state.virtual;

import com.hedera.services.state.merkle.internals.BitPackUtils;
import com.hedera.services.utils.MiscUtils;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualKey;

import java.io.IOException;
import java.nio.ByteBuffer;

import static com.hedera.services.files.store.FcBlobsBytesStore.LEGACY_BLOB_CODE_INDEX;
import static java.lang.Long.parseLong;

public class VirtualBlobKey implements VirtualKey {
	private static final int CURRENT_VERSION = 1;
	private static final int BYTES_IN_SERIALIZED_FORM = 5;
	private static final long CLASS_ID = 0x11b982c14217d523L;

	private static final Type[] BLOB_TYPES = Type.values();

	public enum Type {
		FILE_DATA, FILE_METADATA, CONTRACT_BYTECODE, SYSTEM_DELETED_ENTITY_EXPIRY
	}

	private Type type;
	private int entityNumCode;

	public VirtualBlobKey() {
		/* Required by deserialization facility */
	}

	public VirtualBlobKey(final Type type, final int entityNumCode) {
		this.type = type;
		this.entityNumCode = entityNumCode;
	}

	public static VirtualBlobKey fromPath(final String path) {
		final var code = path.charAt(LEGACY_BLOB_CODE_INDEX);
		final var packedNum = BitPackUtils.codeFromNum(parseLong(path.substring(LEGACY_BLOB_CODE_INDEX + 1)));

		switch (code) {
			case 'f':
				return new VirtualBlobKey(Type.FILE_DATA, packedNum);
			case 'k':
				return new VirtualBlobKey(Type.FILE_METADATA, packedNum);
			case 's':
				return new VirtualBlobKey(Type.CONTRACT_BYTECODE, packedNum);
			case 'e':
				return new VirtualBlobKey(Type.SYSTEM_DELETED_ENTITY_EXPIRY, packedNum);
			default:
				throw new IllegalArgumentException("Invalid code in blob path '" + path + "'");
		}
	}

	@Override
	public void serialize(final ByteBuffer buffer) throws IOException {
		buffer.put((byte) type.ordinal());
		buffer.putInt(entityNumCode);
	}

	@Override
	public void deserialize(ByteBuffer buffer, int version) throws IOException {
		type = BLOB_TYPES[0xff & buffer.get()];
		entityNumCode = buffer.getInt();
	}

	@Override
	public boolean equals(ByteBuffer buffer, int version) throws IOException {
		return type.ordinal() == (0xff & buffer.get()) && entityNumCode == buffer.getInt();
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		type = BLOB_TYPES[0xff & in.readByte()];
		entityNumCode = in.readInt();
	}

	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeByte(type.ordinal());
		out.writeInt(entityNumCode);
	}

	@Override
	public int getVersion() {
		return CURRENT_VERSION;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || VirtualBlobKey.class != o.getClass()) {
			return false;
		}

		var that = (VirtualBlobKey) o;

		return this.type == that.type && this.entityNumCode == that.entityNumCode;
	}

	@Override
	public int hashCode() {
		return (int) MiscUtils.perm64(entityNumCode | ((long) type.ordinal()) << 4);
	}

	public static int sizeInBytes() {
		return BYTES_IN_SERIALIZED_FORM;
	}
}
