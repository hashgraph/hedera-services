package com.hedera.services.utils;


import com.google.protobuf.ByteString;
import com.google.protobuf.TextFormat;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

public final class LogUtils {
	private LogUtils() {
		throw new UnsupportedOperationException("Utility Class");
	}

	// one with txnBody
	// one with txn
	public static void encodeGrpcAndLog(Logger logger, Level logLevel, String message, Transaction txn) {
		encodeGrpcAndLog(logger, logLevel, message, txn, null);
	}

	public static void encodeGrpcAndLog(Logger logger, Level logLevel, String message, Transaction txn, Exception exception) {
		var loggableGrpc = TextFormat.escapeBytes(txn.getSignedTransactionBytes());
		logger.log(logLevel, String.format(message, loggableGrpc), exception);
	}

	public static void encodeGrpcAndLog(Logger logger, Level logLevel, String message, Query query) {
		encodeGrpcAndLog(logger, logLevel, message, query, null);
	}

	public static void encodeGrpcAndLog(Logger logger, Level logLevel, String message, Query query, Exception exception) {
		var loggableGrpc = TextFormat.escapeBytes(query.toByteString());
		logger.log(logLevel, String.format(message, loggableGrpc), exception);
	}

	public static void encodeGrpcAndLog(Logger logger, Level logLevel, String message, TransactionBody txnBody) {
		encodeGrpcAndLog(logger, logLevel, message, txnBody, null);
	}

	public static void encodeGrpcAndLog(Logger logger, Level logLevel, String message, TransactionBody txnBody,
			Exception exception) {
		var loggableGrpc = TextFormat.escapeBytes(txnBody.toByteString());
		logger.log(logLevel, String.format(message, loggableGrpc), exception);
	}

	public static void encodeGrpcAndLog(Logger logger, Level logLevel, String message, String grpc) {
		var loggableGrpc = TextFormat.escapeBytes(ByteString.copyFromUtf8(grpc));
		logger.log(logLevel, String.format(message, loggableGrpc));
	}

	public static void encodeGrpcAndLog(Logger logger, Level logLevel, String message) {
		var loggableGrpc = TextFormat.escapeBytes(ByteString.copyFromUtf8(message));
		logger.log(logLevel, loggableGrpc);
	}
}
