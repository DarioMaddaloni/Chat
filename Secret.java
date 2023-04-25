import java.io.*;  
import java.util.Arrays; 

public class Secret {
	static public void stampaBytes(byte[] message) {
		for (int i=0; i<message.length; i++) {
			if(i%16==0 && i>0) System.out.println("");
			System.out.print(String.format("%02X", message[i])+".");
		}
		System.out.println("");
	}

	public Terabitia terabitia;
	public byte[] roundKeys; //un unico array contentente le 10 roundKeys
	public byte[] IV;
	public byte[] symAndIV; //16b di kSym, 16 di IV
	public Secret() {}
	public Secret(byte[] symAndIV) { //riceve 32 bytes da ECDH
		this.symAndIV=symAndIV;
		this.roundKeys=new byte[176];
		terabitia=new Terabitia();
		terabitia.Setup(Arrays.copyOfRange(this.symAndIV,16, 32),this.roundKeys);
		this.IV=Arrays.copyOfRange(this.symAndIV,16, 32);
	}

	public byte[] getAll(){
		return this.symAndIV;
	}
	public byte[] getSym(){
		return this.roundKeys;
	}
	public byte[] getIV(){
		return this.IV;
	}
}
