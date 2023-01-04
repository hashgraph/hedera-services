package com.hedera.node.app.service.evm.store.tokens;

import com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmNftInfo;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmTokenInfo;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenInfo;
import org.hyperledger.besu.datatypes.Address;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public interface TokenAccessor {
    Optional<EvmTokenInfo> evmInfoForToken(final Address tokenId, final byte[] ledgerId);

    Optional<EvmNftInfo> evmNftInfo(final Address target, final byte[] ledgerId);

    boolean isTokenAddress(final Address address);

    boolean isFrozen(final Address accountId, final Address tokenId);

    boolean defaultFreezeStatus(final Address tokenId);

    boolean defaultKycStatus(final Address tokenId);

    boolean isKyc(final Address accountId, final Address tokenId);

    Optional<List<CustomFee>> infoForTokenCustomFees(final Address tokenId);

    TokenType typeOf(final Address tokenId);

    Optional<TokenInfo> infoForToken(final Address tokenId, final byte[] ledgerId);

    boolean keyOf(final TokenID tokenId, final Supplier<Enum> keyType);

    String nameOf(final Address tokenId);

    String symbolOf(final Address tokenId);

    long totalSupplyOf(final Address tokenId);

    int decimalsOf(final Address tokenId);

    long balanceOf(final Address accountId, final Address tokenId);

    long staticAllowanceOf(final Address ownerId, final Address spenderId, final Address tokenId);

    Address staticApprovedSpenderOf(final Address nftId);

    boolean staticIsOperator(final Address ownerId, final Address operatorId, final Address tokenId);

    Address ownerOf(final Address nftId);

    Address canonicalAddress(final Address addressOrAlias);

    String metadataOf(final Address nftId);
}
