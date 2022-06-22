package com.hedera.services.grpc.controllers;

import com.hedera.services.txns.submission.TxnResponseHelper;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.service.proto.java.UtilServiceGrpc;
import io.grpc.stub.StreamObserver;

import javax.inject.Inject;
import javax.inject.Singleton;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.RandomGenerate;
@Singleton
public class UtilController extends UtilServiceGrpc.UtilServiceImplBase {
	public static final String RANDOM_GENERATE_METRIC = "randomGenerate";
	private final TxnResponseHelper txnHelper;

	@Inject
	public UtilController(TxnResponseHelper txnHelper) {
		this.txnHelper = txnHelper;
	}

	@Override
	public void randomGenerate(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
		txnHelper.submit(signedTxn, observer, RandomGenerate);
	}
}
