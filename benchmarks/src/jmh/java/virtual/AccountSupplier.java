package virtual;

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.jasperdb.SelfSerializableSupplier;
import org.jocl.CL;

import java.io.IOException;

public class AccountSupplier implements SelfSerializableSupplier<Account> {
	private static final int CURRENT_VERSION = 1;
	private static final long CLASS_ID = 0x24d54b4b7f18cc4fL;

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
	public Account get() {
		return new Account();
	}
}
