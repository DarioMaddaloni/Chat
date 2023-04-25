import java.util.*;
import java.io.*;
import java.net.*;

import java.io.StringWriter;
import java.io.PrintWriter;

import java.nio.charset.StandardCharsets;


import java.util.Arrays; 
import java.util.stream.Collectors;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.time.LocalTime;
import java.security.SecureRandom;


public class ServerThreadLauncher {
	static public int BLOCK_SIZE=16;
	static public int pubL=33;
	Socket s;
	DataInputStream dinS;
	DataOutputStream doutS;
	static public Terabitia terabitia;
	static public byte[] kPubS;
	static public byte[][] pairS;

	LinkedHashMap<DataInputStream, String> tableIn; //.ketSet() preserva l'ordine
	Hashtable<String, ChiaviS> tableOut;

	//Trasforma l'intero in un byte salvato nei primi due byte di b
	static public byte[] intTobyte(int intero) {
		byte[] b = new byte[BLOCK_SIZE];
		b[0] = (byte) intero;
		b[1] = (byte) (intero>>8);
		return b;
	}

	//Trasforma i primi due byte in un intero	
	static public int byteToInt(byte[] b) {
		int intero;
		intero = ((int) b[0]&0xff) +(((int) b[1])<<8) ;
		return intero;
	}

	static public void stampaBytes(byte[] message) {
		for (int i=0; i<message.length; i++) {
			if(i%16==0 && i>0) System.out.println("");
			System.out.print(String.format("%02X", message[i])+".");
		}
		System.out.println("\n");
	}

	//Rende il messaggio diviso in blocchi da 16 (non aggiunge il padding)
	static public byte[] rightLength(byte[] message) {
		int len=BLOCK_SIZE-message.length%BLOCK_SIZE;
		byte[] messagePadded = new byte[message.length + len];
		for (int i=0; i<message.length; i++) {
			messagePadded[i]=message[i];
		}
		return messagePadded;
	}

	static public String byteToStringAdjLen(byte[] fakeByte) {
		String to_be_returned = "";
		for (byte Byte : fakeByte) {
			if (Byte!=0x00) {
				to_be_returned += (char)Byte;
			} else {
				break;
			}
		}
		return to_be_returned;
	}

	static public byte[] generaNonce(int len){
		byte[] nonce= new byte[16];
		SecureRandom random = new SecureRandom();
		random.nextBytes(nonce);
		if(len!=0){
			nonce[0] = (byte) len;
			nonce[1] = (byte) (len>>8);
		}
		return nonce;
	}

	//invia a tutti il messaggio per cui devono aggiornare la presenza di un nuovo tableIn
	public void tellEveryoneL(byte[] messaggio) {
		try {
			byte[] messaggioBuf = new byte[messaggio.length];
			for(String nome : this.tableOut.keySet()) {
				messaggioBuf = Arrays.copyOf(messaggio, messaggio.length);
				terabitia.cryptAES(messaggioBuf, tableOut.get(nome).getSym(), tableOut.get(nome).getIV(), "E".getBytes());
				for(int i=0; i<messaggio.length; i++) {
					this.tableOut.get(nome).getOutStr().writeByte(messaggioBuf[i]);
				}
			}
		} catch (Exception e) {
			System.out.println(e);
		}
	}


	public static void main(String[] args){
		ServerThreadLauncher server=new ServerThreadLauncher();
		server.launch();
	}


