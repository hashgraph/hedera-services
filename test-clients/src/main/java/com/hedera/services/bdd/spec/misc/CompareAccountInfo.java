package com.hedera.services.bdd.spec.misc;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.hederahashgraph.api.proto.java.Key;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class CompareAccountInfo {

	private static final String ACCOUNTS_FROM_STATE_PATH =
			"/Users/anighanta/IdeaProjects/hashgraph/hedera-services/test-clients/src/main/resource/accountsFromState.json";
	private static final String ACCOUNTS_FROM_API_PATH =
			"/Users/anighanta/IdeaProjects/hashgraph/hedera-services/test-clients/src/main/resource/accountsFromAPI.json";

	public static void main(String... args) throws IOException, ParseException, DecoderException {
		JSONObject fromState = (JSONObject) new JSONParser().parse(new FileReader(ACCOUNTS_FROM_STATE_PATH));
		JSONObject fromAPI = (JSONObject) new JSONParser().parse(new FileReader(ACCOUNTS_FROM_API_PATH));

		JSONArray accountsFromAPI = (JSONArray) fromAPI.get("accounts");
		JSONArray accountsFromState = (JSONArray) fromState.get("accounts");

		Iterator apiAccountsItr = accountsFromAPI.iterator();
		Iterator stateAccountsItr = accountsFromState.iterator();

		int counter = 0;

		while(apiAccountsItr.hasNext() && counter < 100) {

			Map accountInfoFromAPI = (Map) apiAccountsItr.next();
			String accountFromAPI = (String) accountInfoFromAPI.get("account");
			String keyFromAPI = (String) ((Map)accountInfoFromAPI.get("key")).get("key");

			byte[] decodedKeyFromAPI = Hex.decodeHex(keyFromAPI);
			Key keyListFromAPI = Key.parseFrom(decodedKeyFromAPI);

			String accountNumber = accountFromAPI.split("\\.")[2];

			while(stateAccountsItr.hasNext()) {
				Map accountInfoFromState = (Map) stateAccountsItr.next();
				String accountFromState = String.valueOf(accountInfoFromState.get("accountNum"));

				if(accountNumber.matches(accountFromState)) {
					String keyFromState_Org = (String) accountInfoFromState.get("key");

					Key keyListFromState = Key.parseFrom(Hex.decodeHex(keyFromState_Org));

					if(keyListFromAPI.equals(keyListFromState)) {
						System.out.println("node : " + accountFromAPI + " keys Match");
					} else {
						System.out.println("node : " + accountFromAPI + " keys Do Not Match");
						System.out.println("from State : " + keyListFromState);
						System.out.println("from API : " + keyListFromAPI);
					}
				}
			}
			stateAccountsItr = accountsFromState.iterator();

			System.out.println("___________________________________________________________________");
			System.out.println("___________________________________________________________________");
			counter++;
		}
	}
}
