package virtual;

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.jasperdb.SelfSerializableSupplier;

import java.io.IOException;

public class IdSupplier implements SelfSerializableSupplier<Id> {
	private static final int CURRENT_VERSION = 1;
	private static final long CLASS_ID = 0x5b6195ab7b58c16fL;

	@Override
	public void deserialize(SerializableDataInputStream serializableDataInputStream, int i) throws IOException {

	}

	@Override
	public void serialize(SerializableDataOutputStream serializableDataOutputStream) throws IOException {

	}

	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	@Override
	public int getVersion() {
		return CURRENT_VERSION;
	}

	@Override
	public Id get() {
		return new Id();
	}
}