	public void launch(){
		try {
			ServerSocket serverSocket=new ServerSocket(2020);
			tableIn = new LinkedHashMap<>();
			tableOut = new Hashtable<>();
			kPubS = new byte[2*BLOCK_SIZE];

			ChiaviS chiaviAndCo= new ChiaviS();
			byte[] username = new byte[BLOCK_SIZE];
			byte[] publicKey = new byte[2*BLOCK_SIZE];
			Secret secretSC;

			//generazione chiavi server
			try{
						pairS=ECC.keysGen();
						kPubS=pairS[1];
			} catch(Exception e) {
				e.printStackTrace();
			}

System.out.println("kPubS len: " + kPubS.length);
stampaBytes(kPubS);

			// il Server deve continuamente controllare se qualche client vuole connettersi: quindi mettiamo un while(true)
			while(true){
				Socket s=serverSocket.accept();
				DataInputStream dinS=new DataInputStream(s.getInputStream());
				DataOutputStream doutS=new DataOutputStream(s.getOutputStream());
System.out.println("\nNUOVA CONNESSIONE\n");

				// Lettura del nome e della chiave pubblica

				//Legge la chiave pubblica
				byte[] chiavePubblica = new byte[pubL];
				for(int i=0; i<pubL; i++) {
					chiavePubblica[i]=dinS.readByte();
				}

				//Calcola la chiave simmetrica e iv 
				secretSC = new Secret();
				try{
					secretSC = new Secret(ECC.doECDH(pairS[0],chiavePubblica));
System.out.println("secretClient calcolato.");
				} catch(Exception e) {
					e.printStackTrace();
				}

				//Inviare al nuovo client chiave pubblica del server
				for(int i=0; i<pubL; i++) {
					doutS.writeByte(kPubS[i]);
				}
System.out.println("inviata kP server.\n");

				//Controlla che il nome non sia già presente
				terabitia= new Terabitia();
				byte[] nome = new byte[BLOCK_SIZE];
				byte[] check = new byte[2*BLOCK_SIZE];
				byte[] nonce;
				byte[] isOkName = new byte[BLOCK_SIZE];

				for(int i=0; i<BLOCK_SIZE; i++) {
					nome[i]=dinS.readByte();
				}
				if(tableOut.containsKey(byteToStringAdjLen(nome))) {
					isOkName = rightLength("Change Name".getBytes());
				} else {
					isOkName = rightLength("Nice Name".getBytes());
				}
				nonce=generaNonce(0);
				for(int i=0;i<BLOCK_SIZE;i++){
					check[i]=nonce[i];
				}
				for(int i=0;i<BLOCK_SIZE;i++){
					check[i+BLOCK_SIZE]=isOkName[i];
				}
				terabitia.cryptAES(check, secretSC.getSym(), secretSC.getIV(), "E".getBytes());
				//kSC, ivSC, "E".getBytes());
				for(int i=0; i<2*BLOCK_SIZE; i++) {
					doutS.writeByte(check[i]);
				}

				if(byteToStringAdjLen(isOkName).equals("Nice Name")){
				//Invia il numero di clients connessi

				byte[] numero= new byte[2*BLOCK_SIZE];
				byte[] random = generaNonce(0);
				for (int i = 0; i<BLOCK_SIZE; i++) {
					numero[i] = random[i];
				}
				byte[] numeroClientsConnessi = intTobyte(tableOut.keySet().size());
				for (int i = 0; i<BLOCK_SIZE; i++) {
					numero[i+BLOCK_SIZE]= numeroClientsConnessi[i];
				}
				terabitia.cryptAES(numero, secretSC.getSym(), secretSC.getIV(), "E".getBytes());

				for(int i=0; i<2*BLOCK_SIZE; i++) {
					doutS.writeByte(numero[i]);
				}

				//Invia al client i nomi dei clients presenti e le chiavi pubbliche di tutti i client
				byte[] blocchiDati = new byte[5*BLOCK_SIZE];
				for(String soggetto : tableOut.keySet()) {
					random = generaNonce(0);
					for (int i = 0; i<BLOCK_SIZE; i++) {
						blocchiDati[i]= random[i];
					}
					//La chiave pubblica è salvata in un array
					byte[] sogg = new byte[BLOCK_SIZE];
					sogg = rightLength(soggetto.getBytes());
					for(int i=0; i<BLOCK_SIZE; i++) {
						blocchiDati[i+BLOCK_SIZE]= sogg[i];
					}
					byte[] pub = new byte[3*BLOCK_SIZE];
					pub = rightLength(tableOut.get(soggetto).pubblica);

					for(int i=0; i<3*BLOCK_SIZE; i++) {
						blocchiDati[i+2*BLOCK_SIZE]= pub[i];
					}

					terabitia.cryptAES(blocchiDati, secretSC.getSym(), secretSC.getIV(), "E".getBytes());
					//kSC, ivSC, "E".getBytes());
					for(int i=0; i<5*BLOCK_SIZE; i++) {
						doutS.writeByte(blocchiDati[i]);
					}
				}

				if(tableIn.keySet().size()>0){//se non e' lui stesso il primo
					//Avverte tutti dell'avvenuta connessione di un nuovo client inviando 2 chipertexts
					byte[] messaggio1 = new byte[2*BLOCK_SIZE];
					byte[] messaggio2 = new byte[5*BLOCK_SIZE];

					//scrive 16 bytes random
					random = generaNonce(0);
					for (int i = 0; i<BLOCK_SIZE; i++) {
						messaggio1[i]= random[i];
					}
					byte[] login = rightLength("Login".getBytes());
					//Aggiunge la stringa che avverte di un login
					for (int i=0; i<BLOCK_SIZE; i++) {
						messaggio1[i+BLOCK_SIZE]= login[i];
					}

					random = generaNonce(0);
					for (int i = 0; i<BLOCK_SIZE; i++) {
						messaggio2[i]= random[i];
					}
					//Aggiunge il nome al messaggio
					for(int i=0; i<BLOCK_SIZE; i++) {
						messaggio2[i+BLOCK_SIZE]= nome[i];
					}
					//Aggiunge la chiave pubblica al messaggio
					for(int i=0; i<pubL; i++) {
						messaggio2[i+2*BLOCK_SIZE]= chiavePubblica[i];
					}
					for(int i=0; i<BLOCK_SIZE-1; i++) {
						messaggio2[i+2*BLOCK_SIZE+pubL]= 0x00;
					}
					tellEveryoneL(messaggio1);
					tellEveryoneL(messaggio2);

					//Lo mette in comunicazione con un altro client (il primo) per la ktot
					//trova il primo
					DataInputStream dinC=tableIn.keySet().stream().findFirst().get();
					String contattato=tableIn.get(dinC);
					byte[] contattatoB=rightLength(contattato.getBytes());
					//gli invia la richiesta
					byte[] richiesta=new byte[2*BLOCK_SIZE];

					//scrive 16 bytes random
					nonce = generaNonce(0);
					for (int i = 0; i<BLOCK_SIZE; i++) {
						richiesta[i]=nonce[i];
					}
					//Aggiunge modality
					byte[] ktotM = rightLength("Ktot".getBytes());
					for (int i=0; i<BLOCK_SIZE; i++) {
						richiesta[i+BLOCK_SIZE]=ktotM[i];
					}

					//invia a contattato nonce||modality cifrata
					terabitia.cryptAES(richiesta, tableOut.get(contattato).getSym(), tableOut.get(contattato).getIV(), "E".getBytes());
					for(int i=0; i<2*BLOCK_SIZE; i++) {
						tableOut.get(contattato).getOutStr().writeByte(richiesta[i]);
					}

					//scrive 16 bytes random
					nonce = generaNonce(0);
					for (int i = 0; i<BLOCK_SIZE; i++) {
						richiesta[i]=nonce[i];
					}
					//aggiunge il richiedente
					for(int i=0; i<BLOCK_SIZE; i++) {
						richiesta[i+BLOCK_SIZE]=nome[i];
					}
					//invia a contattato nonce||nomenuovoclient
					terabitia.cryptAES(richiesta, tableOut.get(contattato).getSym(), tableOut.get(contattato).getIV(), "E".getBytes());
					for(int i=0; i<2*BLOCK_SIZE; i++) {
						tableOut.get(contattato).getOutStr().writeByte(richiesta[i]);
					}
				}

				//Salva il nome utente con la sua chiave pubblica e la chiave privata tra server e client
				tableOut.put(byteToStringAdjLen(nome), new ChiaviS(doutS, secretSC, chiavePubblica));
				tableIn.put(dinS, byteToStringAdjLen(nome));

				//Crea l'oggetto Thread
				Thread thr=new Diffusion(s, dinS, doutS, tableIn, tableOut);
				//Brencha l'esecuzione
				thr.start();
				}
			}
		} catch(IOException ex) {
			ex.printStackTrace();
		}
	}

}

