package com.hedera.services.legacy.bip39utils;

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



import java.math.BigInteger;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Encapsulation of a "reference", which is a 384-bit hash of a public key, file, swirld name, or
 * other entity. It supports converting between byte array and string, for several types of string.
 * Eventually, it may also allow converting between those and a QR code image. It is also legal to
 * instantiate with a 128-bit or 256-bit hash, but for some uses, 128 bits will not be secure against
 * birthday attacks, and 256 bits will not be secure against quantum computers. The US CNSA Suite requires
 * 384-bit hashes, and 256-bit keys.
 */
public class Reference {
	// data is the actual reference, either 16 or 32 bytes
	private byte[] data;
	private static final  String digits = "0123456789"
			+ "abcdefghijklmnopqrstuvwxyz" + "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */

	/**
	 * Pass to the constructor 16, 32, or 48 bytes (128, 256, or 384 bits), which is the hash of the thing
	 * being referenced (a key, a swirld name, a file, etc). A copy of data is made, so it is OK to change
	 * the array after instantiating the Reference.
	 * 
	 * The stored information will be one byte longer than the data, found by first appending a CRC8
	 * checksum to the end, then XORing all the previous bytes with that checksum. Later, the checksum can
	 * be checked by doing the XOR, then the checksum calculation, then comparing the result to the last
	 * byte. When expressing a Reference in base 62 or as a list of words, the longer version is used.
	 * 
	 * @param data
	 *            the 16 or 32 or 48 bytes constituting the reference
	 */
	public Reference( byte[] data) {
		if (data == null) {
			throw new InvalidParameterException("data should not be null");
		}
		if (data == null || (data.length != 32 && data.length != 16
				&& data.length != 48)) {
			throw new InvalidParameterException(
					"data.length() should be 16 or 32 or 48, not "
							+ data.length);
		}
		this.data = new byte[data.length + 1];
		// store in scrambled form so changing one bit of reference changes the entire string representation
		byte crc = crc8(data);
		this.data[data.length] = crc; // the crc goes at the end
		for (int i = 0; i < data.length; i++) {
			this.data[i] = (byte) (data[i] ^ crc); // the checksum also is XORed with all previous bytes
		}
	}

	/**
	 * The natural log of 2, used in the log2() function
	 */
	private final static double LOG_2 = Math.log(2);

	/**
	 * return the log base 2 of x
	 * 
	 * @param x
	 *            the number to take the log of
	 * @return the log base 2 of x
	 */
	private double log2(double x) {
		return Math.log(x) / LOG_2;
	}

	/**
	 * Pass to the constructor a string representing the data. If the string starts and ends in <> and
	 * contains only letters and digits in between, then it is considered a base62 encoding. Otherwise, it
	 * is considered a list of words.
	 * 
	 * @param dataString
	 *            the 16, 32, or 48 bytes constituting the reference
	 */
	public Reference(String dataString) {
		if (dataString.matches("^<[a-zA-Z0-9]*>$")) { // base 62 encoding
			int lenString = dataString.length() - 2; // number of characters other than <>
			int len128Bits = (int) Math.ceil((128 + 8) / log2(digits.length()));
			int len256Bits = (int) Math.ceil((256 + 8) / log2(digits.length()));
			int len384Bits = (int) Math.ceil((384 + 8) / log2(digits.length()));
			if (lenString != len128Bits && lenString != len256Bits
					&& lenString != len384Bits) {
				throw new InvalidParameterException(dataString
						+ " is not a proper encoding of 128, 256, or 384 bits because it has "
						+ lenString + " digits instead of " + len128Bits + ", "
						+ len256Bits + ", or " + len384Bits);
			}
			int arrayLen = (lenString == len128Bits) ? 16 + 1
					: (lenString == len256Bits) ? 32 + 1 : 48 + 1;
			data = new byte[arrayLen];

			int[] inDigits = new int[lenString];
			for (int i = 0; i < lenString; i++) {
				inDigits[i] = digits.indexOf(dataString.charAt(i + 1));
			}
			int[] outDigits = convertRadix(inDigits, 62, 256, arrayLen);
			data = new byte[arrayLen];
			for (int i = 0; i < arrayLen; i++) {
				data[i] = (byte) outDigits[i];
			}
		} else { // list of words
			List<String> words = Reference.lowercasedWords();
			int len128Bits = (int) Math.ceil((128 + 8) / log2(words.size()));
			int len256Bits = (int) Math.ceil((256 + 8) / log2(words.size()));
			int len384Bits = (int) Math.ceil((384 + 8) / log2(words.size()));

			List<String> allWords = Collections
					.synchronizedList(new ArrayList<String>());
			Matcher m = Pattern.compile("[a-zA-Z]+")
					.matcher(dataString.toLowerCase());
			while (m.find()) {
				allWords.add(m.group());
			}
			int[] indices = new int[allWords.size()];
			int j = 0;
			for (String word : allWords) {
				indices[j++] = words.indexOf(word);
			}

			if (allWords.size() != len128Bits
					&& allWords.size() != len256Bits) {
				throw new InvalidParameterException("there should be "
						+ len128Bits + ", " + len256Bits + ", or " + len384Bits
						+ " words, not " + allWords.size());
			}

			int dataLen = allWords.size() == len128Bits ? 16 + 1
					: allWords.size() == len256Bits ? 32 + 1 : 48 + 1;
			int[] toDigits = convertRadix(indices, words.size(), 256, dataLen);
			data = new byte[dataLen];
			for (int i = 0; i < dataLen; i++) {
				data[i] = (byte) toDigits[i];
			}
		}
		byte[] dataUnscrambled = new byte[32];
		byte crc = data[data.length - 1];
		for (int i = 0; i < data.length - 1; i++) {
			dataUnscrambled[i] = (byte) (((byte)data[i]) ^ crc);
		}
		crc = crc8(dataUnscrambled);
		if (data[data.length - 1] != crc) {
			throw new InvalidParameterException(
					"Invalid string: fails the cyclic redundency check");
		}
	}

	/**
	 * Given a number in base fromRadix, with each digit being an integer in array number (number[0] is most
	 * significant), return a new number in toRadix, where the array has toLength elements, left padded with
	 * zeros if the number is too small, and chopping off the most significant digits if it is too large.
	 * 
	 * @param number
	 *            the input number to convert
	 * @param fromRadix
	 *            the base of input number
	 * @param toRadix
	 *            the base of the returned number
	 * @param toLength
	 *            the number of digits in the returned number
	 * @return the number in the new base
	 */
	
	private int[] convertRadix(int[] number, int fromRadix, int toRadix,
			int toLength) {
		int[] result = new int[toLength];
		BigInteger num = BigInteger.valueOf(0);
		BigInteger fromR = BigInteger.valueOf(fromRadix);
		BigInteger toR = BigInteger.valueOf(toRadix);
		for (int i = 0; i < number.length; i++) { // convert number ==> BigInteger
			num = num.multiply(fromR);
			num = num.add(BigInteger.valueOf(number[i]));
		}
		for (int i = toLength - 1; i >= 0; i--) { // convert BigInteger ==> result
			BigInteger[] pair = num.divideAndRemainder(toR);
			num = pair[0];
			result[i] = pair[1].intValue();
		}
		return result;
	}

	/**
	 * calculate the checksum of all but the last byte in data. according to
	 * http://www.ece.cmu.edu/~koopman/roses/dsn04/koopman04_crc_poly_embedded.pdf Koopman says 0xA6 (1
	 * 0100110) is a good polynomial choice, which is x^8 + x^6 + x^3 + x^2 + x^1 . The following code uses
	 * 0xB2 (1 0110010), which is 0xA6 with the bits reversed (after the first bit), which is needed for
	 * this code.
	 * 
	 * @param data
	 *            the data to find checksum for (where the last byte does not affect the checksum)
	 * @return the checksum
	 */
	public static byte crc8(byte[] data) {
	    int count = data.length;
		int crc = 0xFF;
		for (int i = 0; i < data.length - 1; i++) {
			crc ^= Reference.byteToUnsignedInt(data[i]);
			for (int j = 0; j < 8; j++) {
				crc = (crc >>> 1) ^ (((crc & 1) == 0) ? 0 : 0xB2);
			}
		}
		return (byte) (crc ^ 0xFF);
	}

