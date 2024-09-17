# Topic custom fees

## Purpose

The implementation of a native fixed fee system for the submission of topic messages on the Hedera network, will add the possibility of adding custom fees to a given topic.

## Prerequisite reading

* [HIP-991](https://hips.hedera.com/hip/hip-991)

## Goals

1. Update existing `ConsensusCreateTopic`, `ConsensusUpdateTopic` and `ConsensusTopicInfo` to support custom fees.
2. Introduce a new `ConsensusApproveAllowance` operation.
3. Update existing `ConsensusSubmitMessage` to validate and charge custom fees, if needed.

## Architecture

### HAPI Updates

Topics now have a new Fee Schedule Key.

```protobuf
/**
 * Access control for update/delete of custom fees.
 * <p>
 * If unset, custom fees CANNOT be set for this topic.<br/>
 * If not set when the topic is created, this field CANNOT be set via update.
 * If set when the topic is created, this field MAY be changed via update.
 */
Key fee_schedule_key = 8;
```

Topics can have an optional fees for submitting messages.

```protobuf
/**
 * A set of custom fee definitions.<br/>
 * These are fees to be assessed for each submit to this topic.
 * <p>
 * Each fee defined in this set SHALL be evaluated for
 * each message submitted to this topic, and the resultant
 * total assessed fees SHALL be charged.<br/>
 * Custom fees defined here SHALL be in addition to the base
 * network and node fees.
 */
repeated ConsensusCustomFee custom_fees = 10;
```

Fees can be set in HBAR or HTS fungible tokens.

```protobuf
/**
 * A custom fee definition for a consensus topic.
 *
 * This fee definition is specific to an Hedera Consensus Service (HCS) topic
 * and SHOULD NOT be used in any other context.<br/>
 * All fields for this message are REQUIRED.<br/>
 * Only "fixed" fee definitions are supported because there is no basis for
 * a fractional fee on a consensus submit transaction.
 */
message ConsensusCustomFee {
  /**
   * A fixed custom fee.
   * <p>
   * The amount of HBAR or other token described by this `FixedFee` SHALL
   * be charged to the transction payer for each message submitted to a
   * topic that assigns this consensus custom fee.
   */
  FixedFee fixed_fee = 1;

  /**
   * A collection account identifier.
   * <p>
   * All amounts collected for this consensus custom fee SHALL be transferred
   * to the account identified by this field.
   */
  AccountID fee_collector_account_id = 2;
}
```

Topics can have a list of keys that are allowed to send free messages.

```protobuf
/**
 * A set of keys that are allowed to submit messages to the topic without
 * paying the topic's custom fees.
 * <p>
 * If a submit transaction is signed by _any_ key included in this set,
 *  custom fees SHALL NOT be charged for that transaction.
 * <p>
 * If fee_exempt_key_list is unset in this transaction, there SHALL NOT be
 * any fee-exempt keys.  In particular, the following keys SHALL NOT be
 * implicitly or automatically added to this list:
 * `adminKey`, `submitKey`, `fee_schedule_key`.
 */
repeated Key fee_exempt_key_list = 9;
```

Note:
In `consensus_update_topic`, `custom_fees` and `fee_exempt_key_list` are wrapped in order to differentiate between setting an empty list and not updating the list.

Create new transaction type as defined in the HIP:

```protobuf
message ConsensusApproveAllowanceTransactionBody {
  /**
   * List of hbar allowances approved by the account owner.
   */
  repeated ConsensusCryptoFeeScheduleAllowance consensus_crypto_fee_schedule_allowances = 4;

  /**
   * List of fungible token allowances approved by the account owner.
   */
  repeated ConsensusTokenFeeScheduleAllowance consensus_token_fee_schedule_allowances = 5;
}
```

```protobuf
/**
 * An approved allowance of hbar transfers for a spender.
 */
message ConsensusCryptoFeeScheduleAllowance {
  /**
   * The account ID of the hbar owner (ie. the grantor of the allowance).
   */
  AccountID owner = 1;

  /**
   * The topic ID enabled to spend fees from the hbar allowance.
   */
  TopicID topicId = 2;

  /**
   * The amount of the spender's allowance in tinybars.
   */
  uint64 amount = 3;

  /**
   * The maximum amount of the spender's token allowance per message.
   */
  uint64 amount_per_message = 4;
}
```

```protobuf
/**
 * An approved allowance of fungible token transfers for a spender.
 */
message ConsensusTokenFeeScheduleAllowance {
  /**
   * The token that the allowance pertains to.
   */
  TokenID tokenId = 1;

  /**
   * The account ID of the token owner (ie. the grantor of the allowance).
   */
  AccountID owner = 2;

  /**
   * The topic ID enabled to spend fees from the token allowance.
   */
  TopicID topicId = 3;

  /**
   * The maximum amount of the spender's token allowance.
   */
  uint64 amount = 4;

  /**
   * The maximum amount of the spender's token allowance per message.
   */
  uint64 amount_per_message = 5;
}
```

Add new RPC to `consensus_service.proto` :

```protobuf
/**
 * Approve allowance for custom fees.
 * <p>
 * Set fee limits per topic.
 * It is applicable only for topics with custom fees.
 */
rpc approveAllowance (Transaction) returns (TransactionResponse);
```

### Fees

- Topics may have fees for submitting messages.
- Fees are defined as a list of custom fees.
- The list of custom fees can contain a maximum of `MAX_CUSTOM_FEE_ENTRIES_FOR_TOPICS` entries.
- A custom fee is defined leveraging the HTS’s FixedFee data structure.
- A custom fee can be set in HBAR or HTS fungible tokens and must have an accountID as collector.
- Fees can be set at topic creation time.
- Fees can be changed (updated or removed) in a topic with an update topic transaction signed by the Fee Schedule Key.

### Keys

- Topics can have a Fee Schedule Key set at creation time.
- The Fee Schedule Key can manage fee updates for the topic.
- Only the Admin Key should be able to remove the Fee Schedule Key.
- The Fee Schedule Key can change itself to another valid or unusable key.
- If the topic was created without a Fee Schedule Key, the key cannot be added later.

### Service updates

#### ConsensusTopicInfo

- Update in `topic.proto` the `Topic` protobuf message, to be able to store the new fields in the state.

```protobuf
    /**
     * Access control for update/delete of custom fees.
     * <p>
     * If this field is unset, the current custom fees CANNOT be changed.<br/>
     * If this field is set, that `Key` MUST sign any transaction to update
     * the custom fee schedule for this topic.
     */
    Key fee_schedule_key = 11;

    /**
     * A list of "privileged payer" keys.
     * <p>
     * If a submit transaction is signed by _any_ key from this list,
     * custom fees SHALL NOT be charged for that transaction.<br/>
     * If fee_exempt_key_list is unset, it SHALL _implicitly_ contain
     * the key `admin_key`, the key `submit_key`, and the key
     * `fee_schedule_key`, if any of those keys are set.
     */
    repeated Key fee_exempt_key_list = 12;

    /**
     * A set of custom fee definitions.<br/>
     * These are fees to be assessed for each submit to this topic.
     * <p>
     * If this list is empty, the only fees charged for a submit to this
     * topic SHALL be the network and node fees.<br/>
     * If this list is not empty, each fee defined in this set SHALL
     * be evaluated for each message submitted to this topic, and the
     * resultant total assessed fees SHALL be charged.<br/>
     * If this list is not empty, custom fees defined here SHALL be
     * charged _in addition to_ the base network and node fees.
     */
    repeated ConsensusCustomFee custom_fees = 13;
```

- Update `ConsensusTopicInfoHandler` class.
  - Add information about the new fields `feeScheduleKey()`, `feeExemptKeyList()`, `customFees()` to the query response.
  - Add needed fields and methods in `HapiCetTopicInfo` to support the new fields.

#### ConsensusCreateTopic

- Add default values for `maxCustoFeeEntriesForTopics` and `maxEntriesForFeeExemptKeyList`
- Update `ConsensusCreateTopicHandler`
  - Pure-check:
    - Check if all keys in FEKL are unique.
  - Handle:
    - Add validation on keys - `Fee Schedule Key` and all keys from the `Fee Exempt Key List`.
    - Add validation on custom fees:
      - Check if fee collector account is usable (not deleted/expired).
      - Check if the fee type is `fixed`.
      - If the fee has denominating token:
        - Check if the token is usable ()
        - Validate fee amount - TBD MAX_FEE is not defined in the HIP, and it is used in the context of SDK only
        - Check if the collector is associated with the token
    - Store the values of the new fields in the state.

#### ConsensusUpdateTopic

- Update `ConsensusUpdateTopicHandler`
  - Pure-check:
    - Check if all keys in FEKL are unique.
  - Pre-handle:
    - Check for `Fee Schedule Key` signature, in case of fees updates.
  - Handle:
    - Add validation on keys - `Fee Schedule Key` and all keys from the `Fee Exempt Key List`.
    - Add validation on custom fees:
      - Check if the fee collector account is usable (not deleted/expired).
      - Check if the fee type is `fixed`.
      - If the fee has denominating token:
        - Check if the token is usable ()
        - Validate fee amount - TBD- MAX_FEE is not defined in the HIP, and it is used in the context of SDK only
        - Check if the collector is associated with the token
    - Update the values of the new fields in the state.

#### ConsensusApproveAllowance

- Update `ApiPermissionConfig` class to include a `0-* PermissionedAccountsRange` for the new `ConsensusApproveAllowance` transaction type
- Update `ConsensusServiceDefinition` class to include the new RPC method definition for approve allowance.
- Implement new `ConsensusApproveAllowanceHandler` class that should be invoked when the gRPC server handles `ConsensusApproveAllowanceTransaction` transactions. The class should be responsible for:
  - Verify that the amounts are set and positive values.
  - Pre-handle:
    - The transaction must be signed by the sender.
    - TBD - more validations ?
  - Handle:
    - Any additional validation depending on config or state, i.e. semantics checks
    - Check that the sender account is a valid one. That is an existing account, and it is not deleted or expired.
    - Add approved allowance:
      - Set `ConsensusCryptoFeeScheduleAllowance` to the account.
      - Set `ConsensusTokenFeeScheduleAllowance` to the account
  - TBD Fee calculation logic
- Interact with the account state
- TBD Update throttle definitions to include the new `ConsensusApproveAllowance` transaction type
  - Throttle definitions are specified in `throttles.json` files
  - There are different configurations containing throttle definitions under `hedera-node/configuration/` for the different environments e.g. testnet, previewnet, mainnet
  - There is also a default throttle definition file in `resources/genesis/throttles.json` that is used during the genesis
  - Add the new `ConsensusApproveAllowance` transaction type to the `ThroughputLimits` bucket
  - Add this new operation type to the `ThroughputLimits` throttle bucket group, so that it's included in the throttling mechanism

#### ConsensusSubmitMessage

- Update `ConsensusSubmitMessageHandler`
  - Handle:
    - Check if the topic has custom fees:
      - Check if the sender has the needed allowance set.
      - Dispatch a crypto transfer transaction to pay the fees.

## Acceptance Tests

### Topic create acceptance tests
  * todo
### Topic update acceptance tests
  * todo
### Approve allowance acceptance tests
  * todo
### Submit message acceptance tests
  * todo