class Diffusion extends Thread {
	static public int BLOCK_SIZE=16;
	Socket s;
	DataInputStream dinS;
	DataOutputStream doutS;
	LinkedHashMap<DataInputStream, String> tableIn;
	ChiaviS chiaviAndCo;
	Hashtable<String, ChiaviS> tableOut;
	String utenteAssociato;
	Terabitia terabitia;

	public Diffusion(Socket socket, DataInputStream dinS, DataOutputStream doutS, LinkedHashMap<DataInputStream, String> tableIn, Hashtable<String, ChiaviS> tableOut) {
		this.s = socket;
		this.dinS = dinS;
		this.doutS = doutS;
		this.tableIn = tableIn;
		this.tableOut = tableOut;
	}

	//Trasforma l'intero in un byte salvato nei primi due byte di b
	static public byte[] intTobyte(int intero) {
		byte[] b = new byte[BLOCK_SIZE];
		b[0] = (byte) intero;
		b[1] = (byte) (intero>>8);
		return b;
	}

	//Trasforma i primi due byte in un intero
	static public int byteToInt(byte[] b) {
		int intero;
		intero = ((int) b[0]&0xff) +(((int) b[1])<<8) ;
		return intero;
	}

	//Rende il messaggio diviso in blocchi da 16 (non aggiunge il padding)
	static public byte[] rightLength(byte[] message) {
		int len=BLOCK_SIZE-message.length%BLOCK_SIZE;
		byte[] messagePadded=new byte[message.length + len];
		for (int i=0; i<message.length; i++) {
			messagePadded[i]=message[i];
		}
		return messagePadded;
	}

