package com.hedera.services.usage.file;

import com.hedera.services.usage.EstimatorFactory;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TransactionBody;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hedera.services.usage.SingletonUsageProperties.USAGE_PROPERTIES;
import static com.hedera.services.usage.TxnUsage.keySizeIfPresent;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BOOL_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.LONG_SIZE;

public class FileOpsUsage {
	static EstimatorFactory estimateFactory = TxnUsageEstimator::new;

	/* { deleted } */
	private static final int NUM_FLAGS_IN_BASE_FILE_REPR = 1;
	/* { expiry } */
	private static final int NUM_LONG_FIELDS_IN_BASE_FILE_REPR = 1;

	static int bytesInBaseRepr() {
		return NUM_FLAGS_IN_BASE_FILE_REPR * BOOL_SIZE
				+ NUM_LONG_FIELDS_IN_BASE_FILE_REPR * LONG_SIZE;
	}

	public FeeData fileCreateUsage(TransactionBody fileCreation, SigUsage sigUsage) {
		var op = fileCreation.getFileCreate();

		var bytesUsed = bytesInBaseRepr();
		bytesUsed += op.getMemoBytes().size();
		bytesUsed += keySizeIfPresent(
				op,
				FileCreateTransactionBody::hasKeys,
				body -> Key.newBuilder().setKeyList(body.getKeys()).build());
		var lifetime = ESTIMATOR_UTILS.relativeLifetime(fileCreation, op.getExpirationTime().getSeconds());

		var estimate = estimateFactory.get(sigUsage, fileCreation, ESTIMATOR_UTILS);
		estimate.addBpt(bytesUsed);
		estimate.addRbs(bytesUsed * lifetime);
		estimate.addNetworkRbs(BASIC_ENTITY_ID_SIZE * USAGE_PROPERTIES.legacyReceiptStorageSecs());

		return estimate.get();
	}
}
