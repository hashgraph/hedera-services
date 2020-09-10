package com.hedera.services.legacy.regression;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import java.io.IOException;
import java.net.URISyntaxException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.builder.TransactionSigner;

/**
 * Tests for updating protected accounts (i.e. those with sequence number under 1000) 
 * with protectedMaxEntityNum=0 setting in the HGCApp application.properties.
 * In particular, these entities are able to update themselves and by other accounts.
 * 
 * @author Hua Li
 * Created on 2019-09-04
 */
public class ProtectedAccountUpdateTests extends ProtectedEntityTests {
  private static final Logger log = LogManager.getLogger(ProtectedAccountUpdateTests.class);

  public ProtectedAccountUpdateTests(String testConfigFilePath)
      throws URISyntaxException, IOException {
    super(testConfigFilePath);
  }

  public static void main(String[] args) throws Throwable {
    ProtectedAccountUpdateTests tester = new ProtectedAccountUpdateTests(testConfigFilePath);
    long[] sysAccountToFund = {3, 49, 50, 51, 55, 56, 57, 58, 59, 60, 80, 81, 100, 45, 46};
    tester.init(sysAccountToFund);
    tester.runTests();
  }

  protected void runTests() throws Throwable {
    updateSelfTests();
    updateAccountWithGenesisAsPayer();
    updateAccountWithMasterAsPayer();
    updateAccountWithProtectedAccountAsPayer();
    updateAccountWithNonProtectedAccountAsPayer();
    updateAccountWithNewKeysTests();
  }

  /**
   * Tests updating protected accounts by themselves except master account (i.e. 50).
   */
  public void updateSelfTests() throws Throwable {
    // create an account
    receiverSigRequired = false;
    payerAccounts = accountCreatBatch(1);
  
    AccountID nodeID = defaultListeningNodeAccountID;
  
    long[] accounts = {2, 45, 46, 49, 50, 51, 55, 56, 57, 80, 81, 100, payerAccounts[0].getAccountNum()};
    for(int i = 0; i < accounts.length; i++) {
      AccountID account = genAccountID(accounts[i]);
      if(account.getAccountNum() <= 1000)
        acc2ComplexKeyMap.put(account, acc2ComplexKeyMap.get(genesisAccountID)); // accounts under 1000 has the same keypair as genesis
    }
    
    long[] otherAccounts = accounts;
    for (int i = 0; i < otherAccounts.length; i++) {
      AccountID payerID = genAccountID(otherAccounts[i]);
        
      for(int j = 0; j < accounts.length; j++) {
        AccountID account = genAccountID(accounts[j]);
        if(payerID.equals(account)) {
          if(payerID.getAccountNum() == 50) {
            updateAccount(account, payerID, nodeID, ResponseCodeEnum.AUTHORIZATION_FAILED, null); 
            log.info("update failed: payer=" + payerID.getAccountNum() + ", target acount=" + account);
          } else {
            updateAccount(account, payerID, nodeID, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS); 
            log.info("update success: payer=" + payerID.getAccountNum() + ", target acount=" + account);
          }
        }
      }
    }
    log.info(header + "updateSelfTests finished");
  }

  /**
   * Tests updating protected accounts by themselves.
   */
  public void updateAccountWithNewKeysTests() throws Throwable {
    // create an account
    receiverSigRequired = false;
    payerAccounts = accountCreatBatch(1);
    
    Key originalKey = acc2ComplexKeyMap.get(genesisAccountID);
    Key newKey = genComplexKey("single");
    AccountID nodeID = defaultListeningNodeAccountID;
  
    long[] accounts = {2, 45, 46, 49, 50, 51, 55, 56, 57, 80, 81, 100, payerAccounts[0].getAccountNum()};
    for(int i = 0; i < accounts.length; i++) {
      AccountID account = genAccountID(accounts[i]);
      if(account.getAccountNum() <= 1000)
        acc2ComplexKeyMap.put(account, originalKey ); // accounts under 1000 has the same keypair as genesis
    }
    
    long[] otherAccounts = accounts;
    for (int i = 0; i < otherAccounts.length; i++) {
      AccountID payerID = genAccountID(otherAccounts[i]);
        
      for(int j = 0; j < accounts.length; j++) {
        AccountID account = genAccountID(accounts[j]);
        if(payerID.equals(account)) {// all accounts (including genesis) can update themselves except master account
          if(payerID.getAccountNum() == 50) {
            updateAccount(account, payerID, nodeID, ResponseCodeEnum.AUTHORIZATION_FAILED, null, true, newKey); 
            log.info("update failed: payer=" + payerID.getAccountNum() + ", target acount=" + account);
          }
          else {
            updateAccount(account, payerID, nodeID, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, true, newKey);
            log.info("update success: payer=" + payerID.getAccountNum() + ", target acount=" + account);
          }
        }
        else if(payerID.getAccountNum() == 2) { 
          if(account.getAccountNum() <= 1000) // genesis can update all protected accounts without needing sig of the existing account 
            updateAccount(account, payerID, nodeID, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, false, newKey);
          else // genesis can update non-protected accounts but need sig of the existing account
            updateAccount(account, payerID, nodeID, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, true, newKey);
          log.info("update success: payer=" + payerID.getAccountNum() + ", target acount=" + account);
        }
      }
    }
    log.info(header + "updateAccountWithNewKeysTests: change to new keys finished");
    
    for (int i = 0; i < otherAccounts.length; i++) {
      AccountID payerID = genAccountID(otherAccounts[i]);
        
      for(int j = 0; j < accounts.length; j++) {
        AccountID account = genAccountID(accounts[j]);
        if(payerID.equals(account)) {// all accounts (including genesis) can update themselves except master account
          if(payerID.getAccountNum() != 50) {
            updateAccount(account, payerID, nodeID, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, true, originalKey);
            log.info("update success: payer=" + payerID.getAccountNum() + ", target acount=" + account);
          }
        }
        else if(payerID.getAccountNum() == 2) { 
          if(account.getAccountNum() <= 1000) // genesis can update all protected accounts without needing sig of the existing account 
            updateAccount(account, payerID, nodeID, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, false, originalKey);
          else // genesis can update non-protected accounts but need sig of the existing account
            updateAccount(account, payerID, nodeID, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, true, originalKey);
          log.info("update success: payer=" + payerID.getAccountNum() + ", target acount=" + account);
        }
      }
    }
    log.info(header + "updateAccountWithNewKeysTests: revert back to original key finished");
    log.info(header + "updateAccountWithNewKeysTests finished");
  }

