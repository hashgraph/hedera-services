# AddressBookTestingTool Instructions

This document describes various manual tests that were performed using this testing app. This playbook should be used when designing automated tests using this app.

## Global Modifications and Starting Conditions

Be sure to clean and assemble the whole project if the java files have been modified.

### config.txt
comment out the current app
```
# app,		HashgraphDemo.jar,	   1,0,0,0,0,0,0,0,0,0, all
```
uncomment the AddressBookTestingTool.jar
```
 app,    AddressBookTestingTool.jar,
```

### settings.txt

Add the following lines to the settings.txt file

```
addressBookTestingTool.softwareVersion, 1
addressBookTestingTool.stakingBehavior, 1
```
## Testing Genesis Behavior

### Force Use of Config Address Book on Genesis
#### Instructions
1. Delete `sdk/data/saved` directory if it exists
2. Ensure settings.txt has `state.saveStatePeriod,     0`
3. Add to settings.txt the value `addressBook.forceUseOfConfigAddressBook, true`
4. Run the app for a short time.

#### Validation

* check the swirlds.log for the text

```
AddressBookInitializer: Overriding the address book in the state with the address book from config.txt
```

* check the directory `sdk/data/saved/address_book` for files
  * usedAddressBook_v1_<date>.txt
    * matches the addresses in the config.txt, including stake value.
  * usedAddressBook_v1_<date>.txt.debug
    * The configuration address book is the same as what is in config.txt
    * the state saved address book was null
    * the used address book text says `The Configuration Address Book Was Used.`


### Call to SwirldState.updateStake() on Genesis
#### Instructions
1. Delete `sdk/data/saved` directory if it exists
2. **Ensure settings.txt has `state.saveStatePeriod,     10` (differs from previous section)**
3. **Ensure settings.txt has the value `addressBook.forceUseOfConfigAddressBook, false` (differs from previous section)**
4. **Run the app for 20 seconds.  (differs from previous section)**

#### Validation

* check the swirlds.log for the text

```
AddressBookInitializer: The loaded signed state is null. The candidateAddressBook is set to genesisSwirldState.updateStake(configAddressBook, null).
```

* check the directory `sdk/data/saved/address_book` for files
  * usedAddressBook_v1_<date>.txt
    * **contains the addresses in the config.txt, all with stake 10. (differs from previous section)**
  * usedAddressBook_v1_<date>.txt.debug
    * The configuration address book is the same as what is in config.txt
    * the state saved address book was null
    * **the used address book matches the content of the non-debug .txt file. (differs from previous section)**

## Testing Non-Genesis Behavior, No Software Upgrade

### No Software Upgrade, Use Saved State Address Book
#### Instructions
1. Ensure settings.txt has `state.saveStatePeriod,     10`
2. Ensure settings.txt has the value `addressBook.forceUseOfConfigAddressBook, false`
3. Run the app for 20 seconds.

#### Validation

* check the swirlds.log for the text

```
AddressBookInitializer: No Software Upgrade. Continuing with software version 1 and using the loaded signed state's address book and stake values.
```

* check the directory `sdk/data/saved/address_book` for the latest files
  * usedAddressBook_v1_<date>.txt
    * contains the addresses in the config.txt, all with stake 10.
  * usedAddressBook_v1_<date>.txt.debug
    * The configuration address book is the same as what is in config.txt
    * **the state saved address book matches the content of the non-debug .txt file. (differs from previous section)**
    * **the used address book has the text `The State Saved Address Book Was Used.` (differs from previous section)**

### No Software Upgrade, Force Use of Config Address Book
#### Instructions
1. Ensure settings.txt has `state.saveStatePeriod,     10`
2. **Ensure settings.txt has the value `addressBook.forceUseOfConfigAddressBook, true` (differs from previous section)**
3. Run the app for 20 seconds.

#### Validation

* check the swirlds.log for the text

