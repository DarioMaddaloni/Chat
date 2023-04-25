public class Terabitia {	
	
	public native void Setup(byte[] masterKey, byte[] roundKey);
	public native void cryptAES(byte[] message, byte[] key, byte[] IV, byte[] modality);
	
	static {
		System.loadLibrary("Terabitia");
	}
}