	/**
	 * Return the base-62 encoding, inside of <brackets>.
	 */
	
	@Override
	public String toString() {
		return to62();
	}

	/**
	 * Return this reference as an array of 16 or 32 or 48 bytes.
	 * 
	 * @return the reference as an array of 16 or 32 or 48 bytes.
	 */
	
	public byte[] toBytes() {
		byte[] result = Arrays.copyOfRange(data, 0, data.length - 1);
		byte crc = data[data.length - 1];
		for (int i = 0; i < data.length - 1; i++) { // return unscrambled data
			result[i] ^= crc;
		}
		return result;
	}

	/**
	 * Return the first few characters of the string representing the reference, encoded in base 62
	 * 
	 * @return
	 */
	
	public String to62Prefix() {
		return to62().substring(0, 6) + "...>";
	}

	/**
	 * Return a string representing the reference, encoded in base 62
	 * 
	 * @return
	 */
	
	public String to62() {
		int len = (int) Math.ceil(data.length * 8 / log2(digits.length()));
		int[] fromDigits = new int[data.length];
		int[] toDigits;
		char[] answer = new char[len];

		for (int i = 0; i < data.length; i++) {
			fromDigits[i] = Reference.byteToUnsignedInt(data[i]);
		}
		toDigits = convertRadix(fromDigits, 256, digits.length(), len);
		for (int i = 0; i < len; i++) {
			answer[i] = digits.charAt(toDigits[i]);
		}
		return "<" + (new String(answer)) + ">";
	}

	/**
	 * Return a string representing the reference, made up of multiple lines of 4 words each, broken into
	 * groups of 4 lines. The last group may have fewer lines, and its last line may have fewer words.
	 * 
	 * @return the result as one string
	 */
	
	public String toWords() {
		return toWords("+----------\n| ", " ", "\n| ", "\n| ", "\n|\n| ",
				"\n|\n| ", "\n+----------");

		// uncomment the following instead of the above, to indent every other group instead of skipping
		// a line every 4th line:
		//
		// return toWords("+----------\n| ", " ", "\n| ", "\n| ", "\n| ",
		// "\n| ", "\n+----------");
	}

	/**
	 * Return a string representing the reference, made up of multiple lines of 4 words each, broken into
	 * groups of 4 lines. The last group may have fewer lines, and its last line may have fewer words.
	 * 
	 * @indent a string inserted at the start of each line
	 * @return the result as one string
	 */
	
	public String toWords(String indent) {
		return toWords( //
				indent + "+----------\n" + indent + "| ", //
				" ", //
				"\n" + indent + "| ", //
				"\n" + indent + "| ", //
				"\n" + indent + "|\n" + indent + "| ", //
				"\n" + indent + "|\n" + indent + "| ", //
				"\n" + indent + "+----------");

		// uncomment the following instead of the above, to indent every other group instead of skipping
		// a line every 4th line:
		//
		// return toWords("+----------\n| ", " ", "\n| ", "\n| ", "\n| ",
		// "\n| ", "\n+----------");
	}

	/**
	 * Return a string representing the reference, made up of multiple lines of 4 words each, broken into
	 * groups of 3 lines. The last group may have fewer lines, and its last line may have fewer words. There
	 * are no actual line breaks unless they are included in the parameters passed in.
	 * 
	 * @param prefix
	 *            start of the entire string
	 * @param wordSeparator
	 *            between adjacent words in a line of 4 words
	 * @param lineSeparator1
	 *            between lines in a group of 3 lines, for every other group, starting with first
	 * @param lineSeparator2
	 *            between lines in a group of 3 lines, for every other group, starting with second
	 * @param groupSeparator1
	 *            between groups of 4 lines, for every other group, starting with first
	 * @param groupSeparator2
	 *            between groups of 4 lines, for every other group, starting with second
	 * @param suffix
	 *            end of the entire string
	 * @return the result as one string
	 */
	
	public String toWords(String prefix, String wordSeparator,
			String lineSeparator1, String lineSeparator2,
			String groupSeparator1, String groupSeparator2, String suffix) {
		List<String> words = this.toWordsList();
		// need len words
		String answer = "";

		for (int i = 0; i < words.size(); i++) {
			if (i == 0) {
				answer += prefix;
			} else if (i % 24 == 0) {
				answer += groupSeparator1;
			} else if (i % 12 == 0) {
				answer += groupSeparator2;
			} else if (i % 4 == 0 && i % 24 < 12) {
				answer += lineSeparator1;
			} else if (i % 4 == 0) {
				answer += lineSeparator2;
			} else {
				answer += wordSeparator;
			}
			answer += words.get(i);
		}
		return new String(answer + suffix);
	}

	
	public List<String> toWordsList(){
        List<String> words = WordList.words;
        // need len words
        int len = (int) Math.ceil(data.length * 8 / log2(words.size()));
        ArrayList<String> answer = new ArrayList<String>();

        int[] fromDigits = new int[data.length];
        for (int i = 0; i < data.length; i++) {
            fromDigits[i] = Reference.byteToUnsignedInt(data[i]);
        }
        int[] toDigits = convertRadix(fromDigits, 256, words.size(), len);

        for (int i = 0; i < len; i++) {
            answer.add(words.get(toDigits[i]));
        }
        return answer;
    }

	/**
	 * return a string representation of the given byte array in hex, with a each byte becoming two
	 * characters (including a leading zero, if necessary). Only use the array from index firstIndex to
	 * index lastIndex. A lastIndex of -1 means the last element, -2 is the second to last, and so on. If
	 * minIndex>maxIndex (after any wrapping of lastIndex), then return the null string.
	 * 
	 * @param bytes
	 *            an array of bytes, some of which are to be converted
	 * @param firstIndex
	 *            index of first element to convert
	 * @param lastIndex
	 *            index of last element to convert (or -1 for last -2 for 2nd to last, etc)
	 * @return a hex string of exactly two characters per converted byte
	 */
	
	static String toHex( byte[] bytes, int firstIndex, int lastIndex) {
		String ans = "";
		int last = lastIndex >= 0 ? lastIndex : bytes.length + lastIndex;
		if (!(0 <= firstIndex && firstIndex <= last && last < bytes.length)) {
			return "";
		}
		for (int i = firstIndex; i <= last; i++) {
			ans += String.format("%02x", bytes[i] & 255);
		}
		return ans;
	}

	public static int byteToUnsignedInt(byte b) {
		return 0x00 << 24 | b & 0xff;
	}

	public static List<String> lowercasedWords () {
		List<String> words = WordList.words;
		ArrayList<String> lowercasedWords = new ArrayList<>();
		for (int i = 0; i < words.size() ; i++) {
			lowercasedWords.add(words.get(i).toLowerCase());
		}
		return lowercasedWords;
	}

