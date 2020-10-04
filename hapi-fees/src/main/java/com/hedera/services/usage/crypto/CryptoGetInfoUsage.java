package com.hedera.services.usage.crypto;

import com.hedera.services.usage.QueryUsage;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;

import java.nio.charset.Charset;

import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.getAccountKeyStorageSize;
import static com.hedera.services.usage.crypto.entities.CryptoEntitySizes.CRYPTO_ENTITY_SIZES;

public class CryptoGetInfoUsage extends QueryUsage  {
	private CryptoGetInfoUsage(Query query) {
		super(query.getCryptoGetInfo().getHeader().getResponseType());
		updateTb(BASIC_ENTITY_ID_SIZE);
		updateRb(CRYPTO_ENTITY_SIZES.fixedBytesInAccountRepr());
	}

	public static CryptoGetInfoUsage newEstimate(Query query) {
		return new CryptoGetInfoUsage(query);
	}

	public CryptoGetInfoUsage givenCurrentKey(Key key) {
		updateRb(getAccountKeyStorageSize(key));
		return this;
	}

	public CryptoGetInfoUsage givenCurrentMemo(String memo) {
		updateRb(memo.getBytes(Charset.forName("UTF-8")).length);
		return this;
	}

	public CryptoGetInfoUsage givenCurrentTokenAssocs(int count) {
		updateRb(count * CRYPTO_ENTITY_SIZES.bytesInTokenAssocRepr());
		return this;
	}

	public CryptoGetInfoUsage givenCurrentlyUsingProxy() {
		updateRb(BASIC_ENTITY_ID_SIZE);
		return this;
	}
}
