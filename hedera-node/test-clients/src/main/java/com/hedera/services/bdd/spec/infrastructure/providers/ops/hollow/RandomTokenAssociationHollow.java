package com.hedera.services.bdd.spec.infrastructure.providers.ops.hollow;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.listeners.TokenAccountRegistryRel;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.token.RandomTokenAssociation;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class RandomTokenAssociationHollow extends RandomTokenAssociation {
    public RandomTokenAssociationHollow(
            RegistrySourcedNameProvider<TokenID> tokens,
            RegistrySourcedNameProvider<AccountID> accounts,
            RegistrySourcedNameProvider<TokenAccountRegistryRel> tokenRels,
            ResponseCodeEnum[] hollowAccountOutcomes,
            String... signers) {
        super(tokens, accounts, tokenRels, hollowAccountOutcomes, signers);
    }

    @Override
    public Optional<HapiSpecOperation> get() {
        if (tokenRels.numPresent() >= ceilingNum) {
            return Optional.empty();
        }

        var account = accounts.getQualifying();
        if (account.isEmpty()) {
            return Optional.empty();
        }

        int numTokensToTry = BASE_RANDOM.nextInt(MAX_TOKENS_PER_OP) + 1;
        Set<String> chosen = new HashSet<>();
        while (numTokensToTry-- > 0) {
            var token = tokens.getQualifyingExcept(chosen);
            token.ifPresent(chosen::add);
        }
        if (chosen.isEmpty()) {
            return Optional.empty();
        }
        String[] toUse = chosen.toArray(new String[0]);

        var op = tokenAssociate(account.get(), toUse)
                .hasPrecheckFrom(STANDARD_PERMISSIBLE_PRECHECKS)
                .hasKnownStatusFrom(ResponseCodeEnum.INVALID_SIGNATURE);

        if (signers != null && signers.length > 0) {
            op.signedBy(signers);
        }

        return Optional.of(op);
    }


}
