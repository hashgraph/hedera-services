package com.hedera.node.app.spi.fixtures;

public class UserScenarioBuilder {
    private TestUser user;

    public UserScenarioBuilder(TestUser user) {
        this.user = user;
    }

    public UserScenarioBuilder withBalance(long balance) {
        return new UserScenarioBuilder(
                new TestUser(
                        user.accountID(),
                        user.account().copy().balance(balance).build(),
                        user.keyInfo()));
    }

    public TestUser build() {
        return user;
    }
}