	static public void stampaBytes(byte[] message) {
		for (int i=0; i<message.length; i++) {
			if(i%16==0 && i>0) System.out.println("");
			System.out.print(String.format("%02X", message[i])+".");
		}
		System.out.println("\n");
	}

	static public String byteToStringAdjLen(byte[] fakeByte) {
		String to_be_returned = "";
		for (byte Byte : fakeByte) {
			if (Byte!=0x00) {
				to_be_returned += (char)Byte;
			} else {
				break;
			}
		}
		return to_be_returned;
	}

	//appena un client si connette o disconnette viene inviata a tutti la notizia (i.e. viene inviata una stringa con formato lista che contiene tuti gli username dei client effettivamente connessi)
	public void updatetableOut(String nomeIn) {
		try {
			for (String nomeOut : tableOut.keySet()) {
				byte[] nomeInb = new byte[BLOCK_SIZE];
				nomeInb = rightLength(nomeIn.getBytes());
				for (int i=0; i<BLOCK_SIZE; i++) {
					tableOut.get(nomeOut).getOutStr().writeByte(nomeInb[i]);
				}
			}
		} catch(IOException ex) {
			ex.printStackTrace();
		}
	}

	static public byte[] generaNonce(int len){
		byte[] nonce= new byte[16];
		SecureRandom random = new SecureRandom();
		random.nextBytes(nonce);
		if(len!=0){
			nonce[0] = (byte) len;
			nonce[1] = (byte) (len>>8);
		}
		return nonce;
	}

	public void tellEveryone(byte[] messaggio, byte[] mittente) {
		try {
			String mittS=byteToStringAdjLen(mittente);
			for(String nome : tableOut.keySet()) {
				if(! nome.equals(mittS)) {
					terabitia.cryptAES(messaggio, tableOut.get(nome).getSym(),tableOut.get(nome).getIV(), "E".getBytes());
					for(int i=0; i<messaggio.length; i++) {
						tableOut.get(nome).getOutStr().writeByte(messaggio[i]);
					}
					terabitia.cryptAES(messaggio, tableOut.get(nome).getSym(),tableOut.get(nome).getIV(), "D".getBytes());
				}
			}
		} catch (Exception e) {
			System.out.println(e);
		}
	}


	public void tellTo(byte[] messaggio, byte[] mittente, byte[] destinatario) {
		if(byteToStringAdjLen(destinatario).equals("All")) {
			tellEveryone(messaggio, mittente);
		} else { 
			try {
				String nome = byteToStringAdjLen(destinatario);
				if(! nome.equals(byteToStringAdjLen(mittente))){

					terabitia.cryptAES(messaggio, tableOut.get(byteToStringAdjLen(destinatario)).getSym(), tableOut.get(byteToStringAdjLen(destinatario)).getIV(), "E".getBytes());
					for(int i=0; i<messaggio.length; i++) {
						tableOut.get(nome).getOutStr().writeByte(messaggio[i]);
					}
				}
			} catch (Exception e) {
				System.out.println(e);
			}
		}
	}

