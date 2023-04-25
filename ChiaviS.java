import java.io.*;  

public class ChiaviS {
	public DataOutputStream dout;
	public Secret KeysAndIV; //consiste in 160 bytes della roudKeysGeneration e 16 di IV
	public byte[] pubblica;

	public ChiaviS() {}
	public ChiaviS(DataOutputStream x, Secret y, byte[] z) {
		this.dout = x;
		this.KeysAndIV= y;
		this.pubblica = z;
	}
	public DataOutputStream getOutStr(){
		return this.dout;
	}
	public byte[] getKPub(){
		return this.pubblica;
	}
	public byte[] getSym(){
		return this.KeysAndIV.getSym();
	}
	public byte[] getIV(){
		return this.KeysAndIV.getIV();
	}
}
