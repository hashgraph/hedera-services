package com.hedera.services.legacy.core.jproto;

import com.swirlds.common.CommonUtils;

/**
 * Maps to proto Key of type ECDSA_secp256k1Key
 */

public class JECDSA_secp256k1Key extends JKey {

	private static final long serialVersionUID = 1L;
	private byte[] ECDSA_secp256k1Key = null;
	public static final int ECDSAsecp256_COMPRESSED_BYTE_LENGTH = 33;

	public JECDSA_secp256k1Key(final byte[] ECDSA_secp256k1Key) {
		this.ECDSA_secp256k1Key = ECDSA_secp256k1Key;
	}

	@Override
	public boolean isEmpty() {
		return ((null == ECDSA_secp256k1Key) || (0 == ECDSA_secp256k1Key.length));
	}

	@Override
	public boolean isValid() {
		if (isEmpty()) {
			return false;
		} else if ((ECDSA_secp256k1Key.length != ECDSAsecp256_COMPRESSED_BYTE_LENGTH)
				|| (ECDSA_secp256k1Key[0] != 0x02 && ECDSA_secp256k1Key[0] != 0x03)) {
			return false;
		}
		return true;
	}

	public byte[] getECDSAsecp256k1Key() {
		return ECDSA_secp256k1Key;
	}

	@Override
	public String toString() {
		return "<JECDSA_secp256k1Key: ECDSA_secp256k1Key hex=" + CommonUtils.hex(ECDSA_secp256k1Key) + ">";
	}
}
