package com.hedera.services.state.migration;

public record ToDiskMigrations(boolean doAccounts, boolean doTokenRels) {
}
