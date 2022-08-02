package com.hedera.services.utils.accessors.custom;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.NodeInfo;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.usage.crypto.CryptoCreateMeta;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.swirlds.merkle.map.MerkleMap;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

import static com.hedera.services.ledger.accounts.HederaAccountCustomizer.hasStakedId;
import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_INITIAL_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RECEIVE_RECORD_THRESHOLD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SEND_RECORD_THRESHOLD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_STAKING_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.KEY_REQUIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.STAKING_NOT_ENABLED;

public class CryptoCreateAccessor extends SignedTxnAccessor {
    private final CryptoCreateTransactionBody body;
    private final GlobalDynamicProperties properties;
    private final OptionValidator validator;
    private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;
    private final NodeInfo nodeInfo;

    public CryptoCreateAccessor(
            final byte[] signedTxnWrapperBytes,
            @Nullable final Transaction transaction,
            final GlobalDynamicProperties properties,
            final OptionValidator validator,
            final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts,
            final NodeInfo nodeInfo)
            throws InvalidProtocolBufferException {
        super(signedTxnWrapperBytes, transaction);
        this.body = getTxn().getCryptoCreateAccount();
        this.properties = properties;
        this.validator = validator;
        this.accounts = accounts;
        this.nodeInfo = nodeInfo;
		setCryptoCreateUsageMeta();
    }

	public long initialBalance(){
		return body.getInitialBalance();
	}

	public Duration autoRenewPeriod(){
		return body.getAutoRenewPeriod();
	}

	public Key key(){
		return body.getKey();
	}

	public String memo(){
		return body.getMemo();
	}
	public boolean receiverSigReq(){
		return body.getReceiverSigRequired();
	}

	public int maxTokenAssociations(){
		return body.getMaxAutomaticTokenAssociations();
	}

	public boolean declineReward(){
		return body.getDeclineReward();
	}

	public CryptoCreateTransactionBody.StakedIdCase stakedIdCase(){
		return body.getStakedIdCase();
	}

	public AccountID stakedAccountId(){
		return body.getStakedAccountId();
	}

	public long stakedNodeId(){
		return body.getStakedNodeId();
	}

    @Override
    public boolean supportsPrecheck() {
        return true;
    }

    @Override
    public ResponseCodeEnum doPrecheck() {
        return validateSyntax();
    }

    public ResponseCodeEnum validateSyntax() {
        var memoValidity = validator.memoCheck(body.getMemo());
        if (memoValidity != OK) {
            return memoValidity;
        }
        if (!body.hasKey()) {
            return KEY_REQUIRED;
        }
        if (!validator.hasGoodEncoding(body.getKey())) {
            return BAD_ENCODING;
        }
        var fcKey = asFcKeyUnchecked(body.getKey());
        if (fcKey.isEmpty()) {
            return KEY_REQUIRED;
        }
        if (!fcKey.isValid()) {
            return INVALID_ADMIN_KEY;
        }
        if (body.getInitialBalance() < 0L) {
            return INVALID_INITIAL_BALANCE;
        }
        if (!body.hasAutoRenewPeriod()) {
            return INVALID_RENEWAL_PERIOD;
        }
        if (!validator.isValidAutoRenewPeriod(body.getAutoRenewPeriod())) {
            return AUTORENEW_DURATION_NOT_IN_RANGE;
        }
        if (body.getSendRecordThreshold() < 0L) {
            return INVALID_SEND_RECORD_THRESHOLD;
        }
        if (body.getReceiveRecordThreshold() < 0L) {
            return INVALID_RECEIVE_RECORD_THRESHOLD;
        }
        if (properties.areTokenAssociationsLimited()
                && body.getMaxAutomaticTokenAssociations() > properties.maxTokensPerAccount()) {
            return REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
        }
        if (body.hasProxyAccountID()
                && !body.getProxyAccountID().equals(AccountID.getDefaultInstance())) {
            return PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED;
        }
        final var stakedIdCase = body.getStakedIdCase().name();
        final var electsStakingId = hasStakedId(stakedIdCase);
        if (!properties.isStakingEnabled() && (electsStakingId || body.getDeclineReward())) {
            return STAKING_NOT_ENABLED;
        }
        if (electsStakingId
                && !validator.isValidStakedId(
                        stakedIdCase,
                        body.getStakedAccountId(),
                        body.getStakedNodeId(),
                        accounts.get(),
                        nodeInfo)) {
            return INVALID_STAKING_ID;
        }
        return OK;
    }

	private void setCryptoCreateUsageMeta() {
		final var cryptoCreateMeta = new CryptoCreateMeta(body);
		getSpanMapAccessor().setCryptoCreateMeta(this, cryptoCreateMeta);
	}
}
