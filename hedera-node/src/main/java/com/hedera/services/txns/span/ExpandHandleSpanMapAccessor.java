package com.hedera.services.txns.span;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hedera.services.grpc.marshalling.ImpliedTransfers;
import com.hedera.services.usage.crypto.CryptoCreateMeta;
import com.hedera.services.usage.crypto.CryptoUpdateMeta;
import com.hedera.services.usage.token.meta.FeeScheduleUpdateMeta;
import com.hedera.services.usage.token.meta.TokenBurnMeta;
import com.hedera.services.usage.token.meta.TokenCreateMeta;
import com.hedera.services.usage.token.meta.TokenDeleteMeta;
import com.hedera.services.usage.token.meta.TokenGrantKycMeta;
import com.hedera.services.usage.token.meta.TokenRevokeKycMeta;
import com.hedera.services.usage.token.meta.TokenUpdateMeta;
import com.hedera.services.usage.token.meta.TokenWipeMeta;
import com.hedera.services.utils.TxnAccessor;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Minimal helper class for getting/setting entries in a span map.
 */
@Singleton
public class ExpandHandleSpanMapAccessor {
	private static final String IMPLIED_TRANSFERS_KEY = "impliedTransfers";
	private static final String FEE_SCHEDULE_UPDATE_META_KEY = "feeScheduleUpdateMeta";
	private static final String TOKEN_CREATE_META_KEY = "tokenCreateMeta";
	private static final String TOKEN_BURN_META_KEY = "tokenBurnMeta";
	private static final String TOKEN_WIPE_META_KEY = "tokenWipeMeta";
	private static final String TOKEN_UPDATE_META_KEY = "tokenUpdateMeta";
	private static final String TOKEN_DELETE_META_KEY = "tokenDeleteMeta";
	private static final String TOKEN_GRANT_KYC_META_KEY = "tokenGrantKycMeta";
	private static final String TOKEN_REVOKE_KYC_META_KEY = "tokenRevokeKycMeta";
	private static final String CRYPTO_CREATE_META_KEY = "cryptoCreateMeta";
	private static final String CRYPTO_UPDATE_META_KEY = "cryptoUpdateMeta";

	@Inject
	public ExpandHandleSpanMapAccessor() {
	}

	public void setFeeScheduleUpdateMeta(TxnAccessor accessor, FeeScheduleUpdateMeta feeScheduleUpdateMeta) {
		accessor.getSpanMap().put(FEE_SCHEDULE_UPDATE_META_KEY, feeScheduleUpdateMeta);
	}

	public FeeScheduleUpdateMeta getFeeScheduleUpdateMeta(TxnAccessor accessor) {
		return (FeeScheduleUpdateMeta) accessor.getSpanMap().get(FEE_SCHEDULE_UPDATE_META_KEY);
	}

	public void setImpliedTransfers(TxnAccessor accessor, ImpliedTransfers impliedTransfers) {
		accessor.getSpanMap().put(IMPLIED_TRANSFERS_KEY, impliedTransfers);
	}

	public ImpliedTransfers getImpliedTransfers(TxnAccessor accessor) {
		return (ImpliedTransfers) accessor.getSpanMap().get(IMPLIED_TRANSFERS_KEY);
	}

	public void setTokenCreateMeta(TxnAccessor accessor, TokenCreateMeta tokenCreateMeta) {
		accessor.getSpanMap().put(TOKEN_CREATE_META_KEY, tokenCreateMeta);
	}

	public TokenCreateMeta getTokenCreateMeta(TxnAccessor accessor) {
		return (TokenCreateMeta) accessor.getSpanMap().get(TOKEN_CREATE_META_KEY);
	}

	public void setTokenBurnMeta(TxnAccessor accessor, TokenBurnMeta tokenBurnMeta) {
		accessor.getSpanMap().put(TOKEN_BURN_META_KEY, tokenBurnMeta);
	}

	public TokenBurnMeta getTokenBurnMeta(TxnAccessor accessor) {
		return (TokenBurnMeta) accessor.getSpanMap().get(TOKEN_BURN_META_KEY);
	}

	public void setTokenWipeMeta(TxnAccessor accessor, TokenWipeMeta tokenWipeMeta) {
		accessor.getSpanMap().put(TOKEN_WIPE_META_KEY, tokenWipeMeta);
	}

	public TokenWipeMeta getTokenWipeMeta(TxnAccessor accessor) {
		return (TokenWipeMeta) accessor.getSpanMap().get(TOKEN_WIPE_META_KEY);
	}

	public void setCryptoCreateMeta(TxnAccessor accessor, CryptoCreateMeta cryptoCreateMeta) {
		accessor.getSpanMap().put(CRYPTO_CREATE_META_KEY, cryptoCreateMeta);
	}

	public CryptoCreateMeta getCryptoCreateMeta(TxnAccessor accessor) {
		return (CryptoCreateMeta) accessor.getSpanMap().get(CRYPTO_CREATE_META_KEY);
	}

	public void setTokenUpdateMeta(TxnAccessor accessor, TokenUpdateMeta tokenUpdateMeta) {
		accessor.getSpanMap().put(TOKEN_UPDATE_META_KEY, tokenUpdateMeta);
	}

	public TokenUpdateMeta getTokenUpdateMeta(TxnAccessor accessor) {
		return (TokenUpdateMeta) accessor.getSpanMap().get(TOKEN_UPDATE_META_KEY);
	}

	public void setTokenDeleteMeta(TxnAccessor accessor, TokenDeleteMeta tokenDeleteMeta) {
		accessor.getSpanMap().put(TOKEN_DELETE_META_KEY, tokenDeleteMeta);
	}

	public TokenDeleteMeta getTokenDeleteMeta(TxnAccessor accessor) {
		return (TokenDeleteMeta) accessor.getSpanMap().get(TOKEN_DELETE_META_KEY);
	}

	public void setTokenGrantKycMeta(TxnAccessor accessor, TokenGrantKycMeta tokenGrantKycMeta) {
		accessor.getSpanMap().put(TOKEN_GRANT_KYC_META_KEY, tokenGrantKycMeta);
	}
	public TokenGrantKycMeta getTokenGrantKycMeta(TxnAccessor accessor) {
		return (TokenGrantKycMeta) accessor.getSpanMap().get(TOKEN_GRANT_KYC_META_KEY);
	}

	public void setTokenRevokeKycMeta(TxnAccessor accessor, TokenRevokeKycMeta tokenRevokeKycMeta) {
		accessor.getSpanMap().put(TOKEN_REVOKE_KYC_META_KEY, tokenRevokeKycMeta);
	}
	public TokenRevokeKycMeta getTokenRevokeKycMeta(TxnAccessor accessor) {
		return (TokenRevokeKycMeta) accessor.getSpanMap().get(TOKEN_REVOKE_KYC_META_KEY);
	}

	public void setCryptoUpdate(TxnAccessor accessor, CryptoUpdateMeta cryptoUpdateMeta) {
		accessor.getSpanMap().put(CRYPTO_UPDATE_META_KEY, cryptoUpdateMeta);
	}

	public CryptoUpdateMeta getCryptoUpdateMeta(TxnAccessor accessor) {
		return (CryptoUpdateMeta) accessor.getSpanMap().get(CRYPTO_UPDATE_META_KEY);
	}
}
