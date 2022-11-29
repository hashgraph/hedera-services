package com.hedera.node.app.service.token.impl.test.entity;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.token.impl.entity.AccountBuilderImpl;
import com.hedera.node.app.spi.key.HederaKey;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Key;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccountBuilderImplTest {
    private AccountBuilderImpl subject;
    final Key key = Key.newBuilder()
            .setKeyList(
                    KeyList.newBuilder()
                            .addKeys(
                                    com.hederahashgraph.api.proto.java.Key.newBuilder()
                                            .setEd25519(
                                                    ByteString.copyFrom(
                                                            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                                                                    .getBytes()))))
                            .build();

    private final HederaKey key = asHederaKey(key);

    @BeforeEach
    void setUp(){
        subject = new AccountBuilderImpl().key()
    }

    @Test
    void constructorWorks(){
        subject = new AccountBuilderImpl();
    }
}
