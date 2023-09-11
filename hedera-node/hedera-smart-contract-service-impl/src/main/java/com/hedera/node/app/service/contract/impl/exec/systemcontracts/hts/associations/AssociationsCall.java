package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.associations;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.DispatchForRcHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.datatypes.Address;

/**
 * Implements the associate and dissociate calls of the HTS contract.
 */
public class AssociationsCall extends DispatchForRcHtsCall<SingleTransactionRecordBuilder> {
    public static final Function HRC_ASSOCIATE = new Function("associate()", ReturnTypes.INT);
    public static final Function ASSOCIATE_ONE = new Function("associateToken(address,address)", ReturnTypes.INT_64);
    public static final Function DISSOCIATE_ONE = new Function("dissociateToken(address,address)", ReturnTypes.INT_64);
    public static final Function HRC_DISSOCIATE = new Function("dissociate()", ReturnTypes.INT);
    public static final Function ASSOCIATE_MANY = new Function("associateTokens(address,address[])", ReturnTypes.INT_64);
    public static final Function DISSOCIATE_MANY = new Function("dissociateTokens(address,address[])", ReturnTypes.INT_64);

    public AssociationsCall(
            final boolean onlyDelegatable,
            @NonNull final HtsCallAttempt attempt,
            @NonNull final Address sender,
            @NonNull final TransactionBody syntheticBody) {
        super(onlyDelegatable, attempt, sender, syntheticBody, SingleTransactionRecordBuilder.class);
    }
}
