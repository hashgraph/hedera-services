# AddressBookTestingTool Instructions 

If this is the first time using the AddressBookTestingTool, please execute the following tests in order.  

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

### AddressTestingToolMain.java

```java 
private static BasicSoftwareVersion softwareVersion = new BasicSoftwareVersion(1);
```

### AddressTestingToolState.java

```java
private int stakingProfile = 1;
```



## Testing Genesis Behavior

### Force Use of Config Address Book on Genesis
#### Instructions
1. Delete `sdk/data/saved` directory if it exists
2. Ensure settings.txt has `state.saveStatePeriod,     0`
3. Add to settings.txt the value `state.forceUseOfConfigAddressBook, true`
4. Run the app for a short time.

#### Validation

* check the swirlds.log for the text

``` 
AddressBookInitializer: A setting has forced the use of the configuration address.
```

* check the directory `sdk/data/saved/address_book` for files
  * usedADdressBook_v1_<date>.txt
    * matches the addresses in the config.txt, including stake value.
  * usedADdressBook_v1_<date>.txt.debug
    * The configuration address book is the same as what is in config.txt
    * the state saved address book was null
    * the used address book text says `The Configuration Address Book Was Used.`


### Call to SwirldState.updateStake() on Genesis
#### Instructions
1. Delete `sdk/data/saved` directory if it exists
2. **Ensure settings.txt has `state.saveStatePeriod,     10` (changed)**
3. **Ensure settings.txt the value `state.forceUseOfConfigAddressBook, false` (changed)**
4. **Run the app for 20 seconds.  (changed)**

#### Validation

* check the swirlds.log for the text

``` 
AddressBookInitializer: The loaded signed state is null. The candidateAddressBook is set to genesisSwirldState.updateStake(configAddressBook, null).
```

* check the directory `sdk/data/saved/address_book` for files
  * usedADdressBook_v1_<date>.txt
    * **contains the addresses in the config.txt, all with stake 10. (changed)**
  * usedADdressBook_v1_<date>.txt.debug
    * The configuration address book is the same as what is in config.txt
    * the state saved address book was null
    * **the used address book matches the content of the non-debug .txt file. (changed)** 

## Testing Non-Genesis Behavior, No Software Upgrade

### No Software Upgrade, Use Saved State Address Book
#### Instructions
1. Ensure settings.txt has `state.saveStatePeriod,     10`
2. Ensure settings.txt the value `state.forceUseOfConfigAddressBook, false`
3. Run the app for 20 seconds.

#### Validation

* check the swirlds.log for the text

``` 
AddressBookInitializer: No Software Upgrade. Continuing with software version 1 and using the loaded signed state's address book and stake values.
```

* check the directory `sdk/data/saved/address_book` for the latest files
  * usedADdressBook_v1_<date>.txt
    * contains the addresses in the config.txt, all with stake 10.
  * usedADdressBook_v1_<date>.txt.debug
    * The configuration address book is the same as what is in config.txt
    * **the state saved address book matches the content of the non-debug .txt file. (changed)**
    * **the used address book has the text `The State Saved Address Book Was Used.` (changed)**

### No Software Upgrade, Force Use of Config Address Book
#### Instructions
1. Ensure settings.txt has `state.saveStatePeriod,     10`
2. **Ensure settings.txt the value `state.forceUseOfConfigAddressBook, true` (changed)**
3. Run the app for 20 seconds.

#### Validation

* check the swirlds.log for the text

``` 
AddressBookInitializer: A setting has forced the use of the configuration address.
```

* check the directory `sdk/data/saved/address_book` for the latest files
  * usedADdressBook_v1_<date>.txt
    * **matches the addresses in the config.txt, including stake value. (changed)**
  * usedADdressBook_v1_<date>.txt.debug
    * The configuration address book is the same as what is in config.txt
    * **the state saved address book contains the addresses in the config.txt, all with stake 10. (changed)** 
    * **the used address book has the text `The Configuration Address Book Was Used.` (changed)**

### No Software Upgrade, Use Saved State Address Book, Matches Config
#### Instructions
1. Ensure settings.txt has `state.saveStatePeriod,     10`
2. **Ensure settings.txt the value `state.forceUseOfConfigAddressBook, false` (changed)**
3. Run the app for 20 seconds.

#### Validation

* check the swirlds.log for the text

``` 
AddressBookInitializer: No Software Upgrade. Continuing with software version 1 and using the loaded signed state's address book and stake values.
```

* check the directory `sdk/data/saved/address_book` for the latest files
  * usedADdressBook_v1_<date>.txt
    * matches the addresses in the config.txt, including stake value.
  * usedADdressBook_v1_<date>.txt.debug
    * The configuration address book is the same as what is in config.txt
    * **The state saved address book is the same as what is in the config.txt (changed)**
    * **the used address book has the text `The State Saved Address Book Was Used.` (changed)**

## Testing Non-Genesis Behavior, Software Upgrade

### Software Upgrade, Force Use of Config Address Book
#### Instructions
1. Ensure settings.txt has `state.saveStatePeriod,     10`
2. Ensure settings.txt the value `state.forceUseOfConfigAddressBook, false`
3. **Increase the softwareVersion in AddressBookTestingToolMain.java to 2. (changed)**
4. **Change the stakingProfile in AddressBookTestingToolState.java to 2. (changed)**
5. **recompile the application: assemble ONLY!!!!.(changed)** 
6. Run the app for 20 seconds.

#### Validation

* check the swirlds.log for the text

``` 
AddressBookInitializer: Software Upgrade from version 1 to 2. The address book stake will be updated by the saved state's SwirldState.
```

* check the directory `sdk/data/saved/address_book` for the latest files
  * usedADdressBook_v1_<date>.txt
    * **matches the addresses in the config.txt, but the stake values incrementally increase starting from 0. (changed)**
  * usedADdressBook_v1_<date>.txt.debug
    * The configuration address book is the same as what is in config.txt
    * The state saved address book is the same as what is in config.txt
    * **the used address book matches the content of the non-debug .txt file. (changed)**
