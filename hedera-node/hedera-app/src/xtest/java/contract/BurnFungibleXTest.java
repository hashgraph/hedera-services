package contract;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static contract.AssociationsXTestConstants.A_TOKEN_ADDRESS;
import static contract.AssociationsXTestConstants.A_TOKEN_ID;
import static contract.AssociationsXTestConstants.B_TOKEN_ADDRESS;
import static contract.AssociationsXTestConstants.B_TOKEN_ID;
import static contract.HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID;
import static contract.MiscClassicTransfersXTestConstants.INITIAL_RECEIVER_AUTO_ASSOCIATIONS;
import static contract.XTestConstants.AN_ED25519_KEY;
import static contract.XTestConstants.ERC20_TOKEN_ADDRESS;
import static contract.XTestConstants.ERC20_TOKEN_ID;
import static contract.XTestConstants.OWNER_ADDRESS;
import static contract.XTestConstants.OWNER_ID;
import static contract.XTestConstants.RECEIVER_ID;
import static contract.XTestConstants.SENDER_ADDRESS;
import static contract.XTestConstants.SENDER_BESU_ADDRESS;
import static contract.XTestConstants.SENDER_CONTRACT_ID_KEY;
import static contract.XTestConstants.SENDER_ID;
import static contract.XTestConstants.addErc20Relation;
import static contract.XTestConstants.assertSuccess;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.burn.BurnTranslator;
import com.hedera.node.app.spi.state.ReadableKVState;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.jetbrains.annotations.NotNull;

public class BurnFungibleXTest extends AbstractContractXTest {

    private static final long TOKEN_BALANCE = 9L;
    private static final long TOKENS_TO_BURN = 1L;

    @Override
    protected void doScenarioOperations() {
        // BURN_TOKEN_V1
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                        .encodeCallWithArgs(
                                ERC20_TOKEN_ADDRESS,
                                BigInteger.valueOf(TOKENS_TO_BURN),
                                new long[] {})
                        .array()),
                assertSuccess());

        // should revert when token has no supplyKey
        runHtsCallAndExpectRevert(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                        .encodeCallWithArgs(
                                A_TOKEN_ADDRESS,
                                BigInteger.valueOf(TOKENS_TO_BURN),
                                new long[]{})
                        .array()), TOKEN_HAS_NO_SUPPLY_KEY);

        // should revert when token is not associated to account
        runHtsCallAndExpectRevert(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                        .encodeCallWithArgs(
                                B_TOKEN_ADDRESS,
                                BigInteger.valueOf(TOKENS_TO_BURN),
                                new long[]{})
                        .array()), TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);

        // should revert on totalSupply < amountToBurn
        runHtsCallAndExpectRevert(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                        .encodeCallWithArgs(
                                ERC20_TOKEN_ADDRESS,
                                BigInteger.valueOf(TOKEN_BALANCE + 1),
                                new long[]{})
                        .array()), INVALID_TOKEN_BURN_AMOUNT);
    }

    @Override
    protected void assertExpectedTokenRelations(
            @NotNull final ReadableKVState<EntityIDPair, TokenRelation> tokenRelationships) {
        final var tokenRelation = tokenRelationships.get(EntityIDPair.newBuilder()
                .tokenId(ERC20_TOKEN_ID)
                .accountId(OWNER_ID)
                .build());
        assertNotNull(tokenRelation);
        assertEquals(TOKEN_BALANCE - TOKENS_TO_BURN, tokenRelation.balance());
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        final var aliases = new HashMap<ProtoBytes, AccountID>();
        aliases.put(ProtoBytes.newBuilder().value(SENDER_ADDRESS).build(), SENDER_ID);
        aliases.put(ProtoBytes.newBuilder().value(OWNER_ADDRESS).build(), OWNER_ID);
        return aliases;
    }

    @Override
    protected Map<TokenID, Token> initialTokens() {
        final var tokens = new HashMap<TokenID, Token>();
        tokens.put(
                ERC20_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(ERC20_TOKEN_ID)
                        .treasuryAccountId(OWNER_ID)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .supplyKey(AN_ED25519_KEY)
                        .totalSupply(TOKEN_BALANCE)
                        .build());
        tokens.put(
                A_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(A_TOKEN_ID)
                        .treasuryAccountId(OWNER_ID)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .totalSupply(TOKEN_BALANCE)
                        .build());
        tokens.put(
                B_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(B_TOKEN_ID)
                        .treasuryAccountId(OWNER_ID)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .supplyKey(AN_ED25519_KEY)
                        .totalSupply(TOKEN_BALANCE)
                        .build());
        return tokens;
    }

    @Override
    protected Map<EntityIDPair, TokenRelation> initialTokenRelationships() {
        final var tokenRelationships = new HashMap<EntityIDPair, TokenRelation>();
        addErc20Relation(tokenRelationships, UNAUTHORIZED_SPENDER_ID, TOKEN_BALANCE);
        addErc20Relation(tokenRelationships, OWNER_ID, TOKEN_BALANCE);
        return tokenRelationships;
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        final var accounts = new HashMap<AccountID, Account>();
        accounts.put(
                SENDER_ID,
                Account.newBuilder()
                        .accountId(OWNER_ID)
                        .alias(SENDER_ADDRESS)
                        .smartContract(true)
                        .build());
        accounts.put(
                OWNER_ID,
                Account.newBuilder()
                        .accountId(OWNER_ID)
                        .alias(OWNER_ADDRESS)
                        .key(SENDER_CONTRACT_ID_KEY)
                        .build());
        accounts.put(
                RECEIVER_ID,
                Account.newBuilder()
                        .accountId(RECEIVER_ID)
                        .maxAutoAssociations(INITIAL_RECEIVER_AUTO_ASSOCIATIONS)
                        .build());
        accounts.put(
                UNAUTHORIZED_SPENDER_ID,
                Account.newBuilder().accountId(UNAUTHORIZED_SPENDER_ID).build());
        return accounts;
    }
}
