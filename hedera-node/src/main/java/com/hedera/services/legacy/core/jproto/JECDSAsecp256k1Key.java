package com.hedera.services.legacy.core.jproto;

import com.swirlds.common.CommonUtils;

/**
 * Maps to proto Key of type ECDSA_secp256k1Key
 */

public class JECDSAsecp256k1Key extends JKey {

	private static final long serialVersionUID = 1L;
	private byte[] ECDSAsecp256k1Key = null;
	public static final int ECDSASECP256_COMPRESSED_BYTE_LENGTH = 33;

	public JECDSAsecp256k1Key(final byte[] ECDSA_secp256k1Key) {
		this.ECDSAsecp256k1Key = ECDSA_secp256k1Key;
	}

	@Override
	public boolean isEmpty() {
		return ((null == ECDSAsecp256k1Key) || (0 == ECDSAsecp256k1Key.length));
	}

	@Override
	public boolean isValid() {
		if (isEmpty()) {
			return false;
		} else if ((ECDSAsecp256k1Key.length != ECDSASECP256_COMPRESSED_BYTE_LENGTH)
				|| (ECDSAsecp256k1Key[0] != 0x02 && ECDSAsecp256k1Key[0] != 0x03)) {
			return false;
		}
		return true;
	}

	public byte[] getECDSAsecp256k1Key() {
		return ECDSAsecp256k1Key;
	}

	@Override
	public String toString() {
		return "<JECDSAsecp256k1Key: ECDSA_secp256k1Key hex=" + CommonUtils.hex(ECDSAsecp256k1Key) + ">";
	}
}