```
AddressBookInitializer: Overriding the address book in the state with the address book from config.txt
```

* check the directory `sdk/data/saved/address_book` for the latest files
  * usedAddressBook_v1_<date>.txt
    * **matches the addresses in the config.txt, including stake value. (differs from previous section)**
  * usedAddressBook_v1_<date>.txt.debug
    * The configuration address book is the same as what is in config.txt
    * **the state saved address book contains the addresses in the config.txt, all with stake 10. (differs from previous section)**
    * **the used address book has the text `The Configuration Address Book Was Used.` (differs from previous section)**

### No Software Upgrade, Use Saved State Address Book, Matches Config
#### Instructions
1. Ensure settings.txt has `state.saveStatePeriod,     10`
2. **Ensure settings.txt has the value `addressBook.forceUseOfConfigAddressBook, false` (differs from previous section)**
3. Run the app for 20 seconds.

#### Validation

* check the swirlds.log for the text

```
AddressBookInitializer: No Software Upgrade. Continuing with software version 1 and using the loaded signed state's address book and stake values.
```

* check the directory `sdk/data/saved/address_book` for the latest files
  * usedAddressBook_v1_<date>.txt
    * matches the addresses in the config.txt, including stake value.
  * usedAddressBook_v1_<date>.txt.debug
    * The configuration address book is the same as what is in config.txt
    * **The state saved address book is the same as what is in the config.txt (differs from previous section)**
    * **the used address book has the text `The State Saved Address Book Was Used.` (differs from previous section)**

## Testing Non-Genesis Behavior, Software Upgrade

### Software Upgrade, Staking Behavior 2
#### Instructions
1. Ensure settings.txt has `state.saveStatePeriod,     10`
2. Ensure settings.txt has the value `addressBook.forceUseOfConfigAddressBook, false`
3. **Ensure settings.txt has the value `addressBookTestingTool.softwareVersion, 2` (differs from previous section)**
4. **Ensure settings.txt has the value `addressBookTestingTool.stakingBehavior, 2` (differs from previous section)**
6. Run the app for 20 seconds.

#### Validation

* check the swirlds.log for the text

```
AddressBookInitializer: Software Upgrade from version 1 to 2. The address book stake will be updated by the saved state's SwirldState.
```

* check the directory `sdk/data/saved/address_book` for the latest files
  * usedAddressBook_v1_<date>.txt
    * **matches the addresses in the config.txt, but the stake values incrementally increase starting from 0. (differs from previous section)**
  * usedAddressBook_v1_<date>.txt.debug
    * The configuration address book is the same as what is in config.txt
    * The state saved address book is the same as what is in config.txt
    * **the used address book matches the content of the non-debug .txt file. (differs from previous section)**


### Software Upgrade, Force Use Of Config Address Book
#### Instructions
1. Ensure settings.txt has `state.saveStatePeriod,     10`
2. **Ensure settings.txt has the value `addressBook.forceUseOfConfigAddressBook, true` (differs from previous section)**
3. **Ensure settings.txt has the value `addressBookTestingTool.softwareVersion, 3` (differs from previous section)**
4. **Ensure settings.txt has the value `addressBookTestingTool.stakingBehavior, 1` (differs from previous section)**
6. Run the app for 20 seconds.

#### Validation

* check the swirlds.log for the text

```
AddressBookInitializer: Overriding the address book in the state with the address book from config.txt
```

* check the directory `sdk/data/saved/address_book` for the latest files
  * usedAddressBook_v1_<date>.txt
    * **matches the addresses in the config.txt, including stake value. (differs from previous section)**
  * usedAddressBook_v1_<date>.txt.debug
    * The configuration address book is the same as what is in config.txt
    * **The state saved address book is the config.txt addresses with stake values incrementally increasing starting from 0 (differs from previous section)**
    * **the used address book matches the content of the non-debug .txt file. (differs from previous section)**