	@Override
	public void run() {
		terabitia= new Terabitia();
		utenteAssociato= tableIn.get(dinS);
		byte[] utenteAssB=rightLength(utenteAssociato.getBytes());
		try{
			while(true) {
				//riceve sempre 2 blocchi criptati, con Nonce e Modality
				byte[] blocco2 = new byte[2*BLOCK_SIZE];
				for (int i=0; i<2*BLOCK_SIZE; i++) {
					blocco2[i]=dinS.readByte();
				}
				terabitia.cryptAES(blocco2, tableOut.get(utenteAssociato).getSym(), tableOut.get(utenteAssociato).getIV(), "D".getBytes());

				//Legge la modality
				byte[] modality = new byte[BLOCK_SIZE];
				for (int i=0; i<BLOCK_SIZE; i++) {
					modality[i]=blocco2[i+BLOCK_SIZE];
				}

				byte[] random;

				if("Logout".equals(byteToStringAdjLen(modality))) {
					//Crea una Lista in cui inserirò tutto il messaggio cifrato
					byte[] messaggio = new byte[2*BLOCK_SIZE];
					int counter=0;

					//Scrive 16 byte random
					random = generaNonce(0);
					for (int i = 0; i<BLOCK_SIZE; i++) {
						messaggio[counter]= random[i];
						counter++;
					}
					//Scrive modality
					for(int i=0; i<BLOCK_SIZE; i++) {
						messaggio[counter]= modality[i];
						counter++;
					}
					//Invia il messaggio
					tellEveryone(messaggio, utenteAssB);

					counter=0;
					//Scrive 16 byte random
					random = generaNonce(0);
					for (int i = 0; i<BLOCK_SIZE; i++) {
						messaggio[counter]= random[i];
						counter++;
					}
					//Aggiunge il nome al messaggio
					for(int i=0; i<BLOCK_SIZE; i++) {
						messaggio[counter]= utenteAssB[i];
						counter++;
					}
					//Invia il messaggio
					tellEveryone(messaggio, utenteAssB);

					//Anche il server li elimina dalla propria tableOut e tableIn
					tableOut.remove(tableIn.get(dinS));
					tableIn.remove(dinS);

				} else if("Ktot".equals(byteToStringAdjLen(modality))){

					//Crea un array in cui inserirò tutto il messaggio cifrato
					byte[] messaggio = new byte[5*BLOCK_SIZE];

					for (int i = 0; i<5*BLOCK_SIZE; i++) {
						messaggio[i]= dinS.readByte();
					}

					terabitia.cryptAES(messaggio, tableOut.get(utenteAssociato).getSym(), tableOut.get(utenteAssociato).getIV(), "D".getBytes());

					//legge destinatario e lo sovrascrive con il mittente di ktot
					byte[] destinatario = new byte[BLOCK_SIZE];
					for (int i=0; i<BLOCK_SIZE; i++) {
						destinatario[i]=messaggio[i+BLOCK_SIZE];
						messaggio[i+BLOCK_SIZE]=utenteAssB[i];
					}

					//manda (la cifratura viene eseguita in tellTo)
					tellTo(messaggio, utenteAssB, destinatario);

				} else if("messaggio".equals(byteToStringAdjLen(modality))) {
					//Legge la lunghezza del messaggio (primi due byte del nonce in blocco2)
					int len= byteToInt(Arrays.copyOfRange(blocco2, 0, 2));
					byte[] messaggioIn= new byte[2*BLOCK_SIZE+ len];
					for(int i=0; i<2*BLOCK_SIZE+ len; i++) {
						messaggioIn[i]=dinS.readByte();
					}
					terabitia.cryptAES(messaggioIn, tableOut.get(utenteAssociato).getSym(), tableOut.get(utenteAssociato).getIV(), "D".getBytes());

					//Legge il mittente
					byte[] mittente = new byte[BLOCK_SIZE];
					mittente = rightLength(tableIn.get(dinS).getBytes());

					//Legge il destinatario
					byte[] destinatario = new byte[BLOCK_SIZE];
					for(int i=0; i<BLOCK_SIZE; i++) {
						destinatario[i]=messaggioIn[i+BLOCK_SIZE];
					}

					//Crea una Lista in cui inserirò tutto il messaggio cifrato
					byte[] messaggioAnteprima = new byte[2*BLOCK_SIZE];
					byte[] messaggioOut = new byte[3*BLOCK_SIZE+len];
					int counter=0;
					//Scrive 16 byte random
					random = generaNonce(len);
					for (int i = 0; i<BLOCK_SIZE; i++) {
						messaggioAnteprima[counter]= random[i];
						counter++;
					}
					//Scrive modality
					for(int i=0; i<BLOCK_SIZE; i++) {
						messaggioAnteprima[counter]= modality[i];
						counter++;
					}
					counter=0;
					//Scrive 16 byte random
					random = generaNonce(0);
					for (int i = 0; i<BLOCK_SIZE; i++) {
						messaggioOut[counter]= random[i];
						counter++;
					}
					//Scrive mittente
					for(int i=0; i<BLOCK_SIZE; i++) {
						messaggioOut[counter]= mittente[i];
						counter++;
					}
					//Scrive dest.
					for(int i=0; i<BLOCK_SIZE; i++) {
						messaggioOut[counter]= destinatario[i];
						counter++;
					}

					//A questo punto il server tenterà di leggere i blocchi successivi di messaggioIn solo se ce ne sono altri da leggere
					for (int i=0; i<len; i++) {
						messaggioOut[counter]=messaggioIn[i+2*BLOCK_SIZE];
						counter++;
					}
					tellTo(messaggioAnteprima, mittente, destinatario);
					tellTo(messaggioOut, mittente, destinatario);
					
				} else {
					System.out.println("Something went wrong in modality");
				}
				if(0x01==0x00) break;
			}
			s.close();
		} catch(IOException e){
			System.out.println("Il client "+ utenteAssociato+ " e' uscito alle ore: " +LocalTime.now().toString()+"\n");
		}
	}
}


