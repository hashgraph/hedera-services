package com.hedera.services.grpc.controllers;

import com.hedera.services.txns.submission.TxnResponseHelper;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.service.proto.java.FreezeServiceGrpc;
import io.grpc.stub.StreamObserver;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.Freeze;

public class FreezeController extends FreezeServiceGrpc.FreezeServiceImplBase {
	public static final String FREEZE_METRIC = "freeze";

	private final TxnResponseHelper txnHelper;

	public FreezeController(TxnResponseHelper txnHelper) {
		this.txnHelper = txnHelper;
	}

	@Override
	public void freeze(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
		txnHelper.submit(signedTxn, observer, Freeze);
	}
}
