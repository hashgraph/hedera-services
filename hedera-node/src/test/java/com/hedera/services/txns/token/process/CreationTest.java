package com.hedera.services.txns.token.process;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEES_LIST_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CreationTest {
	private final int maxTokensPerAccount = 1_000;
	private final long now = 1_234_567L;
	private final long initialSupply = 777L;
	private final Id provisionalId = new Id(0, 0, 666);

	private TokenCreateTransactionBody op;

	@Mock
	private EntityIdSource ids;
	@Mock
	private Token provisionalToken;
	@Mock
	private Account treasury;
	@Mock
	private Account autoRenew;
	@Mock
	private TokenRelationship newRel;
	@Mock
	private OptionValidator validator;
	@Mock
	private AccountStore accountStore;
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private Creation.NewRelsListing listing;
	@Mock
	private Creation.TokenModelFactory modelFactory;

	private Creation subject;

	@Test
	void verifiesExpiryBeforeLoading() {
		givenSubjectWithInvalidExpiry();

		assertFailsWith(
				() -> subject.loadModelsWith(grpcSponsor, accountStore, ids, validator),
				INVALID_EXPIRATION_TIME);
	}

	@Test
	void onlyLoadsTreasuryWithNoAutoRenew() {
		givenSubjectNoAutoRenew();
		given(accountStore.loadAccountOrFailWith(treasuryId, INVALID_TREASURY_ACCOUNT_FOR_TOKEN)).willReturn(treasury);
		given(ids.newTokenId(grpcSponsor)).willReturn(provisionalId.asGrpcToken());

		subject.loadModelsWith(grpcSponsor, accountStore, ids, validator);

		assertSame(treasury, subject.getTreasury());
		assertNull(subject.getAutoRenew());
		assertEquals(provisionalId, subject.getProvisionalId());
	}

	@Test
	void loadsAutoRenewWhenAvail() {
		givenSubjectWithEverything();
		given(accountStore.loadAccountOrFailWith(treasuryId, INVALID_TREASURY_ACCOUNT_FOR_TOKEN)).willReturn(treasury);
		given(accountStore.loadAccountOrFailWith(autoRenewId, INVALID_AUTORENEW_ACCOUNT)).willReturn(autoRenew);
		given(ids.newTokenId(grpcSponsor)).willReturn(provisionalId.asGrpcToken());

		subject.loadModelsWith(grpcSponsor, accountStore, ids, validator);

		assertSame(treasury, subject.getTreasury());
		assertSame(autoRenew, subject.getAutoRenew());
	}

	@Test
	void validatesNumCustomFees() {
		givenSubjectWithEverything();
		given(dynamicProperties.maxCustomFeesAllowed()).willReturn(1);

		assertFailsWith(
				() -> subject.doProvisionallyWith(now, modelFactory, listing),
				CUSTOM_FEES_LIST_TOO_LONG);
	}

	@Test
	void mintsInitialSupplyIfSet() {
		givenSubjectWithEverything();

		given(dynamicProperties.maxTokensPerAccount()).willReturn(maxTokensPerAccount);
		given(dynamicProperties.maxCustomFeesAllowed()).willReturn(2);
		given(modelFactory.createFrom(provisionalId, op, treasury, autoRenew, now)).willReturn(provisionalToken);
		given(listing.listFrom(provisionalToken, maxTokensPerAccount)).willReturn(List.of(newRel));

		subject.setProvisionalId(provisionalId);
		subject.setProvisionalToken(provisionalToken);
		subject.setTreasury(treasury);
		subject.setAutoRenew(autoRenew);

		subject.doProvisionallyWith(now, modelFactory, listing);

		verify(provisionalToken).mint(newRel, initialSupply, true);
	}

	@Test
	void doesntMintInitialSupplyIfNotSet() {
		givenSubjectWithEverythingExceptInitialSupply();

		given(dynamicProperties.maxTokensPerAccount()).willReturn(maxTokensPerAccount);
		given(dynamicProperties.maxCustomFeesAllowed()).willReturn(2);
		given(modelFactory.createFrom(provisionalId, op, treasury, autoRenew, now)).willReturn(provisionalToken);
		given(listing.listFrom(provisionalToken, maxTokensPerAccount)).willReturn(List.of(newRel));

		subject.setProvisionalId(provisionalId);
		subject.setProvisionalToken(provisionalToken);
		subject.setTreasury(treasury);
		subject.setAutoRenew(autoRenew);

		subject.doProvisionallyWith(now, modelFactory, listing);

		verify(provisionalToken, never()).mint(any(), anyLong(), anyBoolean());
	}

	private void givenSubjectWithEverything() {
		subject = new Creation(dynamicProperties, creationWithEverything());
	}

	private void givenSubjectWithEverythingExceptInitialSupply() {
		subject = new Creation(dynamicProperties, creationWithEverythingExceptInitialSupply());
	}

	private void givenSubjectNoAutoRenew() {
		subject = new Creation(dynamicProperties, creationNoAutoRenew());
	}

	private void givenSubjectWithInvalidExpiry() {
		subject = new Creation(dynamicProperties, creationInvalidExpiry());
	}

	private final AccountID grpcSponsor = IdUtils.asAccount("0.0.3");
	private final AccountID grpcTreasuryId = IdUtils.asAccount("0.0.1234");
	private final AccountID grpcAutoRenewId = IdUtils.asAccount("0.0.2345");
	private final Id treasuryId = Id.fromGrpcAccount(grpcTreasuryId);
	private final Id autoRenewId = Id.fromGrpcAccount(grpcAutoRenewId);

	private TokenCreateTransactionBody creationInvalidExpiry() {
		op = TokenCreateTransactionBody.newBuilder()
				.setExpiry(Timestamp.newBuilder().setSeconds(now))
				.setTreasury(grpcTreasuryId)
				.setAutoRenewAccount(grpcAutoRenewId)
				.build();
		return op;
	}

	private TokenCreateTransactionBody creationWithEverything() {
		op = TokenCreateTransactionBody.newBuilder()
				.setTreasury(grpcTreasuryId)
				.setInitialSupply(initialSupply)
				.setAutoRenewAccount(grpcAutoRenewId)
				.addCustomFees(CustomFee.getDefaultInstance())
				.addCustomFees(CustomFee.getDefaultInstance())
				.build();
		return op;
	}

	private TokenCreateTransactionBody creationWithEverythingExceptInitialSupply() {
		op = creationWithEverything().toBuilder().clearInitialSupply().build();
		return op;
	}

	private TokenCreateTransactionBody creationNoAutoRenew() {
		op = TokenCreateTransactionBody.newBuilder()
				.setTreasury(grpcTreasuryId)
				.setAutoRenewAccount(grpcAutoRenewId)
				.build();
		return op;
	}
}