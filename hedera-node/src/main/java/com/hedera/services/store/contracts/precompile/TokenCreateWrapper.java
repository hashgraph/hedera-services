package com.hedera.services.store.contracts.precompile;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.core.jproto.JECDSASecp256k1Key;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.Fraction;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.commons.codec.DecoderException;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

final class TokenCreateWrapper {
	private final boolean isFungible;
	private final String name;
	private final String symbol;
	private final AccountID treasury;
	private final boolean isSupplyTypeFinite;
	private final BigInteger initSupply;
	private final BigInteger decimals;
	private final long maxSupply;
	private final String memo;
	private final boolean isFreezeDefault;
	private final List<TokenKeyWrapper> tokenKeys;
	private final TokenExpiryWrapper expiry;
	private List<FixedFeeWrapper> fixedFees;
	private List<FractionalFeeWrapper> fractionalFees;
	private List<RoyaltyFeeWrapper> royaltyFees;

	TokenCreateWrapper(
			final boolean isFungible,
			final String tokenName,
			final String tokenSymbol,
			final AccountID tokenTreasury,
			final String memo,
			final Boolean isSupplyTypeFinite,
			final BigInteger initSupply,
			final BigInteger decimals,
			final long maxSupply,
			final Boolean isFreezeDefault,
			final List<TokenKeyWrapper> tokenKeys,
			final TokenExpiryWrapper tokenExpiry
	) {
		this.isFungible = isFungible;
		this.name = tokenName;
		this.symbol = tokenSymbol;
		this.treasury = tokenTreasury;
		this.memo = memo;
		this.isSupplyTypeFinite = isSupplyTypeFinite;
		this.initSupply = initSupply;
		this.decimals = decimals;
		this.maxSupply = maxSupply;
		this.isFreezeDefault = isFreezeDefault;
		this.tokenKeys = tokenKeys;
		this.expiry = tokenExpiry;
		this.fixedFees = List.of();
		this.fractionalFees = List.of();
		this.royaltyFees = List.of();
	}

	boolean isFungible() {
		return isFungible;
	}

	String getName() {
		return name;
	}

	String getSymbol() {
		return symbol;
	}

	AccountID getTreasury() {
		return treasury;
	}

	boolean isSupplyTypeFinite() {
		return isSupplyTypeFinite;
	}

	BigInteger getInitSupply() {
		return initSupply;
	}

	BigInteger getDecimals() {
		return decimals;
	}

	long getMaxSupply() {
		return maxSupply;
	}

	String getMemo() {
		return memo;
	}

	boolean isFreezeDefault() {
		return isFreezeDefault;
	}

	List<TokenKeyWrapper> getTokenKeys() {
		return tokenKeys;
	}

	TokenExpiryWrapper getExpiry() {
		return expiry;
	}

	List<FixedFeeWrapper> getFixedFees() {
		return fixedFees;
	}

	List<FractionalFeeWrapper> getFractionalFees() {
		return fractionalFees;
	}

	List<RoyaltyFeeWrapper> getRoyaltyFees() {
		return royaltyFees;
	}

	void setFixedFees(final List<FixedFeeWrapper> fixedFees) {
		this.fixedFees = fixedFees;
	}

	void setFractionalFees(final List<FractionalFeeWrapper> fractionalFees) {
		this.fractionalFees = fractionalFees;
	}

	void setRoyaltyFees(final List<RoyaltyFeeWrapper> royaltyFees) {
		this.royaltyFees = royaltyFees;
	}

	void setAllInheritedKeysTo(final JKey senderKey) throws DecoderException {
		for (final var tokenKey: tokenKeys) {
			if (tokenKey.key.isShouldInheritAccountKeySet()) {
				tokenKey.key.setInheritedKey(JKey.mapJKey(senderKey));
			}
		}
	}