  /**
   * Tests updating accounts by genesis:
   * 1) genesis can update itself in manner similar to regular account updating itself;
   * 2) genesis can update all protected accounts including master account without needing sig of updated account;
   * 3) genesis can also update non-protected accounts but will need sig of updated account.
   */
  public void updateAccountWithGenesisAsPayer() throws Throwable {
    // create an account
    payerAccounts = accountCreatBatch(1);
    
    Key originalKey = acc2ComplexKeyMap.get(genesisAccountID);
    AccountID nodeID = defaultListeningNodeAccountID;
  
    long[] accounts = {2, 45, 46, 49, 50, 51, 55, 56, 57, 80, 81, 100, payerAccounts[0].getAccountNum()};
    for(int i = 0; i < accounts.length; i++) {
      AccountID account = genAccountID(accounts[i]);
      if(account.getAccountNum() <= 1000)
        acc2ComplexKeyMap.put(account, originalKey ); // accounts under 1000 has the same keypair as genesis
    }
    
    long[] payers = {2};
    for (int i = 0; i < payers.length; i++) {
      AccountID payerID = genAccountID(payers[i]);
        
      for(int j = 0; j < accounts.length; j++) {
        AccountID account = genAccountID(accounts[j]);
        if(payerID.equals(account)) {// self update is OK
          updateAccount(account, payerID, nodeID, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, true, null);
          log.info("update success: payer=" + payerID.getAccountNum() + ", target acount=" + account);
        }
        else if(account.getAccountNum() <= 1000)  // genesis can update all protected accounts without needing sig of the existing account
            updateAccount(account, payerID, nodeID, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, false, null);
        else // genesis can update non-protected accounts but need sig of the existing account
          updateAccount(account, payerID, nodeID, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, true, null);
        log.info("update success: payer=" + payerID.getAccountNum() + ", target acount=" + account);
      }
    }
    log.info(header + "updateAccountWithGenesisAsPayer: finished");
  }

  /**
   * Tests updating accounts by master account (i.e. 50):
   * 1) master can NOT update itself;
   * 2) master can update protected accounts 51 through 80 without needing sig of updated account;
   * 3) master can NOT update protected accounts other than 51 through 80;
   * 4) master can also update non-protected accounts but will need sig of updated account.
   */
  public void updateAccountWithMasterAsPayer() throws Throwable {
    // create an account
    payerAccounts = accountCreatBatch(1);
    
    Key originalKey = acc2ComplexKeyMap.get(genesisAccountID);
    AccountID nodeID = defaultListeningNodeAccountID;
  
    long[] accounts = {2, 45, 46, 49, 50, 51, 55, 56, 57, 80, 81, 100, payerAccounts[0].getAccountNum()};
    for(int i = 0; i < accounts.length; i++) {
      AccountID account = genAccountID(accounts[i]);
      if(account.getAccountNum() <= 1000)
        acc2ComplexKeyMap.put(account, originalKey ); // accounts under 1000 has the same keypair as genesis
    }
    
    long[] payers = {50};
    for (int i = 0; i < payers.length; i++) {
      AccountID payerID = genAccountID(payers[i]);
        
      for(int j = 0; j < accounts.length; j++) {
        AccountID account = genAccountID(accounts[j]);
        if(payerID.equals(account)) {// self update is NOT OK
          updateAccount(account, payerID, nodeID, ResponseCodeEnum.AUTHORIZATION_FAILED, null, true, null);
          log.info("update failed: payer=" + payerID.getAccountNum() + ", target acount=" + account);
        }
        else if(account.getAccountNum() >= 51 && account.getAccountNum() <= 80)  // master can update protected accounts 51 through 80 without needing sig of updated account
          updateAccount(account, payerID, nodeID, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, false, null);
        else if(account.getAccountNum() <= 1000) { // master can NOT update protected accounts other than 51 through 80
          updateAccount(account, payerID, nodeID, ResponseCodeEnum.AUTHORIZATION_FAILED, null, false, null);
          log.info("update failed: payer=" + payerID.getAccountNum() + ", target acount=" + account);
        }
        else // can update non-protected accounts but need sig of the existing account
          updateAccount(account, payerID, nodeID, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, true, null);
        log.info("update success: payer=" + payerID.getAccountNum() + ", target acount=" + account);
      }
    }
    log.info(header + "updateAccountWithMasterAsPayer: finished");
  }

