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
import com.hedera.services.usage.crypto.CryptoApproveAllowanceMeta;
import com.hedera.services.usage.crypto.CryptoCreateMeta;
import com.hedera.services.usage.crypto.CryptoUpdateMeta;
import com.hedera.services.usage.token.meta.FeeScheduleUpdateMeta;
import com.hedera.services.usage.token.meta.TokenBurnMeta;
import com.hedera.services.usage.token.meta.TokenCreateMeta;
import com.hedera.services.usage.token.meta.TokenFreezeMeta;
import com.hedera.services.usage.token.meta.TokenPauseMeta;
import com.hedera.services.usage.token.meta.TokenUnfreezeMeta;
import com.hedera.services.usage.token.meta.TokenUnpauseMeta;
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
	private static final String TOKEN_FREEZE_META_KEY = "tokenFreezeMeta";
	private static final String TOKEN_UNFREEZE_META_KEY = "tokenUnfreezeMeta";
	private static final String TOKEN_PAUSE_META_KEY = "tokenPauseMeta";
	private static final String TOKEN_UNPAUSE_META_KEY = "tokenUnpauseMeta";
	private static final String CRYPTO_CREATE_META_KEY = "cryptoCreateMeta";
	private static final String CRYPTO_UPDATE_META_KEY = "cryptoUpdateMeta";
	private static final String CRYPTO_APPROVE_META_KEY = "cryptoApproveMeta";

	@Inject
	public ExpandHandleSpanMapAccessor() {
		// Default constructor
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

	public void setTokenFreezeMeta(TxnAccessor accessor, TokenFreezeMeta tokenFreezeMeta) {
		accessor.getSpanMap().put(TOKEN_FREEZE_META_KEY, tokenFreezeMeta);
	}

	public TokenFreezeMeta getTokenFreezeMeta(TxnAccessor accessor) {
		return (TokenFreezeMeta) accessor.getSpanMap().get(TOKEN_FREEZE_META_KEY);
	}

	public void setTokenUnfreezeMeta(TxnAccessor accessor, TokenUnfreezeMeta tokenUnfreezeMeta) {
		accessor.getSpanMap().put(TOKEN_UNFREEZE_META_KEY, tokenUnfreezeMeta);
	}

	public TokenUnfreezeMeta getTokenUnfreezeMeta(TxnAccessor accessor) {
		return (TokenUnfreezeMeta) accessor.getSpanMap().get(TOKEN_UNFREEZE_META_KEY);
	}

	public void setTokenPauseMeta(TxnAccessor accessor, TokenPauseMeta tokenPauseMeta) {
		accessor.getSpanMap().put(TOKEN_PAUSE_META_KEY, tokenPauseMeta);
	}

	public TokenPauseMeta getTokenPauseMeta(TxnAccessor accessor) {
		return (TokenPauseMeta) accessor.getSpanMap().get(TOKEN_PAUSE_META_KEY);
	}

	public void setTokenUnpauseMeta(TxnAccessor accessor, TokenUnpauseMeta tokenUnpauseMeta) {
		accessor.getSpanMap().put(TOKEN_UNPAUSE_META_KEY, tokenUnpauseMeta);
	}

	public TokenUnpauseMeta getTokenUnpauseMeta(TxnAccessor accessor) {
		return (TokenUnpauseMeta) accessor.getSpanMap().get(TOKEN_UNPAUSE_META_KEY);
	}

	public void setCryptoCreateMeta(TxnAccessor accessor, CryptoCreateMeta cryptoCreateMeta) {
		accessor.getSpanMap().put(CRYPTO_CREATE_META_KEY, cryptoCreateMeta);
	}

	public CryptoCreateMeta getCryptoCreateMeta(TxnAccessor accessor) {
		return (CryptoCreateMeta) accessor.getSpanMap().get(CRYPTO_CREATE_META_KEY);
	}

	public void setCryptoUpdate(TxnAccessor accessor, CryptoUpdateMeta cryptoUpdateMeta) {
		accessor.getSpanMap().put(CRYPTO_UPDATE_META_KEY, cryptoUpdateMeta);
	}

	public CryptoUpdateMeta getCryptoUpdateMeta(TxnAccessor accessor) {
		return (CryptoUpdateMeta) accessor.getSpanMap().get(CRYPTO_UPDATE_META_KEY);
	}

	public void setCryptoApproveMeta(TxnAccessor accessor, CryptoApproveAllowanceMeta cryptoApproveMeta) {
		accessor.getSpanMap().put(CRYPTO_APPROVE_META_KEY, cryptoApproveMeta);
	}

	public CryptoApproveAllowanceMeta getCryptoApproveMeta(TxnAccessor accessor) {
		return (CryptoApproveAllowanceMeta) accessor.getSpanMap().get(CRYPTO_APPROVE_META_KEY);
	}
}