	public Optional<TokenKeyWrapper> getAdminKey() {
		return tokenKeys.stream()
				.filter(TokenKeyWrapper::isUsedForAdminKey)
				.findFirst();
	}

	/* ------------------ */

	record TokenKeyWrapper(BigInteger keyType, KeyValueWrapper key) {
		boolean isUsedForAdminKey() {
			return (keyType().intValue() & 1) != 0;
		}

		boolean isUsedForKycKey() {
			return (keyType().intValue() & 2) != 0;
		}

		boolean isUsedForFreezeKey() {
			return (keyType().intValue() & 4) != 0;
		}

		boolean isUsedForWipeKey() {
			return (keyType().intValue() & 8) != 0;
		}

		boolean isUsedForSupplyKey() {
			return (keyType().intValue() & 16) != 0;
		}

		boolean isUsedForFeeScheduleKey() {
			return (keyType().intValue() & 32) != 0;
		}

		boolean isUsedForPauseKey() {
			return (keyType().intValue() & 64) != 0;
		}
	}

	static final class KeyValueWrapper {
		enum KeyValueType {
			INVALID_KEY,
			INHERIT_ACCOUNT_KEY,
			CONTRACT_ID,
			DELEGATABLE_CONTRACT_ID,
			ED25519,
			ECDS_SECPK256K1
		}

		/* ---  Only 1 of these values should be set when the input is valid. --- */
		private final boolean shouldInheritAccountKey;
		private final ContractID contractID;
		private final byte[] ed25519;
		private final byte[] ecdsSecp256k1;
		private final ContractID delegatableContractID;

		/* --- This field is populated only when `shouldInheritAccountKey` is true --- */
		private Key inheritedKey;
		
		private KeyValueType keyValueType;

		public KeyValueWrapper(
				final boolean shouldInheritAccountKey,
				final ContractID contractID,
				final byte[] ed25519,
		   		final byte[] ecdsSecp256k1,
				final ContractID delegatableContractID
		) {
			this.shouldInheritAccountKey = shouldInheritAccountKey;
			this.contractID = contractID;
			this.ed25519 = ed25519;
			this.ecdsSecp256k1 = ecdsSecp256k1;
			this.delegatableContractID = delegatableContractID;
			this.setKeyValueType();
		}

		private boolean isContractIDSet() {
			return contractID != null;
		}

		private boolean isDelegatableContractIdSet() {
			return delegatableContractID != null;
		}

		private boolean isShouldInheritAccountKeySet() {
			return shouldInheritAccountKey;
		}

		private boolean isEd25519KeySet() {
			return ed25519.length == JEd25519Key.ED25519_BYTE_LENGTH;
		}

		private boolean isEcdsSecp256k1KeySet() {
			return ecdsSecp256k1.length == JECDSASecp256k1Key.ECDSASECP256_COMPRESSED_BYTE_LENGTH;
		}

		private void setInheritedKey(final Key key) {
			this.inheritedKey = key;
		}

		private void setKeyValueType() {
			if (isShouldInheritAccountKeySet() && !isEcdsSecp256k1KeySet() && !isDelegatableContractIdSet()
					&& !isContractIDSet() && !isEd25519KeySet()) {
				this.keyValueType = KeyValueType.INHERIT_ACCOUNT_KEY;
			} else if (isContractIDSet() && !isEcdsSecp256k1KeySet() && !isDelegatableContractIdSet()
					&& !isEd25519KeySet()) {
				this.keyValueType = KeyValueType.CONTRACT_ID;
			} else if (isEd25519KeySet() && !isEcdsSecp256k1KeySet() && !isDelegatableContractIdSet()) {
				this.keyValueType = KeyValueType.ED25519;
			} else if (isEcdsSecp256k1KeySet() && !isDelegatableContractIdSet() ) {
				this.keyValueType = KeyValueType.ECDS_SECPK256K1;
			} else if (isDelegatableContractIdSet()) {
				this.keyValueType = KeyValueType.DELEGATABLE_CONTRACT_ID;
			} else {
				this.keyValueType = KeyValueType.INVALID_KEY;
			}
		}

