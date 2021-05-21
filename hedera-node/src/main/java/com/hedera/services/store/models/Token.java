package com.hedera.services.store.models;

import com.google.common.base.MoreObjects;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import static com.hedera.services.exceptions.ValidationUtils.checkInvariant;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.utils.MiscUtils.describe;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;

public class Token {
	private final Id id;

	private boolean supplyHasChanged;

	private long totalSupply;
	private JKey kycKey;
	private JKey freezeKey;
	private JKey supplyKey;
	private Account treasury;
	private Account autoRenewAccount;

	public Token(Id id) {
		this.id = id;
	}

	public void burn(TokenRelationship treasuryRel, long amount) {
		checkInvariant(
				amount > 0,
				() -> "Cannot burn " + amount + " units of " + this + " from " + treasuryRel);
		changeSupply(treasuryRel, -amount, INVALID_TOKEN_BURN_AMOUNT);
	}

	public void mint(TokenRelationship treasuryRel, long amount) {
		checkInvariant(
				amount > 0,
				() -> "Cannot mint " + amount + " units of " + this + " from " + treasuryRel);
		changeSupply(treasuryRel, +amount, INVALID_TOKEN_MINT_AMOUNT);
	}

	private void changeSupply(TokenRelationship treasuryRel, long amount, ResponseCodeEnum negSupplyCode) {
		checkInvariant(treasuryRel != null, () -> "Cannot mint with a null treasuryRel");
		checkInvariant(
				treasuryRel.hasInvolvedIds(id, treasury.getId()),
				() -> "Cannot change " + this + " supply (" + amount + ") with non-treasury rel " + treasuryRel);

		validateTrue(supplyKey != null, TOKEN_HAS_NO_SUPPLY_KEY);

		final long newTotalSupply = totalSupply + amount;
		validateTrue(newTotalSupply >= 0, negSupplyCode);

		final long newTreasuryBalance = treasuryRel.getBalance() + amount;
		validateTrue(newTreasuryBalance >= 0, INSUFFICIENT_TOKEN_BALANCE);

		setTotalSupply(newTotalSupply);
		treasuryRel.setBalance(newTreasuryBalance);
	}

	public Account getTreasury() {
		return treasury;
	}

	public void setTreasury(Account treasury) {
		this.treasury = treasury;
	}

	public Account getAutoRenewAccount() {
		return autoRenewAccount;
	}

	public void setAutoRenewAccount(Account autoRenewAccount) {
		this.autoRenewAccount = autoRenewAccount;
	}

	public long getTotalSupply() {
		return totalSupply;
	}

	public void initTotalSupply(long totalSupply) {
		this.totalSupply = totalSupply;
	}

	public void setTotalSupply(long totalSupply) {
		supplyHasChanged = true;
		this.totalSupply = totalSupply;
	}

	public JKey getSupplyKey() {
		return supplyKey;
	}

	public void setSupplyKey(JKey supplyKey) {
		this.supplyKey = supplyKey;
	}

	public JKey getKycKey() {
		return kycKey;
	}

	public void setKycKey(JKey kycKey) {
		this.kycKey = kycKey;
	}

	public JKey getFreezeKey() {
		return freezeKey;
	}

	public void setFreezeKey(JKey freezeKey) {
		this.freezeKey = freezeKey;
	}

	public boolean hasChangedSupply() {
		return supplyHasChanged;
	}

	public Id getId() {
		return id;
	}

	/* NOTE: The object methods below are only overridden to improve
	readability of unit tests; model objects are not used in hash-based
	collections, so the performance of these methods doesn't matter. */
	@Override
	public boolean equals(Object obj) {
		return EqualsBuilder.reflectionEquals(this, obj);
	}

	@Override
	public int hashCode() {
		return HashCodeBuilder.reflectionHashCode(this);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(Token.class)
				.add("id", id)
				.add("treasury", treasury)
				.add("autoRenewAccount", autoRenewAccount)
				.add("kycKey", describe(kycKey))
				.add("freezeKey", describe(freezeKey))
				.add("supplyKey", describe(supplyKey))
				.toString();
	}
}
