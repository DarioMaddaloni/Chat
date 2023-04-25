import java.math.BigInteger;
import java.security.PublicKey;
import java.security.PrivateKey;
import java.security.KeyFactory;
import java.security.Security;
import java.security.KeyPairGenerator;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.ECGenParameterSpec;
import javax.crypto.KeyAgreement;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.jce.spec.ECPrivateKeySpec;
import org.bouncycastle.math.ec.ECPoint;

public class ECC {
	static public void stampaBytes(byte[] message) {
		for (int i=0; i<message.length; i++) {
			if(i%16==0 && i>0) System.out.println("");
			System.out.print(String.format("%02X", message[i])+".");
		}
		System.out.println("\n");
	}

	public static byte[] savePublicKey (PublicKey key) throws Exception {
		ECPublicKey eckey = (ECPublicKey)key;
		return eckey.getQ().getEncoded(true);
	}

	public static PublicKey loadPublicKey (byte [] data) throws Exception {
		ECParameterSpec params = ECNamedCurveTable.getParameterSpec("secp256k1");
		ECPublicKeySpec pubKey = new ECPublicKeySpec(
		params.getCurve().decodePoint(data), params);
		try {
			KeyFactory kf = KeyFactory.getInstance("ECDH", "BC");
			return kf.generatePublic(pubKey);
		} catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidKeySpecException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static byte[] savePrivateKey (PrivateKey key) throws Exception {
		ECPrivateKey eckey = (ECPrivateKey)key;
		return eckey.getD().toByteArray();
	}

	public static PrivateKey loadPrivateKey (byte [] data) throws Exception {
		ECParameterSpec params = ECNamedCurveTable.getParameterSpec("secp256k1");
		ECPrivateKeySpec prvkey = new ECPrivateKeySpec(new BigInteger(data), params);
		try {
			KeyFactory kf = KeyFactory.getInstance("ECDH", "BC");
			return kf.generatePrivate(prvkey);
		} catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidKeySpecException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static byte[] doECDH (byte[] dataPrv, byte[] dataPub) throws Exception {
		Security.addProvider(new BouncyCastleProvider());
		try {
			KeyAgreement ka = KeyAgreement.getInstance("ECDH", "BC");
			ka.init(loadPrivateKey(dataPrv));
			ka.doPhase(loadPublicKey(dataPub), true);
			byte[] secret = ka.generateSecret(); //32bytes
			return secret;
		} catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static byte[][] keysGen() throws Exception {
		Security.addProvider(new BouncyCastleProvider());
		try {
			KeyPairGenerator kpgen = KeyPairGenerator.getInstance("ECDH", "BC");
			kpgen.initialize(new ECGenParameterSpec("secp256k1"), new SecureRandom());
			KeyPair pair = kpgen.generateKeyPair();

			byte[] dataPrv = savePrivateKey(pair.getPrivate()); //32bytes
			byte[] dataPub = savePublicKey(pair.getPublic()); //32bytes
			byte[][] pairByte={dataPrv,dataPub};
			return pairByte;
		} catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException e) {
			e.printStackTrace();
			return null;
		}
	}

}
