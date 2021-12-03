package com.hedera.services.txns.crypto;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import javax.inject.Inject;

import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_INITIAL_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RECEIVE_RECORD_THRESHOLD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SEND_RECORD_THRESHOLD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.KEY_REQUIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;

public class CreateLogic {
	private final OptionValidator validator;
	private final GlobalDynamicProperties dynamicProperties;
	@Inject
	public CreateLogic(OptionValidator validator, GlobalDynamicProperties dynamicProperties) {
        this.validator = validator;
		this.dynamicProperties = dynamicProperties;
	}

	public AccountID create(HederaLedger ledger,
			final CryptoCreateTransactionBody txnBody,
			final AccountID sponsor,
			final long balance,
			final long consensusTime){
		validate(txnBody);
		return ledger.create(sponsor, balance, asCustomizer(txnBody, consensusTime));
	}

	private HederaAccountCustomizer asCustomizer(final CryptoCreateTransactionBody op, final long consensusTime) {
		long autoRenewPeriod = op.getAutoRenewPeriod().getSeconds();
		long expiry = consensusTime + autoRenewPeriod;

		/* Note that {@code this.validate(TransactionBody)} will have rejected any txn with an invalid key. */
		JKey key = asFcKeyUnchecked(op.getKey());
		HederaAccountCustomizer customizer = new HederaAccountCustomizer()
				.key(key)
				.memo(op.getMemo())
				.expiry(expiry)
				.autoRenewPeriod(autoRenewPeriod)
				.isReceiverSigRequired(op.getReceiverSigRequired())
				.maxAutomaticAssociations(op.getMaxAutomaticTokenAssociations());
		if (op.hasProxyAccountID()) {
			customizer.proxy(EntityId.fromGrpcAccountId(op.getProxyAccountID()));
		}
		return customizer;
	}

	public ResponseCodeEnum validate(CryptoCreateTransactionBody op) {
		var memoValidity = validator.memoCheck(op.getMemo());
		if (memoValidity != OK) {
			return memoValidity;
		}
		if (!op.hasKey()) {
			return KEY_REQUIRED;
		}
		if (!validator.hasGoodEncoding(op.getKey())) {
			return BAD_ENCODING;
		}
		var fcKey = asFcKeyUnchecked(op.getKey());
		if (fcKey.isEmpty()) {
			return KEY_REQUIRED;
		}
		if (!fcKey.isValid()) {
			return BAD_ENCODING;
		}
		if (op.getInitialBalance() < 0L) {
			return INVALID_INITIAL_BALANCE;
		}
		if (!op.hasAutoRenewPeriod()) {
			return INVALID_RENEWAL_PERIOD;
		}
		if (!validator.isValidAutoRenewPeriod(op.getAutoRenewPeriod())) {
			return AUTORENEW_DURATION_NOT_IN_RANGE;
		}
		if (op.getSendRecordThreshold() < 0L) {
			return INVALID_SEND_RECORD_THRESHOLD;
		}
		if (op.getReceiveRecordThreshold() < 0L) {
			return INVALID_RECEIVE_RECORD_THRESHOLD;
		}
		if (op.getMaxAutomaticTokenAssociations() > dynamicProperties.maxTokensPerAccount()) {
			return REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
		}

		return OK;
	}
}
