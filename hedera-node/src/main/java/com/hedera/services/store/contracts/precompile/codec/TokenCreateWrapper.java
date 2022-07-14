package com.hedera.services.store.contracts.precompile.codec;

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

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;

import com.google.protobuf.ByteString;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.legacy.core.jproto.JECDSASecp256k1Key;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.submerkle.EntityId;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.Fraction;
import com.hederahashgraph.api.proto.java.FractionalFee;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.RoyaltyFee;
import com.hederahashgraph.api.proto.java.TokenID;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import org.apache.commons.codec.DecoderException;

public class TokenCreateWrapper {
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

	public TokenCreateWrapper(
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

	public boolean isFungible() {
		return isFungible;
	}

	public String getName() {
		return name;
	}

	public String getSymbol() {
		return symbol;
	}

	public AccountID getTreasury() {
		return treasury;
	}

	public boolean isSupplyTypeFinite() {
		return isSupplyTypeFinite;
	}

	public BigInteger getInitSupply() {
		return initSupply;
	}

	public BigInteger getDecimals() {
		return decimals;
	}

	public long getMaxSupply() {
		return maxSupply;
	}

	public String getMemo() {
		return memo;
	}

	public boolean isFreezeDefault() {
		return isFreezeDefault;
	}

	public List<TokenKeyWrapper> getTokenKeys() {
		return tokenKeys;
	}

	public TokenExpiryWrapper getExpiry() {
		return expiry;
	}

	public List<FixedFeeWrapper> getFixedFees() {
		return fixedFees;
	}

	public List<FractionalFeeWrapper> getFractionalFees() {
		return fractionalFees;
	}

	public List<RoyaltyFeeWrapper> getRoyaltyFees() {
		return royaltyFees;
	}

	public void setFixedFees(final List<FixedFeeWrapper> fixedFees) {
		this.fixedFees = fixedFees;
	}

	public void setFractionalFees(final List<FractionalFeeWrapper> fractionalFees) {
		this.fractionalFees = fractionalFees;
	}

	public void setRoyaltyFees(final List<RoyaltyFeeWrapper> royaltyFees) {
		this.royaltyFees = royaltyFees;
	}

	public void setAllInheritedKeysTo(final JKey senderKey) throws DecoderException {
		for (final var tokenKey : tokenKeys) {
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

	public boolean hasAutoRenewAccount() {
		return expiry.autoRenewAccount() != null && !expiry.autoRenewAccount().equals(AccountID.getDefaultInstance());
	}

	public void inheritAutoRenewAccount(final EntityId parentAutoRenewId) {
		expiry.setAutoRenewAccount(parentAutoRenewId.toGrpcAccountId());
	}

	/* ------------------ */

	public record TokenKeyWrapper(int keyType, KeyValueWrapper key) {
		public boolean isUsedForAdminKey() {
			return (keyType & 1) != 0;
		}

		public boolean isUsedForKycKey() {
			return (keyType & 2) != 0;
		}

		public boolean isUsedForFreezeKey() {
			return (keyType & 4) != 0;
		}

		public boolean isUsedForWipeKey() {
			return (keyType & 8) != 0;
		}

		public boolean isUsedForSupplyKey() {
			return (keyType & 16) != 0;
		}

		public boolean isUsedForFeeScheduleKey() {
			return (keyType & 32) != 0;
		}

		public boolean isUsedForPauseKey() {
			return (keyType & 64) != 0;
		}
	}

	public static final class KeyValueWrapper {
		public enum KeyValueType {
			INVALID_KEY,
			INHERIT_ACCOUNT_KEY,
			CONTRACT_ID,
			DELEGATABLE_CONTRACT_ID,
			ED25519,
			ECDSA_SECPK256K1
		}

		/* ---  Only 1 of these values should be set when the input is valid. --- */
		private final boolean shouldInheritAccountKey;
		private final ContractID contractID;
		private final byte[] ed25519;
		private final byte[] ecdsaSecp256k1;
		private final ContractID delegatableContractID;
		private final KeyValueType keyValueType;

		/* --- This field is populated only when `shouldInheritAccountKey` is true --- */
		private Key inheritedKey;

		public KeyValueWrapper(
				final boolean shouldInheritAccountKey,
				final ContractID contractID,
				final byte[] ed25519,
				final byte[] ecdsaSecp256k1,
				final ContractID delegatableContractID
		) {
			this.shouldInheritAccountKey = shouldInheritAccountKey;
			this.contractID = contractID;
			this.ed25519 = ed25519;
			this.ecdsaSecp256k1 = ecdsaSecp256k1;
			this.delegatableContractID = delegatableContractID;
			this.keyValueType = this.setKeyValueType();
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

		private boolean isEcdsaSecp256k1KeySet() {
			return ecdsaSecp256k1.length == JECDSASecp256k1Key.ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH;
		}

		private void setInheritedKey(final Key key) {
			this.inheritedKey = key;
		}

		private KeyValueType setKeyValueType() {
			if (isShouldInheritAccountKeySet()) {
				return (!isEcdsaSecp256k1KeySet() && !isDelegatableContractIdSet() && !isContractIDSet() && !isEd25519KeySet())
						? KeyValueType.INHERIT_ACCOUNT_KEY
						: KeyValueType.INVALID_KEY;
			} else if (isContractIDSet()) {
				return !isEcdsaSecp256k1KeySet() && !isDelegatableContractIdSet() && !isEd25519KeySet()
						? KeyValueType.CONTRACT_ID
						: KeyValueType.INVALID_KEY;
			} else if (isEd25519KeySet()) {
				return !isEcdsaSecp256k1KeySet() && !isDelegatableContractIdSet()
						? KeyValueType.ED25519
						: KeyValueType.INVALID_KEY;
			} else if (isEcdsaSecp256k1KeySet()) {
				return !isDelegatableContractIdSet()
						? KeyValueType.ECDSA_SECPK256K1
						: KeyValueType.INVALID_KEY;
			} else {
				return isDelegatableContractIdSet()
						? KeyValueType.DELEGATABLE_CONTRACT_ID
						: KeyValueType.INVALID_KEY;
			}
		}

		public KeyValueType getKeyValueType() {
			return this.keyValueType;
		}

		public ContractID getContractID() {
			return this.contractID;
		}

		public ContractID getDelegatableContractID() {
			return this.delegatableContractID;
		}

		public byte[] getEd25519Key() {
			return this.ed25519;
		}

		public byte[] getEcdsaSecp256k1() {
			return this.ecdsaSecp256k1;
		}

		public Key asGrpc() {
			return switch (keyValueType) {
				case INHERIT_ACCOUNT_KEY -> this.inheritedKey;
				case CONTRACT_ID -> Key.newBuilder().setContractID(contractID).build();
				case ED25519 -> Key.newBuilder().setEd25519(ByteString.copyFrom(ed25519)).build();
				case ECDSA_SECPK256K1 -> Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom(ecdsaSecp256k1)).build();
				case DELEGATABLE_CONTRACT_ID -> Key.newBuilder().setDelegatableContractId(delegatableContractID).build();
				default -> throw new InvalidTransactionException(ResponseCodeEnum.FAIL_INVALID);
			};
		}
	}

	public static final class FixedFeeWrapper {
		public enum FixedFeePayment {
			INVALID_PAYMENT,
			USE_HBAR,
			USE_CURRENTLY_CREATED_TOKEN,
			USE_EXISTING_FUNGIBLE_TOKEN
		}

		private final long amount;
		private final TokenID tokenID;
		private final boolean useHbarsForPayment;
		private final boolean useCurrentTokenForPayment;
		private final AccountID feeCollector;

		private FixedFeePayment fixedFeePayment;

		public FixedFeeWrapper(
				final long amount,
				final TokenID tokenID,
				final boolean useHbarsForPayment,
				final boolean useCurrentTokenForPayment,
				final AccountID feeCollector
		) {
			this.amount = amount;
			this.tokenID = tokenID;
			this.useHbarsForPayment = useHbarsForPayment;
			this.useCurrentTokenForPayment = useCurrentTokenForPayment;
			this.feeCollector = feeCollector;
			this.fixedFeePayment = setFixedFeePaymentType();
		}

		private FixedFeePayment setFixedFeePaymentType() {
			if (tokenID != null) {
				return !useHbarsForPayment && !useCurrentTokenForPayment
						? FixedFeePayment.USE_EXISTING_FUNGIBLE_TOKEN
						: FixedFeePayment.INVALID_PAYMENT;
			} else if (useCurrentTokenForPayment) {
				return !useHbarsForPayment
						? FixedFeePayment.USE_CURRENTLY_CREATED_TOKEN
						: FixedFeePayment.INVALID_PAYMENT;
			} else {
				return useHbarsForPayment
						? FixedFeePayment.USE_HBAR
						: FixedFeePayment.INVALID_PAYMENT;
			}
		}

		private FixedFee.Builder asBuilder() {
			return switch (fixedFeePayment) {
				case USE_HBAR -> FixedFee.newBuilder().setAmount(amount);
				case USE_EXISTING_FUNGIBLE_TOKEN -> FixedFee.newBuilder().setAmount(amount).setDenominatingTokenId(
						tokenID);
				case USE_CURRENTLY_CREATED_TOKEN -> FixedFee.newBuilder().setAmount(amount)
						.setDenominatingTokenId(TokenID.newBuilder()
								.setShardNum(0L)
								.setRealmNum(0L)
								.setTokenNum(0L)
								.build());
				default -> throw new InvalidTransactionException(ResponseCodeEnum.FAIL_INVALID);
			};
		}

		public FixedFeePayment getFixedFeePayment() {
			return this.fixedFeePayment;
		}

		public CustomFee asGrpc() {
			final var feeBuilder = CustomFee.newBuilder()
					.setFixedFee(asBuilder().build());
			if (feeCollector != null) {
				feeBuilder.setFeeCollectorAccountId(feeCollector);
			}
			return feeBuilder.build();
		}
	}

	public record FractionalFeeWrapper(
			long numerator,
			long denominator,
			long minimumAmount,
			long maximumAmount,
			boolean netOfTransfers,
			AccountID feeCollector
	) {
		public CustomFee asGrpc() {
			final var feeBuilder = CustomFee.newBuilder()
					.setFractionalFee(FractionalFee.newBuilder()
							.setFractionalAmount(Fraction.newBuilder()
									.setNumerator(numerator)
									.setDenominator(denominator)
									.build()
							)
							.setMinimumAmount(minimumAmount)
							.setMaximumAmount(maximumAmount)
							.setNetOfTransfers(netOfTransfers)
							.build()
					);
			if (feeCollector != null) {
				feeBuilder.setFeeCollectorAccountId(feeCollector);
			}
			return feeBuilder.build();
		}
	}

	public record RoyaltyFeeWrapper(
			long numerator, long denominator, FixedFeeWrapper fallbackFixedFee, AccountID feeCollector
	) {
		public CustomFee asGrpc() {
			final var royaltyFeeBuilder = RoyaltyFee.newBuilder()
					.setExchangeValueFraction(Fraction.newBuilder()
							.setNumerator(numerator)
							.setDenominator(denominator)
							.build()
					);
			if (fallbackFixedFee != null) {
				validateTrue(fallbackFixedFee.getFixedFeePayment() != FixedFeeWrapper.FixedFeePayment.INVALID_PAYMENT,
						ResponseCodeEnum.FAIL_INVALID
				);
				royaltyFeeBuilder.setFallbackFee(fallbackFixedFee.asBuilder().build());
			}

			final var customFeeBuilder = CustomFee.newBuilder().setRoyaltyFee(royaltyFeeBuilder.build());
			if (feeCollector != null) {
				customFeeBuilder.setFeeCollectorAccountId(feeCollector);
			}
			return customFeeBuilder.build();
		}
	}
}