	// /**
	// * This is only used for testing this class. It should probably be deleted at some point.
	// * @param args ignored
	// */
	// private static void main(String[] args) {
	// Random rnd = new Random(123);
	// Reference ref;
	// byte[] data;
	//
	// for (int i=16; i<=32; i+=16 ) {
	// String strString, str62, strWords;
	//
	// data = new byte[i];
	// rnd.nextBytes(data);
	// ref = new Reference(data);
	// strString=ref.toString();
	// System.out.println("toString() = " + strString);
	//
	// str62=ref.to62();
	// System.out.println("to62() = " + str62);
	//
	// //str62 = str62.substring(0, 5) + "X" + str62.substring(6);
	// Reference newRef = new Reference(str62);
	// byte[] newRefBytes = newRef.toBytes();
	// String roundTrip62 = new Reference(newRefBytes).to62();
	// System.out.println("RT to62() = " + roundTrip62);
	//
	// strWords=ref.toWords(" "," ","\n","\n","");
	// System.out.println("toWords() = \n" + strWords);
	//
	//
	// //strWords = strWords.substring(0, 5) + "X" + strWords.substring(6);
	// Reference newRef2 = new Reference(strWords);
	// String strWords2 = newRef2.toWords(" "," ","\n","");
	// System.out.println("RT toWords() = \n" + strWords2);
	//
	// System.out.println();;
	// System.out.println("number of words: "+words.size());
	//
	// str62=ref.to62();
	// System.out.println("to62() = " + str62);
	// data[5] ^= 16;
	// Reference ref3 = new Reference(data);
	// System.out.println("to62() = " + ref3.to62());
	// }
	//
	// Class<?> main = null;
	// try {
	// Class.forName("com.swirlds.demos.ServerDemoMain");
	// } catch(Exception e) {
	// Platform.log(e,"");
	// }
	// System.out.println("Found class "+main);
	// }

	// {
	// System.out.println("WordList.words.size(): " + WordList.words.size());
	// }
}

class WordList {
	/** the list of 4096 words */
	
