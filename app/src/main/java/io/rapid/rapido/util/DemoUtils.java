package io.rapid.rapido.util;


import java.util.UUID;


public class DemoUtils {

	public static String checkCollectionName(String collectionName) {
		if(!isValidUUID(collectionName))
			throw new IllegalArgumentException("Sample collection name has wrong format. Make sure you are using the name that was generated for you on http://www.rapid.io/demo");
		else
			return collectionName;
	}


	private static boolean isValidUUID(String uuidString) {
		try {
			UUID uuid = UUID.fromString(uuidString);
			return true;
		} catch(IllegalArgumentException exception) {
			return false;
		}
	}
}
