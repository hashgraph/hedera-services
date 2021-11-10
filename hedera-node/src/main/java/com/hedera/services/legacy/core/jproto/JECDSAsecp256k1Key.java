package com.hedera.services.legacy.core.jproto;

import com.swirlds.common.CommonUtils;

/**
 * Maps to proto Key of type ECDSA_secp256k1Key
 */

public class JECDSAsecp256k1Key extends JKey {

	private static final long serialVersionUID = 1L;
	private byte[] ecdsaSecp256k1Key = null;
	public static final int ECDSASECP256_COMPRESSED_BYTE_LENGTH = 33;

	public JECDSAsecp256k1Key(final byte[] ECDSA_secp256k1Key) {
		this.ecdsaSecp256k1Key = ECDSA_secp256k1Key;
	}

	@Override
	public boolean isEmpty() {
		return ((null == ecdsaSecp256k1Key) || (0 == ecdsaSecp256k1Key.length));
	}

	@Override
	public boolean isValid() {
		if (isEmpty() || (ecdsaSecp256k1Key.length != ECDSASECP256_COMPRESSED_BYTE_LENGTH)
				|| (ecdsaSecp256k1Key[0] != 0x02 && ecdsaSecp256k1Key[0] != 0x03)) {
			return false;
		}
		return true;
	}

	@Override
	public byte[] getEcdsaSecp256k1Key() {
		return ecdsaSecp256k1Key;
	}

	@Override
	public String toString() {
		return "<JECDSAsecp256k1Key: ECDSA_secp256k1Key hex=" + CommonUtils.hex(ecdsaSecp256k1Key) + ">";
	}
}
