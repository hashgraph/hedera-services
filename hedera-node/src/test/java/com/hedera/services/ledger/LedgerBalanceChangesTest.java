package com.hedera.services.ledger;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.accounts.BackingStore;
import com.hedera.services.ledger.accounts.HashMapBackingAccounts;
import com.hedera.services.ledger.accounts.HashMapBackingTokenRels;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;


@ExtendWith(MockitoExtension.class)
class LedgerBalanceChangesTest {
	private BackingStore<AccountID, MerkleAccount> backingAccounts = new HashMapBackingAccounts();
	private BackingStore<Pair<AccountID, TokenID>, MerkleTokenRelStatus> backingRels = new HashMapBackingTokenRels();

	private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
	private TransactionalLedger<
			Pair<AccountID, TokenID>,
			TokenRelProperty,
			MerkleTokenRelStatus> tokenRelsLedger;

	@Mock
	private TokenStore tokenStore;
	@Mock
	private EntityIdSource ids;
	@Mock
	private ExpiringCreations creator;
	@Mock
	private OptionValidator validator;
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private AccountRecordsHistorian historian;

	private HederaLedger subject;

	@BeforeEach
	void setUp() {
		accountsLedger = new TransactionalLedger<>(
				AccountProperty.class, MerkleAccount::new, backingAccounts, new ChangeSummaryManager<>());
		tokenRelsLedger = new TransactionalLedger<>(
				TokenRelProperty.class, MerkleTokenRelStatus::new, backingRels, new ChangeSummaryManager<>());

		subject = new HederaLedger(tokenStore, ids, creator, validator, historian, dynamicProperties, accountsLedger);
		subject.setTokenRelsLedger(tokenRelsLedger);
	}

//	@Test
	void happyPathScans() {
		givenInitialBalances();

		// when:
		final var result = subject.doZeroSum(fixtureChanges());

		// then:
		assertEquals(OK, result);
		// and:
	}

	private void givenInitialBalances() {
		throw new AssertionError("Not implemented!");
	}

	private List<BalanceChange> fixtureChanges() {
		return List.of(new BalanceChange[] {
						BalanceChange.hbarAdjust(aModel, aHbarChange),
						BalanceChange.hbarAdjust(bModel, bHbarChange),
						BalanceChange.hbarAdjust(cModel, cHbarChange),
						BalanceChange.tokenAdjust(anotherToken, aModel, aAnotherTokenChange),
						BalanceChange.tokenAdjust(anotherToken, bModel, bAnotherTokenChange),
						BalanceChange.tokenAdjust(anotherToken, cModel, cAnotherTokenChange),
						BalanceChange.tokenAdjust(token, bModel, bTokenChange),
						BalanceChange.tokenAdjust(token, cModel, cTokenChange),
						BalanceChange.tokenAdjust(yetAnotherToken, aModel, aYetAnotherTokenChange),
						BalanceChange.tokenAdjust(yetAnotherToken, bModel, bYetAnotherTokenChange),
				}
		);
	}

	private final Id aModel = new Id(1, 2, 3);
	private final Id bModel = new Id(2, 3, 4);
	private final Id cModel = new Id(3, 4, 5);
	private final Id token = new Id(0, 0, 75231);
	private final Id anotherToken = new Id(0, 0, 75232);
	private final Id yetAnotherToken = new Id(0, 0, 75233);
	private final TokenID anId = asToken("0.0.75231");
	private final TokenID anotherId = asToken("0.0.75232");
	private final TokenID yetAnotherId = asToken("0.0.75233");
	private final AccountID a = asAccount("1.2.3");
	private final AccountID b = asAccount("2.3.4");
	private final AccountID c = asAccount("3.4.5");

	private final long aStartBalance = 1_000L;
	private final long bStartBalance = 2_000L;
	private final long cStartBalance = 3_000L;
	private final long aHbarChange = -100L;
	private final long bHbarChange = +50L;
	private final long cHbarChange = +50L;
	private final long aAnotherTokenChange = -50L;
	private final long bAnotherTokenChange = +25L;
	private final long cAnotherTokenChange = +25L;
	private final long bTokenChange = -100L;
	private final long cTokenChange = +100L;
	private final long aYetAnotherTokenChange = -15L;
	private final long bYetAnotherTokenChange = +15L;
}