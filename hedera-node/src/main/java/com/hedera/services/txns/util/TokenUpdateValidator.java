package com.hedera.services.txns.util;

import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;

import static com.hedera.services.txns.validation.TokenListChecks.checkKeys;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class TokenUpdateValidator {
	public static ResponseCodeEnum validate(TransactionBody txnBody, OptionValidator validator) {
		TokenUpdateTransactionBody op = txnBody.getTokenUpdate();

		if (!op.hasToken()) {
			return INVALID_TOKEN_ID;
		}

		var validity = !op.hasMemo() ? OK : validator.memoCheck(op.getMemo().getValue());
		if (validity != OK) {
			return validity;
		}

		var hasNewSymbol = op.getSymbol().length() > 0;
		if (hasNewSymbol) {
			validity = validator.tokenSymbolCheck(op.getSymbol());
			if (validity != OK) {
				return validity;
			}
		}

		var hasNewTokenName = op.getName().length() > 0;
		if (hasNewTokenName) {
			validity = validator.tokenNameCheck(op.getName());
			if (validity != OK) {
				return validity;
			}
		}

		validity =
				checkKeys(
						op.hasAdminKey(), op.getAdminKey(),
						op.hasKycKey(), op.getKycKey(),
						op.hasWipeKey(), op.getWipeKey(),
						op.hasSupplyKey(), op.getSupplyKey(),
						op.hasFreezeKey(), op.getFreezeKey(),
						op.hasFeeScheduleKey(), op.getFeeScheduleKey(),
						op.hasPauseKey(), op.getPauseKey());

		return validity;
	}
}