	static List<String> words = Arrays.asList("aback", "abbey", "abbot",
			"abide", "ablaze", "able", "aboard", "abode", "abort", "abound",
			"about", "above", "abroad", "abrupt", "absent", "absorb", "absurd",
			"abuse", "accent", "accept", "access", "accord", "accuse", "ace",
			"ache", "aching", "acid", "acidic", "acorn", "acre", "across",
			"act", "action", "active", "actor", "actual", "acute", "Adam",
			"adapt", "add", "added", "addict", "adept", "adhere", "adjust",
			"admire", "admit", "adobe", "adopt", "adrift", "adult", "adverb",
			"advice", "aerial", "afar", "affair", "affect", "afford", "Afghan",
			"afield", "afloat", "afraid", "afresh", "after", "again", "age",
			"agency", "agenda", "agent", "aghast", "agile", "ago", "agony",
			"agree", "agreed", "ahead", "aid", "aide", "aim", "air", "airman",
			"airy", "akin", "alarm", "Alaska", "albeit", "album", "ale",
			"alert", "alibi", "Alice", "alien", "alight", "align", "alike",
			"alive", "alkali", "all", "alley", "allied", "allow", "alloy",
			"ally", "almond", "almost", "aloft", "alone", "along", "aloof",
			"aloud", "alpha", "alpine", "also", "altar", "alter", "always",
			"amaze", "Amazon", "amber", "ambush", "amen", "amend", "amid",
			"amidst", "amiss", "among", "amount", "ample", "amuse", "anchor",
			"and", "Andrew", "anew", "angel", "anger", "angle", "angry",
			"animal", "ankle", "annoy", "annual", "answer", "anthem", "any",
			"anyhow", "anyway", "apart", "apathy", "apex", "apiece", "appeal",
			"appear", "apple", "apply", "April", "apron", "Arab", "arcade",
			"arcane", "arch", "Arctic", "ardent", "are", "area", "argue",
			"arid", "arise", "ark", "arm", "armful", "army", "aroma", "around",
			"arouse", "array", "arrest", "arrive", "arrow", "arson", "art",
			"artery", "artful", "artist", "ascent", "ash", "ashen", "ashore",
			"aside", "ask", "asleep", "aspect", "assay", "assent", "assert",
			"assess", "asset", "assign", "assist", "assume", "assure", "asthma",
			"astute", "asylum", "ate", "Athens", "atlas", "atom", "atomic",
			"attach", "attack", "attain", "attend", "attic", "auburn", "audio",
			"audit", "august", "aunt", "auntie", "aura", "Austin", "author",
			"auto", "autumn", "avail", "avenge", "avenue", "avert", "avid",
			"avoid", "await", "awake", "awaken", "award", "aware", "awash",
			"away", "awful", "awhile", "axe", "axes", "axiom", "axis", "axle",
			"aye", "babe", "baby", "Bach", "back", "backup", "bacon", "bad",
			"badge", "badly", "bag", "baggy", "bail", "bait", "bake", "baker",
			"bakery", "bald", "ball", "ballad", "ballet", "ballot", "Baltic",
			"bamboo", "ban", "banal", "banana", "band", "bang", "bank", "bar",
			"barber", "bare", "barely", "barge", "bark", "barley", "barn",
			"baron", "barrel", "barren", "basalt", "base", "basic", "basil",
			"basin", "basis", "basket", "bass", "bat", "batch", "bath", "baton",
			"battle", "bay", "beach", "beacon", "beak", "beam", "bean", "bear",
			"beard", "beast", "beat", "beauty", "become", "bed", "beech",
			"beef", "beefy", "beep", "beer", "beet", "beetle", "before", "beg",
			"beggar", "begin", "behalf", "behave", "behind", "beige", "being",
			"belief", "bell", "belly", "belong", "below", "belt", "bench",
			"bend", "benign", "bent", "Berlin", "berry", "berth", "beset",
			"beside", "best", "bestow", "bet", "beta", "betray", "better",
			"beware", "beyond", "bias", "biceps", "bicker", "bid", "big",
			"bigger", "bike", "bile", "Bill", "bin", "binary", "bind", "biopsy",
			"birch", "bird", "birdie", "birth", "bishop", "bit", "bitch",
			"bite", "bitter", "black", "blade", "blame", "bland", "blast",
			"blaze", "bleak", "blend", "bless", "blew", "blind", "blink",
			"blip", "bliss", "blitz", "block", "blond", "blood", "bloody",
			"bloom", "blot", "blouse", "blow", "blue", "bluff", "blunt", "blur",
			"blush", "boar", "board", "boast", "boat", "Bob", "bodily", "body",
			"bogus", "boil", "bold", "bolt", "bomb", "Bombay", "bond", "bone",
			"Bonn", "bonnet", "bonus", "bony", "book", "boom", "boost", "boot",
			"booth", "booze", "border", "bore", "borrow", "bosom", "boss",
			"Boston", "both", "bother", "bottle", "bottom", "bought", "bounce",
			"bound", "bounty", "bout", "bovine", "bow", "bowel", "bowl", "box",
			"boy", "boyish", "brace", "brain", "brainy", "brake", "bran",
			"branch", "brand", "brandy", "brass", "brave", "bravo", "Brazil",
			"breach", "bread", "break", "breast", "breath", "bred", "breed",
			"breeze", "brew", "bribe", "brick", "bride", "bridge", "brief",
			"bright", "brim", "brine", "bring", "brink", "brisk", "broad",
			"broke", "broken", "bronze", "brook", "broom", "brown", "bruise",
			"brush", "brutal", "brute", "bubble", "buck", "bucket", "buckle",
			"budget", "buffet", "buggy", "build", "bulb", "bulge", "bulk",
			"bulky", "bull", "bullet", "bully", "bump", "bumpy", "bunch",
			"bundle", "bunk", "bunny", "burden", "bureau", "burial", "buried",
			"burly", "burn", "burnt", "burrow", "burst", "bury", "bus", "bush",
			"bust", "bustle", "busy", "but", "butler", "butt", "butter",
			"button", "buy", "buyer", "buzz", "bye", "byte", "cab", "cabin",
			"cable", "cache", "cactus", "Caesar", "cage", "Cairo", "Cajun",
			"cajole", "cake", "calf", "call", "caller", "calm", "calmly",
			"came", "camel", "camera", "camp", "campus", "can", "Canada",
			"canal", "canary", "cancel", "cancer", "candid", "candle", "candy",
			"cane", "canine", "canoe", "canopy", "canvas", "canyon", "cap",
			"cape", "car", "carbon", "card", "care", "career", "caress",
			"cargo", "Carl", "carnal", "Carol", "carp", "carpet", "carrot",
			"carry", "cart", "cartel", "case", "cash", "cask", "cast", "castle",
			"casual", "cat", "catch", "cater", "cattle", "caught", "causal",
			"cause", "cave", "cease", "celery", "cell", "cellar", "Celtic",
			"cement", "censor", "census", "cent", "cereal", "chain", "chair",
			"chalk", "chalky", "champ", "chance", "change", "chant", "chaos",
			"chap", "chapel", "charge", "charm", "chart", "chase", "chat",
			"cheap", "cheat", "check", "cheek", "cheeky", "cheer", "cheery",
			"cheese", "chef", "cheque", "cherry", "chess", "chest", "chew",
			"chic", "chick", "chief", "child", "Chile", "chill", "chilly",
			"chin", "China", "chip", "choice", "choir", "choose", "chop",
			"choppy", "chord", "chorus", "chose", "chosen", "Chris", "chrome",
			"chunk", "chunky", "church", "cider", "cigar", "cinema", "circa",
			"circle", "circus", "cite", "city", "civic", "civil", "clad",
			"claim", "clammy", "clan", "clap", "clash", "clasp", "class",
			"clause", "claw", "clay", "clean", "clear", "clergy", "clerk",
			"clever", "click", "client", "cliff", "climax", "climb", "clinch",
			"cling", "clinic", "clip", "cloak", "clock", "clone", "close",
			"closer", "closet", "cloth", "cloud", "cloudy", "clout", "clown",
			"club", "clue", "clumsy", "clung", "clutch", "coach", "coal",
			"coarse", "coast", "coat", "coax", "cobalt", "cobra", "coca",
			"cock", "cocoa", "code", "coffee", "coffin", "cohort", "coil",
			"coin", "coke", "cold", "collar", "colon", "colony", "colt",
			"column", "comb", "combat", "come", "comedy", "comic", "commit",
			"common", "compel", "comply", "concur", "cone", "confer", "Congo",
			"consul", "convex", "convey", "convoy", "cook", "cool", "cope",
			"copper", "copy", "coral", "cord", "core", "cork", "corn", "corner",
			"corps", "corpse", "corpus", "cortex", "cosmic", "cosmos", "cost",
			"costly", "cotton", "couch", "cough", "could", "count", "county",
			"coup", "couple", "coupon", "course", "court", "cousin", "cove",
			"cover", "covert", "cow", "coward", "cowboy", "cozy", "crab",
			"crack", "cradle", "craft", "crafty", "crag", "crane", "crash",
			"crate", "crater", "crawl", "crazy", "creak", "cream", "creamy",
			"create", "credit", "creed", "creek", "creep", "creepy", "crept",
			"crest", "crew", "cried", "crime", "crisis", "crisp", "critic",
			"crook", "crop", "cross", "crow", "crowd", "crown", "crude",
			"cruel", "cruise", "crunch", "crush", "crust", "crux", "cry",
			"crypt", "Cuba", "cube", "cubic", "cuckoo", "cuff", "cult", "cup",
			"curb", "cure", "curfew", "curl", "curry", "curse", "cursor",
			"curve", "cuss", "custom", "cut", "cute", "cycle", "cyclic",
			"cynic", "Czech", "dad", "daddy", "dagger", "daily", "dairy",
			"daisy", "dale", "dam", "damage", "damp", "dampen", "dance",
			"danger", "Danish", "dare", "dark", "darken", "darn", "dart",
			"dash", "data", "date", "David", "dawn", "day", "dead", "deadly",
			"deaf", "deal", "dealer", "dean", "dear", "death", "debate",
			"debit", "debris", "debt", "debtor", "decade", "decay", "decent",
			"decide", "deck", "decor", "decree", "deduce", "deed", "deep",
			"deeply", "deer", "defeat", "defect", "defend", "defer", "define",
			"defy", "degree", "deity", "delay", "delete", "Delhi", "delta",
			"demand", "demise", "demo", "demure", "denial", "denote", "dense",
			"dental", "deny", "depart", "depend", "depict", "deploy", "depot",
			"depth", "deputy", "derive", "desert", "design", "desire", "desist",
			"desk", "detail", "detect", "deter", "detest", "detour", "device",
			"devise", "devoid", "devote", "devour", "dial", "Diana", "diary",
			"dice", "dictum", "did", "die", "diesel", "diet", "differ", "dig",
			"digest", "digit", "dine", "dinghy", "dinner", "diode", "dip",
			"dire", "direct", "dirt", "dirty", "disc", "disco", "dish", "disk",
			"dismal", "dispel", "ditch", "dive", "divert", "divide", "divine",
			"dizzy", "docile", "dock", "doctor", "dog", "dogma", "dole", "doll",
			"dollar", "dolly", "domain", "dome", "domino", "donate", "done",
			"donkey", "donor", "doom", "door", "dorsal", "dose", "dot",
			"double", "doubt", "dough", "dour", "dove", "down", "dozen",
			"draft", "drag", "dragon", "drain", "drama", "drank", "draw",
			"drawer", "dread", "dream", "dreary", "dress", "drew", "dried",
			"drift", "drill", "drink", "drip", "drive", "driver", "drop",
			"drove", "drown", "drug", "drum", "drunk", "dry", "dual", "duck",
			"duct", "due", "duel", "duet", "duke", "dull", "duly", "dumb",
			"dummy", "dump", "dune", "dung", "duress", "during", "dusk", "dust",
			"dusty", "Dutch", "duty", "dwarf", "dwell", "dyer", "dying",
			"dynamo", "each", "eager", "eagle", "ear", "earl", "early", "earn",
			"earth", "ease", "easel", "easily", "East", "Easter", "easy", "eat",
			"eaten", "eater", "echo", "eddy", "Eden", "edge", "edible", "edict",
			"edit", "editor", "eel", "eerie", "eerily", "effect", "effort",
			"egg", "ego", "eight", "eighth", "eighty", "either", "elbow",
			"elder", "eldest", "elect", "eleven", "elicit", "elite", "else",
			"elude", "elves", "embark", "emblem", "embryo", "emerge", "emit",
			"empire", "employ", "empty", "enable", "enamel", "end", "endure",
			"enemy", "energy", "engage", "engine", "enjoy", "enlist", "enough",
			"ensure", "entail", "enter", "entire", "entry", "envoy", "envy",
			"enzyme", "epic", "epoch", "equal", "equate", "equip", "equity",
			"era", "erect", "Eric", "erode", "erotic", "errant", "error",
			"escape", "escort", "essay", "Essex", "estate", "esteem", "ethic",
			"ethnic", "Europe", "evade", "Eve", "even", "event", "ever",
			"every", "evict", "evil", "evoke", "evolve", "exact", "exam",
			"exceed", "excel", "except", "excess", "excise", "excite", "excuse",
			"exempt", "exert", "exile", "exist", "exit", "Exodus", "exotic",
			"expand", "expect", "expert", "expire", "export", "expose",
			"extend", "extra", "eye", "eyed", "fabric", "face", "facial",
			"fact", "factor", "fade", "fail", "faint", "fair", "fairly",
			"fairy", "faith", "fake", "falcon", "fall", "false", "falter",
			"fame", "family", "famine", "famous", "fan", "fancy", "far",
			"farce", "fare", "farm", "farmer", "fast", "fasten", "faster",
			"fat", "fatal", "fate", "father", "fatty", "fault", "faulty",
			"fauna", "fear", "feast", "feat", "fed", "fee", "feeble", "feed",
			"feel", "feet", "fell", "fellow", "felt", "female", "fence", "fend",
			"ferry", "fetal", "fetch", "feudal", "fever", "few", "fewer",
			"fiasco", "fiddle", "field", "fiend", "fierce", "fiery", "fifth",
			"fifty", "fig", "fight", "figure", "file", "fill", "filled",
			"filler", "film", "filter", "filth", "filthy", "final", "finale",
			"find", "fine", "finery", "finger", "finish", "finite", "fire",
			"firm", "firmly", "first", "fiscal", "fish", "fisher", "fist",
			"fit", "fitful", "five", "fix", "flag", "flair", "flak", "flame",
			"flank", "flap", "flare", "flash", "flask", "flat", "flavor",
			"flaw", "fled", "flee", "fleece", "fleet", "flesh", "fleshy",
			"flew", "flick", "flight", "flimsy", "flint", "flirt", "float",
			"flock", "flood", "floor", "floppy", "flora", "floral", "flour",
			"flow", "flower", "fluent", "fluffy", "fluid", "flung", "flurry",
			"flush", "flute", "flux", "fly", "flyer", "foal", "foam", "focal",
			"focus", "fog", "foil", "fold", "folk", "follow", "folly", "fond",
			"fondly", "font", "food", "fool", "foot", "for", "forbid", "force",
			"ford", "forest", "forge", "forget", "fork", "form", "formal",
			"format", "former", "fort", "forth", "forty", "forum", "fossil",
			"foster", "foul", "found", "four", "fourth", "fox", "foyer",
			"frail", "frame", "franc", "France", "frank", "fraud", "Fred",
			"free", "freed", "freely", "freeze", "French", "frenzy", "fresh",
			"friar", "Friday", "fridge", "fried", "friend", "fright", "fringe",
			"frock", "frog", "from", "front", "frost", "frosty", "frown",
			"frozen", "frugal", "fruit", "fry", "fudge", "fuel", "full",
			"fully", "fumes", "fun", "fund", "funny", "fur", "furry", "fury",
			"fuse", "fusion", "fuss", "fussy", "futile", "future", "fuzzy",
			"gadget", "gain", "gala", "galaxy", "gale", "gall", "galley",
			"gallon", "gallop", "gamble", "game", "gamma", "Gandhi", "gang",
			"gap", "garage", "garden", "garlic", "gas", "gasp", "gate",
			"gather", "gauge", "gaunt", "gave", "gaze", "gear", "geese", "gem",
			"Gemini", "gender", "gene", "Geneva", "genial", "genius", "genre",
			"gentle", "gently", "gentry", "genus", "George", "germ", "get",
			"ghetto", "ghost", "giant", "gift", "giggle", "gill", "gilt",
			"ginger", "girl", "give", "given", "glad", "glade", "glance",
			"gland", "glare", "glass", "glassy", "gleam", "glee", "glide",
			"global", "globe", "gloom", "gloomy", "Gloria", "glory", "gloss",
			"glossy", "glove", "glow", "glue", "gnat", "gnu", "goal", "goat",
			"gold", "golden", "golf", "gone", "gong", "goo", "good", "goose",
			"gore", "gorge", "gory", "gosh", "gospel", "gossip", "got",
			"Gothic", "govern", "gown", "grab", "grace", "grade", "grail",
			"grain", "grand", "grant", "grape", "graph", "grasp", "grass",
			"grassy", "grate", "grave", "gravel", "gravy", "grease", "greasy",
			"great", "Greece", "greed", "greedy", "Greek", "green", "greet",
			"grew", "grey", "grid", "grief", "grill", "grim", "grin", "grind",
			"grip", "grit", "gritty", "groan", "groin", "groom", "groove",
			"gross", "ground", "group", "grove", "grow", "grown", "growth",
			"grudge", "grunt", "guard", "guess", "guest", "guide", "guild",
			"guilt", "guilty", "guise", "guitar", "gulf", "gully", "gun",
			"gunman", "guru", "gut", "guy", "gypsy", "habit", "hack", "had",
			"hail", "hair", "hairy", "Haiti", "hale", "half", "hall", "halt",
			"hamlet", "hammer", "hand", "handle", "handy", "hang", "hangar",
			"Hanoi", "happen", "happy", "harass", "harbor", "hard", "harder",
			"hardly", "hare", "harem", "harm", "harp", "Harry", "harsh", "has",
			"hash", "hassle", "haste", "hasten", "hasty", "hat", "hatch",
			"hate", "haul", "haunt", "Havana", "have", "haven", "havoc",
			"Hawaii", "hawk", "hay", "hazard", "haze", "hazel", "hazy", "head",
			"heal", "health", "heap", "hear", "heard", "heart", "hearth",
			"hearty", "heat", "heater", "heaven", "heavy", "Hebrew", "heck",
			"hectic", "hedge", "heel", "hefty", "height", "heir", "held",
			"helium", "helix", "hell", "hello", "helm", "helmet", "help",
			"hemp", "hence", "Henry", "her", "herald", "herb", "herd", "here",
			"hereby", "Hermes", "hernia", "hero", "heroic", "heroin", "hey",
			"heyday", "hick", "hidden", "hide", "high", "higher", "highly",
			"hill", "him", "hind", "hinder", "hint", "hippie", "hire", "his",
			"hiss", "hit", "hive", "hoard", "hoarse", "hobby", "hockey", "hold",
			"holder", "hole", "hollow", "holly", "holy", "home", "honest",
			"honey", "hood", "hook", "hope", "horn", "horrid", "horror",
			"horse", "hose", "host", "hot", "hotel", "hound", "hour", "house",
			"hover", "how", "huge", "hull", "human", "humane", "humble",
			"humid", "hung", "hunger", "hungry", "hunt", "hurdle", "hurl",
			"hurry", "hurt", "hush", "hut", "hybrid", "hymn", "hyphen", "ice",
			"icing", "icon", "Idaho", "idea", "ideal", "idiom", "idiot", "idle",
			"idly", "idol", "ignite", "ignore", "ill", "image", "immune",
			"impact", "imply", "import", "impose", "Inca", "incest", "inch",
			"income", "incur", "indeed", "index", "India", "Indian", "indoor",
			"induce", "inept", "inert", "infant", "infect", "infer", "influx",
			"inform", "inject", "injure", "injury", "ink", "inlaid", "inland",
			"inlet", "inmate", "inn", "innate", "inner", "input", "insane",
			"insect", "insert", "inset", "inside", "insist", "insult", "insure",
			"intact", "intake", "intend", "inter", "into", "invade", "invent",
			"invest", "invite", "invoke", "inward", "Iowa", "Iran", "Iraq",
			"Irish", "iron", "ironic", "irony", "Isaac", "Isabel", "island",
			"isle", "Israel", "issue", "Italy", "itch", "item", "itself",
			"Ivan", "ivory", "Jack", "jacket", "Jacob", "jade", "jaguar",
			"jail", "James", "Jane", "Japan", "jargon", "Java", "jaw", "jazz",
			"jeep", "jelly", "jerky", "jest", "jet", "jewel", "Jewish", "Jim",
			"job", "jock", "jockey", "Joe", "John", "join", "joint", "joke",
			"jolly", "jolt", "Jordan", "Joseph", "joy", "joyful", "joyous",
			"judge", "Judy", "juice", "juicy", "July", "jumble", "jumbo",
			"jump", "June", "jungle", "junior", "junk", "junta", "jury", "just",
			"Kansas", "karate", "Karl", "keel", "keen", "keep", "keeper",
			"Kenya", "kept", "kernel", "kettle", "key", "khaki", "kick", "kid",
			"kidnap", "kidney", "kill", "killer", "kin", "kind", "kindly",
			"king", "kiss", "kite", "kitten", "knack", "knee", "kneel", "knew",
			"knife", "knight", "knit", "knob", "knock", "knot", "know", "known",
			"Koran", "Korea", "Kuwait", "label", "lace", "lack", "lad",
			"ladder", "laden", "lady", "lagoon", "laity", "lake", "lamb",
			"lame", "lamp", "lance", "land", "lane", "lap", "lapse", "large",
			"larval", "laser", "last", "latch", "late", "lately", "latent",
			"later", "latest", "Latin", "latter", "laugh", "launch", "lava",
			"lavish", "law", "lawful", "lawn", "lawyer", "lay", "layer",
			"layman", "lazy", "lead", "leader", "leaf", "leafy", "league",
			"leak", "leaky", "lean", "leap", "learn", "lease", "leash", "least",
			"leave", "led", "ledge", "left", "leg", "legacy", "legal", "legend",
			"legion", "lemon", "lend", "length", "lens", "lent", "Leo", "leper",
			"lesion", "less", "lessen", "lesser", "lesson", "lest", "let",
			"lethal", "letter", "level", "lever", "levy", "Lewis", "liable",
			"liar", "libel", "Libya", "lice", "lick", "lid", "lie", "lied",
			"lier", "life", "lift", "light", "like", "likely", "limb", "lime",
			"limit", "limp", "line", "linear", "linen", "linger", "link",
			"lint", "lion", "lip", "liquid", "liquor", "list", "listen", "lit",
			"live", "lively", "liver", "Liz", "lizard", "load", "loaf", "loan",
			"lobby", "lobe", "local", "locate", "lock", "locus", "lodge",
			"loft", "lofty", "log", "logic", "logo", "London", "lone", "lonely",
			"long", "longer", "look", "loop", "loose", "loosen", "loot", "lord",
			"lorry", "lose", "loss", "lost", "lot", "lotion", "lotus", "loud",
			"loudly", "lounge", "lousy", "love", "lovely", "lover", "low",
			"lower", "lowest", "loyal", "lucid", "luck", "lucky", "Lucy",
			"lull", "lump", "lumpy", "lunacy", "lunar", "lunch", "lung", "lure",
			"lurid", "lush", "lust", "lute", "Luther", "luxury", "lying",
			"lymph", "lynch", "lyric", "macho", "macro", "mad", "Madam", "made",
			"Mafia", "magic", "magma", "magnet", "magnum", "magpie", "maid",
			"maiden", "mail", "main", "mainly", "major", "make", "maker",
			"male", "malice", "mall", "malt", "mammal", "manage", "mane",
			"mania", "manic", "manner", "manor", "mantle", "manual", "manure",
			"many", "map", "maple", "marble", "march", "mare", "margin",
			"Maria", "marina", "mark", "market", "marry", "Mars", "marsh",
			"martin", "martyr", "Mary", "mask", "mason", "mass", "mast",
			"master", "mat", "match", "mate", "matrix", "matter", "mature",
			"maxim", "may", "maybe", "mayor", "maze", "mead", "meadow", "meal",
			"mean", "meant", "meat", "medal", "media", "median", "medic",
			"medium", "meet", "mellow", "melody", "melon", "melt", "member",
			"memo", "memory", "menace", "mend", "mental", "mentor", "menu",
			"mercy", "mere", "merely", "merge", "merger", "merit", "merry",
			"mesh", "mess", "messy", "met", "metal", "meter", "method",
			"methyl", "metric", "metro", "Mexico", "Miami", "Mickey", "mid",
			"midday", "middle", "midst", "midway", "might", "mighty", "mild",
			"mildew", "mile", "milk", "milky", "mill", "mimic", "mince", "mind",
			"mine", "mini", "mink", "minor", "mint", "minus", "minute", "mire",
			"mirror", "mirth", "misery", "miss", "mist", "misty", "mite", "mix",
			"moan", "moat", "mob", "mobile", "mock", "mode", "model", "modem",
			"modern", "modest", "modify", "module", "moist", "molar", "mold",
			"mole", "molten", "moment", "Monday", "money", "monk", "monkey",
			"month", "mood", "moody", "moon", "moor", "moral", "morale",
			"morbid", "more", "morgue", "mortal", "mortar", "mosaic", "Moscow",
			"Moses", "mosque", "moss", "most", "mostly", "moth", "mother",
			"motion", "motive", "motor", "mount", "mourn", "mouse", "mouth",
			"move", "movie", "Mrs", "much", "muck", "mucus", "mud", "muddle",
			"muddy", "mule", "mummy", "Munich", "murder", "murky", "murmur",
			"muscle", "museum", "music", "mussel", "must", "mutant", "mute",
			"mutiny", "mutter", "mutton", "mutual", "muzzle", "myopic",
			"myriad", "myself", "mystic", "myth", "nadir", "nail", "naked",
			"name", "namely", "nape", "napkin", "Naples", "narrow", "nasal",
			"nasty", "Nathan", "nation", "native", "nature", "nausea", "naval",
			"nave", "navy", "near", "nearer", "nearly", "neat", "neatly",
			"neck", "need", "needle", "needy", "negate", "neon", "Nepal",
			"nephew", "nerve", "nest", "net", "neural", "never", "newly",
			"next", "nice", "nicely", "niche", "nickel", "niece", "night",
			"Nile", "nimble", "nine", "ninety", "ninth", "Nobel", "noble",
			"nobody", "node", "noise", "noisy", "none", "noon", "nor", "norm",
			"normal", "North", "Norway", "nose", "nosy", "not", "note",
			"notice", "notify", "notion", "noun", "novel", "novice", "now",
			"nozzle", "null", "numb", "number", "nurse", "nut", "nylon",
			"nymph", "oak", "oar", "oasis", "oath", "obese", "obey", "object",
			"oblige", "oboe", "obtain", "obtuse", "occult", "occupy", "occur",
			"ocean", "octave", "odd", "off", "offend", "offer", "office",
			"offset", "often", "Ohio", "oil", "oily", "okay", "old", "older",
			"oldest", "olive", "omega", "omen", "omit", "once", "one", "onion",
			"only", "onset", "onto", "onus", "onward", "opaque", "open",
			"openly", "opera", "opium", "oppose", "optic", "option", "oracle",
			"oral", "orange", "orbit", "orchid", "ordeal", "order", "organ",
			"orgasm", "orient", "origin", "ornate", "orphan", "Oscar", "other",
			"otter", "ought", "ounce", "our", "out", "outer", "output",
			"outset", "oval", "oven", "over", "overt", "owe", "owing", "owl",
			"own", "owner", "Oxford", "oxide", "oxygen", "oyster", "ozone",
			"pace", "pack", "packet", "pact", "pad", "paddle", "paddy", "pagan",
			"page", "paid", "pain", "paint", "pair", "palace", "pale", "palm",
			"pan", "Panama", "panel", "panic", "papa", "papal", "paper",
			"parade", "parcel", "pardon", "parent", "Paris", "parish", "park",
			"parody", "parrot", "part", "partly", "party", "Pascal", "pass",
			"past", "paste", "pastel", "pastor", "pastry", "pat", "patch",
			"patent", "path", "patio", "patrol", "patron", "Paul", "pause",
			"pave", "paw", "pawn", "pay", "peace", "peach", "peak", "pear",
			"pearl", "pedal", "peel", "peer", "Peking", "pelvic", "pelvis",
			"pen", "penal", "pence", "pencil", "penny", "people", "pepper",
			"per", "perch", "peril", "period", "perish", "permit", "person",
			"Peru", "pest", "pet", "Peter", "petite", "petrol", "petty",
			"phase", "Philip", "phone", "photo", "phrase", "piano", "pick",
			"picket", "picnic", "pie", "piece", "pier", "pierce", "piety",
			"pig", "pigeon", "piggy", "pike", "pile", "pill", "pillar",
			"pillow", "pilot", "pin", "pinch", "pine", "pink", "pint", "pious",
			"pipe", "pirate", "piss", "pistol", "piston", "pit", "pitch",
			"pity", "pivot", "pixel", "pizza", "place", "placid", "plague",
			"plain", "plan", "plane", "planet", "plank", "plant", "plasma",
			"plate", "play", "player", "plea", "plead", "please", "pledge",
			"plenty", "plight", "plot", "plough", "ploy", "plug", "plum",
			"plump", "plunge", "plural", "plus", "plush", "pocket", "poem",
			"poet", "poetic", "poetry", "point", "poison", "Poland", "polar",
			"pole", "police", "policy", "Polish", "polite", "poll", "pollen",
			"polo", "pond", "ponder", "pony", "pool", "poor", "poorly", "pop",
			"poppy", "pore", "pork", "port", "portal", "pose", "posh", "post",
			"postal", "pot", "potato", "potent", "pouch", "pound", "pour",
			"powder", "power", "praise", "pray", "prayer", "preach", "prefer",
			"prefix", "press", "pretty", "price", "pride", "priest", "primal",
			"prime", "prince", "print", "prior", "prism", "prison", "privy",
			"prize", "probe", "profit", "prompt", "prone", "proof", "propel",
			"proper", "prose", "proton", "proud", "prove", "proven", "proxy",
			"prune", "pry", "psalm", "pseudo", "psyche", "pub", "public",
			"puff", "pull", "pulp", "pulpit", "pulsar", "pulse", "pump",
			"punch", "punish", "punk", "pupil", "puppet", "puppy", "pure",
			"purely", "purge", "purify", "purple", "purse", "pursue", "push",
			"pushy", "put", "putt", "puzzle", "quaint", "quake", "quarry",
			"quart", "quartz", "Quebec", "queen", "queer", "query", "quest",
			"queue", "quick", "quid", "quiet", "quilt", "quirk", "quit",
			"quite", "quiver", "quiz", "quota", "quote", "rabbit", "race",
			"racial", "racism", "rack", "racket", "radar", "radio", "radish",
			"radius", "raffle", "raft", "rage", "raid", "rail", "rain", "rainy",
			"raise", "rake", "rally", "ramp", "random", "range", "rank",
			"ransom", "rape", "rapid", "rare", "rarely", "rarity", "rash",
			"rat", "rate", "rather", "ratify", "ratio", "rattle", "rave",
			"raven", "raw", "ray", "razor", "reach", "react", "read", "reader",
			"ready", "real", "really", "realm", "reap", "rear", "reason",
			"rebel", "recall", "recent", "recess", "recipe", "reckon", "record",
			"recoup", "rector", "red", "redeem", "redo", "reduce", "reed",
			"reef", "reek", "refer", "reform", "refuge", "refuse", "regal",
			"regard", "regent", "regime", "region", "regret", "reign", "reject",
			"relate", "relax", "relay", "relic", "relief", "relish", "rely",
			"remain", "remark", "remedy", "remind", "remit", "remote", "remove",
			"renal", "render", "rent", "rental", "repair", "repeal", "repeat",
			"repent", "reply", "report", "rescue", "resent", "reside", "resign",
			"resin", "resist", "resort", "rest", "result", "resume", "retail",
			"retain", "retina", "retire", "return", "reveal", "review",
			"revise", "revive", "revolt", "reward", "Rex", "Rhine", "rhino",
			"rhyme", "rhythm", "ribbon", "rice", "rich", "Rick", "rid", "ride",
			"rider", "ridge", "rife", "rifle", "rift", "right", "rigid", "rile",
			"rim", "ring", "rinse", "riot", "ripe", "ripen", "ripple", "rise",
			"risk", "risky", "rite", "ritual", "Ritz", "rival", "river", "road",
			"roar", "roast", "rob", "robe", "Robert", "robin", "robot",
			"robust", "rock", "rocket", "rocky", "rod", "rode", "rodent",
			"rogue", "role", "roll", "Roman", "Rome", "roof", "room", "root",
			"rope", "rose", "rosy", "rot", "rotate", "rotor", "rotten", "rouge",
			"rough", "round", "route", "rover", "row", "royal", "rub", "rubber",
			"rubble", "ruby", "rudder", "rude", "rug", "rugby", "ruin", "rule",
			"ruler", "rumble", "rump", "run", "rune", "rung", "runway", "rural",
			"rush", "Russia", "rust", "rustic", "rusty", "sack", "sacred",
			"sad", "saddle", "sadism", "sadly", "safari", "safe", "safely",
			"safer", "safety", "saga", "sage", "Sahara", "said", "sail",
			"sailor", "saint", "sake", "salad", "salary", "sale", "saline",
			"saliva", "salmon", "saloon", "salt", "salty", "salute", "Sam",
			"same", "sample", "sand", "sandy", "sane", "sash", "satin",
			"satire", "Saturn", "sauce", "saucer", "Saudi", "sauna", "savage",
			"save", "saw", "say", "scale", "scalp", "scan", "scant", "scar",
			"scarce", "scare", "scarf", "scary", "scene", "scenic", "scent",
			"school", "scold", "scope", "score", "scorn", "scotch", "Scott",
			"scout", "scrap", "scrape", "scream", "screen", "screw", "script",
			"scroll", "scrub", "scum", "sea", "seal", "seam", "seaman",
			"search", "season", "seat", "second", "secret", "sect", "sector",
			"secure", "see", "seed", "seeing", "seek", "seem", "seize",
			"seldom", "select", "self", "sell", "seller", "semi", "senate",
			"send", "senile", "senior", "sense", "sensor", "sent", "sentry",
			"Seoul", "sequel", "serene", "serial", "series", "sermon", "serum",
			"serve", "server", "set", "settle", "seven", "severe", "sew",
			"sewage", "shabby", "shade", "shadow", "shady", "shaft", "shaggy",
			"shah", "shake", "shaky", "shall", "sham", "shame", "shape",
			"share", "shark", "sharp", "shawl", "she", "shear", "sheen",
			"sheep", "sheer", "sheet", "shelf", "shell", "sherry", "shield",
			"shift", "shine", "shiny", "ship", "shire", "shirk", "shirt",
			"shiver", "shock", "shoe", "shook", "shoot", "shop", "shore",
			"short", "shot", "should", "shout", "show", "shower", "shrank",
			"shrewd", "shrill", "shrimp", "shrine", "shrink", "shrub", "shrug",
			"shut", "shy", "shyly", "sick", "side", "siege", "sigh", "sight",
			"sigma", "sign", "signal", "silent", "silk", "silken", "silky",
			"sill", "silly", "silo", "silver", "simple", "simply", "since",
			"sinful", "sing", "singer", "single", "sink", "sir", "sire",
			"siren", "sister", "sit", "site", "sitter", "six", "sixth", "sixty",
			"size", "sketch", "skill", "skin", "skinny", "skip", "skirt",
			"skull", "sky", "slab", "slack", "slain", "slam", "slang", "slap",
			"slat", "slate", "slave", "sleek", "sleep", "sleepy", "sleeve",
			"slice", "slick", "slid", "slide", "slight", "slim", "slimy",
			"sling", "slip", "slit", "slogan", "slope", "sloppy", "slot",
			"slow", "slowly", "slug", "slum", "slump", "smack", "small",
			"smart", "smash", "smear", "smell", "smelly", "smelt", "smile",
			"smite", "smoke", "smoky", "smooth", "smug", "snack", "snail",
			"snake", "snap", "snatch", "sneak", "snow", "snowy", "snug", "soak",
			"soap", "sober", "soccer", "social", "sock", "socket", "socks",
			"soda", "sodden", "sodium", "sofa", "soft", "soften", "softly",
			"soggy", "soil", "solar", "sold", "sole", "solely", "solemn",
			"solid", "solo", "solve", "some", "son", "sonar", "sonata", "song",
			"sonic", "Sony", "soon", "sooner", "soot", "soothe", "sordid",
			"sore", "sorrow", "sorry", "sort", "soul", "sound", "soup", "sour",
			"source", "Soviet", "sow", "space", "spade", "Spain", "span",
			"spare", "spark", "sparse", "spasm", "spat", "spate", "speak",
			"spear", "speech", "speed", "speedy", "spell", "spend", "sphere",
			"spice", "spicy", "spider", "spiky", "spill", "spin", "spinal",
			"spine", "spiral", "spirit", "spit", "spite", "splash", "split",
			"spoil", "spoke", "sponge", "spoon", "sport", "spot", "spouse",
			"spray", "spread", "spree", "spring", "sprint", "spur", "squad",
			"square", "squash", "squat", "squid", "stab", "stable", "stack",
			"staff", "stage", "stain", "stair", "stairs", "stake", "stale",
			"stall", "stamp", "stance", "stand", "staple", "star", "starch",
			"stare", "stark", "start", "starve", "state", "static", "statue",
			"status", "stay", "stead", "steady", "steak", "steal", "steam",
			"steel", "steep", "steer", "stem", "stench", "step", "stereo",
			"stern", "stew", "stick", "sticky", "stiff", "stifle", "stigma",
			"still", "sting", "stint", "stir", "stitch", "stock", "stocky",
			"stone", "stony", "stool", "stop", "store", "storm", "stormy",
			"story", "stout", "stove", "stow", "strain", "strait", "strand",
			"strap", "strata", "straw", "stray", "streak", "stream", "street",
			"stress", "strict", "stride", "strife", "strike", "string", "strip",
			"stripe", "strive", "stroke", "stroll", "strong", "stud", "studio",
			"study", "stuff", "stuffy", "stunt", "stupid", "sturdy", "style",
			"submit", "subtle", "subtly", "suburb", "such", "sudden", "sue",
			"Suez", "suffer", "sugar", "suit", "suite", "suitor", "sullen",
			"sultan", "sum", "summer", "summit", "summon", "sun", "Sunday",
			"sunny", "sunset", "super", "superb", "supper", "supple", "supply",
			"sure", "surely", "surf", "surge", "survey", "suture", "swamp",
			"swan", "swap", "swarm", "sway", "swear", "sweat", "sweaty",
			"Sweden", "sweep", "sweet", "swell", "swift", "swim", "swine",
			"swing", "swirl", "Swiss", "switch", "sword", "swore", "Sydney",
			"symbol", "synod", "syntax", "Syria", "syrup", "system", "table",
			"tablet", "taboo", "tacit", "tackle", "tact", "tactic", "tail",
			"tailor", "Taiwan", "take", "tale", "talent", "talk", "tall",
			"tally", "tame", "Tampa", "tan", "tandem", "tangle", "tank", "tap",
			"tape", "target", "tariff", "tarp", "tart", "Tarzan", "task",
			"taste", "tasty", "tattoo", "Taurus", "taut", "tavern", "tax",
			"taxi", "tea", "teach", "teak", "team", "tear", "tease", "tech",
			"teeth", "tell", "temper", "temple", "tempo", "tempt", "ten",
			"tenant", "tend", "tender", "tendon", "tennis", "tenor", "tense",
			"tent", "tenth", "tenure", "Teresa", "term", "terror", "terse",
			"test", "Texas", "text", "thank", "thaw", "them", "theme", "thence",
			"theory", "there", "these", "thesis", "they", "thick", "thief",
			"thigh", "thin", "thing", "think", "third", "thirst", "thirty",
			"this", "Thomas", "thorn", "those", "though", "thread", "threat",
			"three", "thrill", "thrive", "throat", "throne", "throng", "throw",
			"thrust", "thud", "thug", "thumb", "thus", "thyme", "Tibet", "tick",
			"ticket", "tidal", "tide", "tidy", "tie", "tier", "tiger", "tight",
			"tile", "till", "tilt", "timber", "time", "timid", "tin", "tiny",
			"tip", "tire", "tissue", "title", "toad", "toast", "today", "toe",
			"toilet", "token", "Tokyo", "told", "toll", "Tom", "tomato", "tomb",
			"tonal", "tone", "tongue", "tonic", "too", "took", "tool", "tooth",
			"top", "topaz", "topic", "torch", "torque", "torso", "tort", "toss",
			"total", "touch", "tough", "tour", "toward", "towel", "tower",
			"town", "toxic", "toxin", "toy", "trace", "track", "tract", "trade",
			"tragic", "trail", "train", "trait", "tram", "trance", "trap",
			"trauma", "travel", "tray", "tread", "treat", "treaty", "treble",
			"tree", "trek", "tremor", "trench", "trend", "trendy", "trial",
			"tribal", "tribe", "trick", "tricky", "tried", "trifle", "trim",
			"trio", "trip", "triple", "troop", "trophy", "trot", "trough",
			"trout", "truce", "truck", "true", "truly", "trunk", "trust",
			"truth", "try", "tube", "tumble", "tuna", "tundra", "tune", "tunic",
			"tunnel", "turban", "turf", "Turk", "Turkey", "turn", "turtle",
			"tutor", "tweed", "twelve", "twenty", "twice", "twin", "twist",
			"two", "tycoon", "tying", "type", "tyrant", "ugly", "ulcer",
			"ultra", "umpire", "unable", "uncle", "under", "uneasy", "unfair",
			"unify", "union", "unique", "unit", "unite", "unity", "unlike",
			"unrest", "unruly", "until", "update", "upheld", "uphill", "uphold",
			"upon", "upper", "uproar", "upset", "upshot", "uptake", "upturn",
			"upward", "urban", "urge", "urgent", "urging", "urine", "usable",
			"usage", "use", "useful", "user", "usual", "utmost", "utter",
			"vacant", "vacuum", "vague", "vain", "valet", "valid", "valley",
			"value", "valve", "van", "vanish", "vanity", "vary", "vase", "vast",
			"vat", "vault", "vector", "veil", "vein", "velvet", "vendor",
			"veneer", "Venice", "venom", "vent", "venue", "Venus", "verb",
			"verbal", "verge", "verify", "verity", "verse", "versus", "very",
			"vessel", "vest", "vet", "veto", "via", "viable", "vicar", "vice",
			"victim", "victor", "video", "Vienna", "view", "vigil", "Viking",
			"vile", "villa", "vine", "vinyl", "viola", "violet", "violin",
			"viral", "Virgo", "virtue", "virus", "visa", "vision", "visit",
			"visual", "vital", "vivid", "vocal", "vodka", "vogue", "voice",
			"void", "volley", "volume", "vote", "vowel", "voyage", "vulgar",
			"wade", "wage", "waist", "wait", "waiter", "wake", "walk", "walker",
			"wall", "wallet", "walnut", "wander", "want", "war", "warden",
			"warm", "warmth", "warn", "warp", "Warsaw", "wary", "was", "wash",
			"wasp", "waste", "watch", "water", "watery", "wave", "wax", "way",
			"weak", "weaken", "wealth", "weapon", "wear", "weary", "weave",
			"wedge", "wee", "weed", "week", "weekly", "weep", "weigh", "weight",
			"weird", "well", "were", "West", "wet", "whale", "wharf", "what",
			"wheat", "wheel", "when", "whence", "where", "which", "whiff",
			"while", "whim", "whip", "whisky", "white", "who", "whole",
			"wholly", "whom", "whose", "why", "wicked", "wide", "widely",
			"widen", "wider", "widow", "width", "wife", "wig", "wild", "wildly",
			"will", "willow", "wily", "win", "wind", "window", "windy", "wine",
			"wing", "wink", "winner", "winter", "wipe", "wire", "wisdom",
			"wise", "wish", "wit", "witch", "with", "within", "witty", "wizard",
			"woke", "wolf", "wolves", "woman", "womb", "won", "wonder", "wood",
			"wooden", "woods", "woody", "wool", "word", "work", "worker",
			"world", "worm", "worry", "worse", "worst", "worth", "worthy",
			"would", "wound", "wrap", "wrath", "wreath", "wreck", "wring",
			"wrist", "writ", "write", "writer", "wrong", "Xerox", "yacht",
			"Yale", "yard", "yarn", "yeah", "year", "yeard", "yeast", "yellow",
			"yet", "yield", "yogurt", "yolk", "you", "young", "your", "youth",
			"Zaire", "zeal", "zebra", "zenith", "zero", "Zeus", "zigzag",
			"zinc", "zombie", "zone");
}
