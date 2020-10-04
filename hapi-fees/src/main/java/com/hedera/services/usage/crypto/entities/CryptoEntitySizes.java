package com.hedera.services.usage.crypto.entities;

import com.hederahashgraph.api.proto.java.Key;

import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BOOL_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.LONG_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.getAccountKeyStorageSize;

public enum CryptoEntitySizes {
	CRYPTO_ENTITY_SIZES;

	/* { deleted, smartContract, receiverSigRequired } */
	static int NUM_FLAGS_IN_BASE_ACCOUNT_REPRESENTATION = 3;
	/* { expiry, hbarBalance, autoRenewSecs, senderThresh, receiverThresh } */
	static int NUM_LONG_FIELDS_IN_BASE_ACCOUNT_REPRESENTATION = 5;

	public int bytesInTokenAssocRepr() {
		return LONG_SIZE + 2 * BOOL_SIZE;
	}

	public int fixedBytesInAccountRepr() {
		return NUM_FLAGS_IN_BASE_ACCOUNT_REPRESENTATION * BOOL_SIZE
				+ NUM_LONG_FIELDS_IN_BASE_ACCOUNT_REPRESENTATION * LONG_SIZE;
	}
}
