package com.hedera.services.txns.token;

import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class DeleteLogic {
	private final AccountStore accountStore;
	private final TypedTokenStore tokenStore;
	private final SigImpactHistorian sigImpactHistorian;
	@Inject
	public DeleteLogic(
			final AccountStore accountStore,
			final TypedTokenStore tokenStore,
			final SigImpactHistorian sigImpactHistorian
	) {
		this.tokenStore = tokenStore;
		this.accountStore = accountStore;
		this.sigImpactHistorian = sigImpactHistorian;
	}

	public void delete(TokenID grpcTokenId){
		// --- Convert to model id ---
		final var targetTokenId = Id.fromGrpcToken(grpcTokenId);
		// --- Load the model object ---
		final var loadedToken = tokenStore.loadToken(targetTokenId);

		// --- Do the business logic ---
		loadedToken.delete();

		// --- Persist the updated model ---
		tokenStore.commitToken(loadedToken);
		accountStore.commitAccount(loadedToken.getTreasury());
		sigImpactHistorian.markEntityChanged(grpcTokenId.getTokenNum());
	}

	public ResponseCodeEnum validate(final TransactionBody txnBody) {
		final TokenDeleteTransactionBody op = txnBody.getTokenDeletion();

		if (!op.hasToken()) {
			return INVALID_TOKEN_ID;
		}

		return OK;
	}
}