		KeyValueType getKeyValueType() {
			return this.keyValueType;
		}

		ContractID getContractID() {
			return this.contractID;
		}

		ContractID getDelegatableContractID() {
			return this.delegatableContractID;
		}

		byte[] getEd25519Key() {
			return this.ed25519;
		}

		byte[] getEcdsSecp256k1() {
			return this.ecdsSecp256k1;
		}

		Key asGrpc() {
			if (shouldInheritAccountKey) {
				return this.inheritedKey;
			} else if (contractID != null) {
				return Key.newBuilder().setContractID(contractID).build();
			} else if (ed25519.length == JEd25519Key.ED25519_BYTE_LENGTH) {
				return Key.newBuilder().setEd25519(ByteString.copyFrom(ed25519)).build();
			} else if (ecdsSecp256k1.length == JECDSASecp256k1Key.ECDSASECP256_COMPRESSED_BYTE_LENGTH) {
				return Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom(ecdsSecp256k1)).build();
			} else if (delegatableContractID != null) {
				return Key.newBuilder().setContractID((delegatableContractID)).build();
			} else {
				return Key.newBuilder().build();
			}
		}
	}

	record TokenExpiryWrapper(long second, AccountID autoRenewAccount, long autoRenewPeriod) { }

	record FixedFeeWrapper(
			long amount,
			TokenID tokenID,
			boolean useHbarsForPayment,
			boolean useCurrentTokenForPayment,
			AccountID feeCollector
	) {

		private FixedFee.Builder asBuilder() {
			final var fixedFeeBuilder = FixedFee.newBuilder().setAmount(amount);
			if (isTokenIdSet()) {
				fixedFeeBuilder.setDenominatingTokenId(tokenID);
			} else if (useCurrentTokenForPayment) {
				fixedFeeBuilder.setDenominatingTokenId(TokenID.newBuilder()
						.setShardNum(0L)
						.setRealmNum(0L)
						.setTokenNum(0L).build());
			}
			return fixedFeeBuilder;
		}

		boolean isTokenIdSet() {
			return tokenID.getTokenNum() != 0;
		}

		CustomFee asGrpc() {
			return CustomFee.newBuilder()
				.setFixedFee(asBuilder().build())
				.setFeeCollectorAccountId(feeCollector)
				.build();
		}
	}

	record FractionalFeeWrapper(
			long numerator,
			long denominator,
			long minimumAmount,
			long maximumAmount,
			boolean netOfTransfers,
			AccountID feeCollector
	) {

		CustomFee asGrpc() {
			return CustomFee.newBuilder()
				.setFractionalFee(com.hederahashgraph.api.proto.java.FractionalFee.newBuilder()
					.setFractionalAmount(Fraction.newBuilder()
						.setNumerator(numerator)
						.setDenominator(denominator)
						.build()
					)
					.setMinimumAmount(minimumAmount)
					.setMaximumAmount(maximumAmount)
					.setNetOfTransfers(netOfTransfers)
					.build()
				)
				.setFeeCollectorAccountId(feeCollector)
				.build();
		}
	}

	record RoyaltyFeeWrapper(
			long numerator, long denominator, FixedFeeWrapper fallbackFixedFee, AccountID feeCollector
	) {
		CustomFee asGrpc() {
			return CustomFee.newBuilder()
				.setRoyaltyFee(com.hederahashgraph.api.proto.java.RoyaltyFee.newBuilder()
					.setExchangeValueFraction(Fraction.newBuilder()
						.setNumerator(numerator)
						.setDenominator(denominator)
						.build()
					)
					.setFallbackFee(fallbackFixedFee.asBuilder().build())
					.build()
				)
				.setFeeCollectorAccountId(feeCollector)
				.build();
		}
	}
}