  /**
   * Tests updating accounts by protected accounts other than genesis and master:
   * 1) they can update themselves;
   * 2) they can NOT update protected accounts except self;
   * 3) they can update non-protected accounts but will need sig of updated account.
   */
  public void updateAccountWithProtectedAccountAsPayer() throws Throwable {
    // create an account
    payerAccounts = accountCreatBatch(1);
    
    Key originalKey = acc2ComplexKeyMap.get(genesisAccountID);
    AccountID nodeID = defaultListeningNodeAccountID;
  
    long[] accounts = {2, 45, 46, 49, 50, 51, 55, 56, 57, 80, 81, 100, payerAccounts[0].getAccountNum()};
    for(int i = 0; i < accounts.length; i++) {
      AccountID account = genAccountID(accounts[i]);
      if(account.getAccountNum() <= 1000)
        acc2ComplexKeyMap.put(account, originalKey ); // accounts under 1000 has the same keypair as genesis
    }
    
    long[] payers = {45, 46, 49, 51, 55, 56, 57, 80, 81, 100};
    for (int i = 0; i < payers.length; i++) {
      AccountID payerID = genAccountID(payers[i]);
        
      for(int j = 0; j < accounts.length; j++) {
        AccountID account = genAccountID(accounts[j]);
        if(payerID.equals(account)) {// self update is OK
          updateAccount(account, payerID, nodeID, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, true, null);
        }
        else if(account.getAccountNum() <= 1000) { // can NOT update protected accounts except self
          updateAccount(account, payerID, nodeID, ResponseCodeEnum.AUTHORIZATION_FAILED, null, false, null);
          log.info("update failed: payer=" + payerID.getAccountNum() + ", target acount=" + account);
        }
        else // can update non-protected accounts but need sig of the existing account
          updateAccount(account, payerID, nodeID, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, true, null);
        log.info("update success: payer=" + payerID.getAccountNum() + ", target acount=" + account);
      }
    }
    log.info(header + "updateAccountWithProtectedAccountAsPayer: finished");
  }

  /**
   * Tests updating accounts by non-protected accounts (i.e. all greater than 1000):
   * 1) they can update themselves;
   * 2) they can NOT update protected accounts;
   * 3) they can update non-protected accounts but will need sig of updated account.
   */
  public void updateAccountWithNonProtectedAccountAsPayer() throws Throwable {
    // create an account
    payerAccounts = accountCreatBatch(1);
    
    Key originalKey = acc2ComplexKeyMap.get(genesisAccountID);
    AccountID nodeID = defaultListeningNodeAccountID;
  
    long[] accounts = {2, 45, 46, 49, 50, 51, 55, 56, 57, 80, 81, 100, payerAccounts[0].getAccountNum()};
    for(int i = 0; i < accounts.length; i++) {
      AccountID account = genAccountID(accounts[i]);
      if(account.getAccountNum() <= 1000)
        acc2ComplexKeyMap.put(account, originalKey ); // accounts under 1000 has the same keypair as genesis
    }
    
    long[] payers = {payerAccounts[0].getAccountNum()};
    for (int i = 0; i < payers.length; i++) {
      AccountID payerID = genAccountID(payers[i]);
        
      for(int j = 0; j < accounts.length; j++) {
        AccountID account = genAccountID(accounts[j]);
        if(payerID.equals(account)) {// self update is OK
          updateAccount(account, payerID, nodeID, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, true, null);
        }
        else if(account.getAccountNum() <= 1000) { // can NOT update protected accounts
          updateAccount(account, payerID, nodeID, ResponseCodeEnum.AUTHORIZATION_FAILED, null, false, null);
          log.info("update failed: payer=" + payerID.getAccountNum() + ", target acount=" + account);
        }
        else // can update non-protected accounts but need sig of the existing account
          updateAccount(account, payerID, nodeID, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, true, null);
        log.info("update success: payer=" + payerID.getAccountNum() + ", target acount=" + account);
      }
    }
    log.info(header + "updateAccountWithNonProtectedAccountAsPayer: finished");
  }
}
