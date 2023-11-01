package contract.approvals;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import contract.AbstractContractXTest;

import java.util.HashMap;
import java.util.Map;

import static com.hedera.node.app.service.contract.impl.ContractServiceImpl.CONTRACT_SERVICE;
import static contract.XTestConstants.AN_ED25519_KEY;
import static contract.XTestConstants.COINBASE_ID;
import static contract.XTestConstants.ONE_HBAR;
import static contract.XTestConstants.SENDER_ADDRESS;
import static contract.XTestConstants.SENDER_ALIAS;
import static contract.approvals.ApproveAllowanceXTestConstants.ACCOUNT_ID;
import static contract.approvals.ApproveAllowanceXTestConstants.GAS;
import static contract.approvals.ApproveAllowanceXTestConstants.HTS_APPROVE_ALLOWANCE_INITCODE_ID;
import static contract.approvals.ApproveAllowanceXTestConstants.NEXT_ENTITY_NUM;
import static contract.XTestConstants.SENDER_ID;
import static contract.approvals.ApproveAllowanceXTestConstants.NFT_TOKEN_TYPE_ID;
import static contract.approvals.ApproveAllowanceXTestConstants.OWNER_ID;
import static contract.approvals.ApproveAllowanceXTestConstants.RECIPIENT_ID;
import static contract.approvals.ApproveAllowanceXTestConstants.TOKEN_TREASURY_ID;

public class ApproveAllowanceXTest extends AbstractContractXTest {
    @Override
    protected void doScenarioOperations() {
        handleAndCommitSingleTransaction(CONTRACT_SERVICE.handlers().contractCreateHandler(), createHtsApproveAllowanceTxn());
        handleAndCommitSingleTransaction(
                component.tokenMintHandler(), nftMint(Bytes.wrap("HOLD"), NON_FUNGIBLE_UNIQUE));
    }

    @Override
    protected Map<FileID, File> initialFiles() {
        final var files = new HashMap<FileID, File>();
        files.put(
                HTS_APPROVE_ALLOWANCE_INITCODE_ID,
                File.newBuilder().contents(resourceAsBytes("initcode/HtsApproveAllowance.bin")).build());
        return files;
    }

    @Override
    protected long initialEntityNum() {
        return NEXT_ENTITY_NUM - 1;
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        final var aliases = new HashMap<ProtoBytes, AccountID>();
        aliases.put(ProtoBytes.newBuilder().value(SENDER_ALIAS).build(), SENDER_ID);
        aliases.put(ProtoBytes.newBuilder().value(SENDER_ADDRESS).build(), SENDER_ID);
        return aliases;
    }

    @Override
    protected Map<TokenID, Token> initialTokens() {
        final var tokens = new HashMap<TokenID, Token>();
        tokens.put(
                NFT_TOKEN_TYPE_ID,
                Token.newBuilder()
                        .tokenId(NFT_TOKEN_TYPE_ID)
                        .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(TOKEN_TREASURY_ID)
                        .adminKey(AN_ED25519_KEY)
                        .supplyKey(AN_ED25519_KEY)
                        .build());
        return tokens;
    }

    @Override
    protected Map<EntityIDPair, TokenRelation> initialTokenRelationships() {
        final var tokenRels = new HashMap<EntityIDPair, TokenRelation>();
        addUsableRelation(tokenRels, OWNER_ID, NFT_TOKEN_TYPE_ID, rel -> {});
        return tokenRels;
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        final var accounts = new HashMap<AccountID, Account>();
        accounts.put(
                SENDER_ID,
                Account.newBuilder()
                        .accountId(SENDER_ID)
                        .alias(SENDER_ALIAS)
                        .tinybarBalance(100 * ONE_HBAR)
                        .build());
        accounts.put(
                OWNER_ID,
                Account.newBuilder()
                        .accountId(OWNER_ID)
                        .tinybarBalance(10_000 * ONE_HBAR)
                        .build());
        accounts.put(
                RECIPIENT_ID,
                Account.newBuilder()
                        .accountId(RECIPIENT_ID)
                        .tinybarBalance(10_000 * ONE_HBAR)
                        .build());
        accounts.put(
                ACCOUNT_ID,
                Account.newBuilder()
                        .accountId(ACCOUNT_ID)
                        .tinybarBalance(10_000 * ONE_HBAR)
                        .build());
        accounts.put(
                TOKEN_TREASURY_ID,
                Account.newBuilder()
                        .accountId(TOKEN_TREASURY_ID)
                        .tinybarBalance(10_000 * ONE_HBAR)
                        .build());
        accounts.put(COINBASE_ID, Account.newBuilder().accountId(COINBASE_ID).build());
        return accounts;
    }

    private TransactionBody createHtsApproveAllowanceTxn() {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(SENDER_ID))
                .contractCreateInstance(ContractCreateTransactionBody.newBuilder()
                        .autoRenewPeriod(STANDARD_AUTO_RENEW_PERIOD)
                        .fileID(HTS_APPROVE_ALLOWANCE_INITCODE_ID)
                        .gas(GAS)
                        .build())
                .build();
    }
}
