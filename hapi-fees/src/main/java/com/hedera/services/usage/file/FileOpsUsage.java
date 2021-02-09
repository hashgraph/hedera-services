package com.hedera.services.usage.file;

import com.hedera.services.usage.QueryUsage;
import com.hedera.services.usage.EstimatorFactory;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TransactionBody;

import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hedera.services.usage.SingletonUsageProperties.USAGE_PROPERTIES;
import static com.hedera.services.usage.TxnUsage.keySizeIfPresent;
import static com.hederahashgraph.fee.FeeBuilder.BASE_FILEINFO_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BOOL_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.LONG_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.getAccountKeyStorageSize;

public class FileOpsUsage {
	static EstimatorFactory txnEstimateFactory = TxnUsageEstimator::new;
	static Function<ResponseType, QueryUsage> queryEstimateFactory = QueryUsage::new;

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

		int customBytes = 0;
		customBytes += op.getContents().size();
		customBytes += op.getMemoBytes().size();
		customBytes += keySizeIfPresent(op, FileCreateTransactionBody::hasKeys, body -> asKey(body.getKeys()));

		var lifetime = ESTIMATOR_UTILS.relativeLifetime(fileCreation, op.getExpirationTime().getSeconds());

		var estimate = txnEstimateFactory.get(sigUsage, fileCreation, ESTIMATOR_UTILS);
		/* Variable bytes plus a long for expiration time */
		estimate.addBpt(customBytes + LONG_SIZE);
		estimate.addSbs((bytesInBaseRepr() + customBytes) * lifetime);
		estimate.addNetworkRbs(BASIC_ENTITY_ID_SIZE * USAGE_PROPERTIES.legacyReceiptStorageSecs());

		return estimate.get();
	}

	public FeeData fileInfoUsage(Query fileInfoReq, ExtantFileContext ctx) {
		var op = fileInfoReq.getFileGetInfo();

		var estimate = queryEstimateFactory.apply(op.getHeader().getResponseType());
		estimate.updateTb(BASIC_ENTITY_ID_SIZE);
		long extraSb = 0;
		extraSb += ctx.currentMemo().getBytes(StandardCharsets.UTF_8).length;
		extraSb += getAccountKeyStorageSize(asKey(ctx.currentWacl()));
		estimate.updateSb(BASE_FILEINFO_SIZE + extraSb);

		return estimate.get();
	}

	public FeeData fileUpdateUsage(TransactionBody fileUpdate, SigUsage sigUsage, ExtantFileContext ctx) {
		var op = fileUpdate.getFileUpdate();

		long keyBytesUsed = op.hasKeys() ? getAccountKeyStorageSize(asKey(op.getKeys())) : 0;
		long msgBytesUsed = BASIC_ENTITY_ID_SIZE
				+ op.getContents().size()
				+ op.getMemo().getValueBytes().size()
				+ keyBytesUsed
				+ (op.hasExpirationTime() ? LONG_SIZE : 0);
		var estimate = txnEstimateFactory.get(sigUsage, fileUpdate, ESTIMATOR_UTILS);
		estimate.addBpt(msgBytesUsed);

		long newCustomBytes = 0;
		newCustomBytes += op.getContents().isEmpty()
				? ctx.currentSize()
				: op.getContents().size();
		newCustomBytes += !op.hasMemo()
				? ctx.currentMemo().getBytes(StandardCharsets.UTF_8).length
				: op.getMemo().getValueBytes().size();
		newCustomBytes += !op.hasKeys()
				? getAccountKeyStorageSize(asKey(ctx.currentWacl()))
				: keyBytesUsed;
		long oldCustomBytes = ctx.currentNonBaseSb();
		long oldLifetime = ESTIMATOR_UTILS.relativeLifetime(fileUpdate, ctx.currentExpiry());
		long newLifetime = ESTIMATOR_UTILS.relativeLifetime(fileUpdate, op.getExpirationTime().getSeconds());
		long sbsDelta = ESTIMATOR_UTILS.changeInBsUsage(oldCustomBytes, oldLifetime, newCustomBytes, newLifetime);
		if (sbsDelta > 0) {
			estimate.addSbs(sbsDelta);
		}

		return estimate.get();
	}

	public static Key asKey(KeyList wacl) {
		return Key.newBuilder().setKeyList(wacl).build();
	}
}
