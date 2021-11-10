package com.hedera.services.store.contracts.precompile;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.txns.token.process.Dissociation;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.precompile.AbstractPrecompiledContract;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;

@Singleton
public class HTSPrecompiledContract extends AbstractPrecompiledContract {
	private static final Logger LOG = LogManager.getLogger(HTSPrecompiledContract.class);
	private final HederaLedger ledger;
	private final AccountStore accountStore;
	private final TypedTokenStore tokenStore;
	private final GlobalDynamicProperties dynamicProperties;
	private final OptionValidator validator;
	int ABI_ID_ASSOCIATE_TOKEN = 0xa7efe348;  // "associateToken(address)"
	int ABI_ID_BURN_TOKEN = 0xd4e34845;       // "burnToken(address,int64)"
	int ABI_ID_DISSOCIATE_TOKEN = 0xd5d607b6; // "dissociateToken(address)"
	int ABI_ID_MINT_TOKEN = 0x67c44fb8;       // "mintToken(address,int64)"
	int ABI_ID_TRANSFER_TOKEN = 0x75fd1606;   // "transferToken(address,address,int64)"

	@Inject
	public HTSPrecompiledContract(
			final HederaLedger ledger,
			final AccountStore accountStore,
			final OptionValidator validator,
			final TypedTokenStore tokenStore,
			GlobalDynamicProperties dynamicProperties,
			final GasCalculator gasCalculator) {
		super("HTS", gasCalculator);
		this.ledger = ledger;
		this.validator = validator;
		this.tokenStore = tokenStore;
		this.accountStore = accountStore;
		this.dynamicProperties = dynamicProperties;
	}

	@Override
	public Gas gasRequirement(final Bytes input) {
		return Gas.of(10_000); // TODO revisit cost, this is arbitrary
	}

	@Override
	public Bytes compute(final Bytes input, final MessageFrame messageFrame) {
		if (messageFrame.isStatic()) {
			messageFrame.setRevertReason(
					Bytes.of("Cannot interact with HTS in a static call".getBytes(StandardCharsets.UTF_8)));
			return null;
		}

		int functionId = input.getInt(0);
		if (functionId == ABI_ID_TRANSFER_TOKEN) {
			return computeTransferToken(input, messageFrame);
		} else if (functionId == ABI_ID_MINT_TOKEN) {
			return computeMintToken(input, messageFrame);
		} else if (functionId == ABI_ID_BURN_TOKEN) {
			return computeBurnToken(input, messageFrame);
		} else if (functionId == ABI_ID_ASSOCIATE_TOKEN) {
			return computeAssociateToken(input, messageFrame);
		} else if (functionId == ABI_ID_DISSOCIATE_TOKEN) {
			return computeDissociateToken(input, messageFrame);
		} else {
			// Null is the "Precompile Failed" signal
			return null;
		}
	}

	private Bytes computeTransferToken(final Bytes input, final MessageFrame messageFrame) {
		final var fromAddress = messageFrame.getSenderAddress();
		final Bytes tokenAddress = Address.wrap(input.slice(16, 20));
		final Bytes toAddress = Address.wrap(input.slice(48, 20));
		final BigInteger amount = input.slice(68, 32).toBigInteger();

		final var from = EntityIdUtils.accountParsedFromSolidityAddress(fromAddress.toArray());
		final var token = EntityIdUtils.tokenParsedFromSolidityAddress(tokenAddress.toArray());
		final var to = EntityIdUtils.accountParsedFromSolidityAddress(toAddress.toArray());

		// TODO validate addresses, associations, etc

		final List<BalanceChange> changes = new ArrayList<>();
		changes.add(
				BalanceChange.changingFtUnits(
						Id.fromGrpcToken(token),
						token,
						AccountAmount.newBuilder().setAccountID(from).setAmount(-amount.longValue()).build()
				));
		changes.add(
				BalanceChange.changingFtUnits(
						Id.fromGrpcToken(token),
						token,
						AccountAmount.newBuilder().setAccountID(to).setAmount(amount.longValue()).build()
				));

		ResponseCodeEnum responseCode;
		try {
			ledger.doZeroSum(changes);
			responseCode = ResponseCodeEnum.SUCCESS;
		} catch (InvalidTransactionException ite) {
			responseCode = ite.getResponseCode();
			if (responseCode == FAIL_INVALID) {
				LOG.warn("HTS Precompiled Contract failed, status {} ", responseCode);
			}
		}

		return UInt256.valueOf(responseCode.getNumber());
	}

	@SuppressWarnings("unused")
	private Bytes computeMintToken(final Bytes input, final MessageFrame messageFrame) {
		return null;
	}

	@SuppressWarnings("unused")
	private Bytes computeBurnToken(final Bytes input, final MessageFrame messageFrame) {
		return null;
	}

	@SuppressWarnings("unused")
	private Bytes computeAssociateToken(final Bytes input, final MessageFrame messageFrame) {
		final var address = messageFrame.getSenderAddress();
		final Bytes tokenAddress = Address.wrap(input.slice(16, 20));
		final var accountID = EntityIdUtils.accountParsedFromSolidityAddress(address.toArrayUnsafe());
		var account = accountStore.loadAccount(Id.fromGrpcAccount(accountID));
		final var tokenID = EntityIdUtils.tokenParsedFromSolidityAddress(tokenAddress.toArrayUnsafe());
		var token = tokenStore.loadToken(Id.fromGrpcToken(tokenID));
		tokenStore.persistTokenRelationships(List.of(token.newRelationshipWith(account, false)));

		try {
			account.associateWith(List.of(token), dynamicProperties.maxTokensPerAccount(), false);
			accountStore.persistAccount(account); // this is bad, no easy rollback
			return UInt256.valueOf(ResponseCodeEnum.SUCCESS_VALUE);
		} catch (InvalidTransactionException ite) {
			return UInt256.valueOf(ite.getResponseCode().getNumber());
		} catch (Exception e) {
			return UInt256.valueOf(ResponseCodeEnum.UNKNOWN_VALUE);
		}
	}

	@SuppressWarnings("unused")
	private Bytes computeDissociateToken(final Bytes input, final MessageFrame messageFrame) {
		final var address = messageFrame.getSenderAddress();
		final Bytes tokenAddress = Address.wrap(input.slice(16, 20));
		final var accountID = EntityIdUtils.accountParsedFromSolidityAddress(address.toArrayUnsafe());
		var account = accountStore.loadAccount(Id.fromGrpcAccount(accountID));
		final var tokenID =
				Id.fromGrpcToken(EntityIdUtils.tokenParsedFromSolidityAddress(tokenAddress.toArrayUnsafe()));
//		var token = tokenStore.loadToken(Id.fromGrpcToken(tokenID));


		final List<Dissociation> dissociations = List.of(Dissociation.loadFrom(tokenStore, account, tokenID));

		try {
			/* --- Do the business logic --- */
			account.dissociateUsing(dissociations, validator);

			/* --- Persist the updated models --- */
			accountStore.persistAccount(account);
			final List<TokenRelationship> allUpdatedRels = new ArrayList<>();
			for (var dissociation : dissociations) {
				dissociation.addUpdatedModelRelsTo(allUpdatedRels);
			}
			tokenStore.persistTokenRelationships(allUpdatedRels);
			return UInt256.valueOf(ResponseCodeEnum.SUCCESS_VALUE);
		} catch (InvalidTransactionException ite) {
			return UInt256.valueOf(ite.getResponseCode().getNumber());
		} catch (Exception e) {
			return UInt256.valueOf(ResponseCodeEnum.UNKNOWN_VALUE);
		}

	}
}