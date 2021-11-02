package com.hedera.services.state.virtual;

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.jasperdb.SelfSerializableSupplier;

import java.io.IOException;

public class ContractValueSupplier implements SelfSerializableSupplier<ContractValue> {
	static final long CLASS_ID = 0x11cd526cdc0e925dL;
	static final int CURRENT_VERSION = 1;

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		/* No-op */
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		/* No-op */
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
	public ContractValue get() {
		return new ContractValue();
	}
}
