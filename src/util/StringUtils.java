package util;

import java.nio.ByteBuffer;

public abstract class StringUtils {
	public final static String newline = System.getProperty("line.separator");
	
	public final static int NAME_MAXLENGTH = 32;

	public static final int MESSAGE_MAXLENGTH = 128;
	
	public static String getName(ByteBuffer bb) {
		StringBuilder sb = new StringBuilder();
		
		for(int i = 0; i < NAME_MAXLENGTH; i++) {
			sb.append((char)(bb.get() & 0xFF));
		}
		
		return sb.toString();
	}
	
	public static String getMessage(ByteBuffer bb) {
		StringBuilder sb = new StringBuilder();
		
		for(int i = 0; i < MESSAGE_MAXLENGTH; i++) {
			sb.append((char)(bb.get() & 0xFF));
		}
		
		return sb.toString();
	}
}
