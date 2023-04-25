import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Scanner;
import java.time.LocalTime;

import java.io.StringWriter;
import java.io.PrintWriter;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.Arrays; 
import java.util.stream.Collectors;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.security.SecureRandom;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.text.Text;

import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.control.ChoiceBox;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;

import javafx.scene.paint.Color; 
import javafx.scene.Group; 
import javafx.scene.Node;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;


public class ClientThreadLauncher extends Application {
	static int BLOCK_SIZE=16; 
	static public int pubL=33;
	public byte[] username;

	//i dati sensibili
	static private byte[] myPublicKey;
	static private byte[][] myPair;
	static private Secret secretTot; //con Ktot e IVtot
	static private Secret secretServer; // K e Iv scambiati con il server
	static private Hashtable<String, Secret> hashtable; // K e Iv scambiati con tutti gli altri clients online

	static public Socket sock;
	static public DataInputStream dinC;
	static public DataOutputStream doutC;

	//id è l'indirizzo a cui si collega il client
	static public String id;
	//E' il nome che verrà visualizzato dal client come nome scelto
	static public String titolo;

	static public Terabitia terabitia;
	static public Thread thread;

	public Stage stage;
	public Scene scene;
	public Parent root;
	//TextArea
	@FXML public TextArea indirizzo; // set.fxml
	@FXML public TextArea nomeUtente; // set.fxml
	@FXML public TextArea inputFieldThisClient; // go.fxml
	@FXML public TextArea outputFieldAllClients; // go.fxml
	//Text
	@FXML public Text Morpheus; // ready.fxml
	@FXML public Text idAndUser; // set.fxml
	@FXML public Text welcometext; // go.fxml
	//Buttons
	@FXML public javafx.scene.control.Button enter; // ready.fxml
	@FXML public javafx.scene.control.Button exit; // ready.fxml and set.fxml
	@FXML public javafx.scene.control.Button connect; //set.fxml
	@FXML public javafx.scene.control.Button disconnect; // go.fxml
	@FXML public javafx.scene.control.Button send; // go.fxml
	// ChoiceBox
	@FXML public ChoiceBox<String> Lista; // go.fxml

	static int logged=0;

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

	//Stampa in blocchi il messaggio
	static public void stampaBytes(byte[] message) {
		for (int i=0; i<message.length; i++) {
			if(i%16==0 && i>0) System.out.println("");
			System.out.print(String.format("%02X", message[i])+".");
		}
		System.out.println("");
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

	//Trasforma in stringa ciò che non è 0x00
	static public String byteToStringAdjLen(byte[] fakeByte) {
		String to_be_returned = "";
		for (byte Byte : fakeByte) {
			if (Byte!=0x00 && Byte!=0x01) {
				to_be_returned += (char)Byte;
			} else {
				break;
			}
		}
		return to_be_returned;
	}

	static public byte[] generaNonce(int len){
		byte[] nonce= new byte[BLOCK_SIZE];
		SecureRandom random = new SecureRandom();
		random.nextBytes(nonce);
		if(len!=0){
			nonce[0] = (byte) len;
			nonce[1] = (byte) (len>>8);
		}
		return nonce;
	}

	//Serve a scrivere i nomi nel go.fxml e attiva il thread di ascolto del client
	public void displayName(byte[] nameToShow, Set<String> altriClients) {
		for(String altroClient : altriClients) {
			//Aggiunge il client alla lista
			Lista.getItems().add(altroClient);
		}
		Lista.getItems().add(byteToStringAdjLen(nameToShow));//aggiunge se stesso
		//Setta il nome nel titolo dell'utente
		username=nameToShow;
		titolo=byteToStringAdjLen(username);
		welcometext.setText(byteToStringAdjLen(nameToShow));
		//Si prepara ad ascoltare i messaggio che verranno inviati dal server
		thread = new Thread(new Ascoltatore());
		thread.start();
	}

	//Si connette al server nella porta 2020
	static public void networking(String id) {
		try {
			sock = new Socket(id, 2020);
			InputStreamReader streamReader = new InputStreamReader(sock.getInputStream());
			doutC=new DataOutputStream(sock.getOutputStream());
			dinC=new DataInputStream(sock.getInputStream());
			terabitia=new Terabitia();
		} catch(IOException ex) {
			ex.printStackTrace();
		}
	}


	static public void main(String[] args) {
		Application.launch(ClientThreadLauncher.class, args);
	}


	//Si apre la finestra ready.fxml quando runniamo il codice
	@Override
	public void start(Stage stage) throws Exception {
		Parent root = FXMLLoader.load(getClass().getResource("ready.fxml"));
		Scene scene = new Scene(root, 850, 600);
		stage.setTitle("Chat");
		stage.setScene(scene);
		stage.setResizable(false);
		stage.setOnCloseRequest(new EventHandler<WindowEvent>() {
			public void handle(WindowEvent we) {
				if (logged==1){
					byte[] messaggio = new byte[2*BLOCK_SIZE];

					byte[] random = generaNonce(0);
					for (int i = 0; i<BLOCK_SIZE; i++) {
						messaggio[i]=random[i];
					}
					byte[] logout = new byte[BLOCK_SIZE];
					logout = rightLength("Logout".getBytes());
					for (int i = 0; i<BLOCK_SIZE; i++) {
						messaggio[i+BLOCK_SIZE]=logout[i];
					}

					terabitia.cryptAES(messaggio, secretServer.getSym(), secretServer.getIV(), "E".getBytes());
					//Scrive il messaggio al server

					try {
						for(int i=0; i<2*BLOCK_SIZE; i++) {
							doutC.writeByte(messaggio[i]);
						}
					} catch (Exception e) {
						System.out.println("Something went wront in writing");
					}
					//Chiude la connessione
					try {
						sock.close();
						Parent root = FXMLLoader.load(getClass().getResource("ready.fxml"));
						Stage stage = (Stage)((Node)we.getSource()).getScene().getWindow();
						Scene scene = new Scene(root, 850, 600);
						stage.setScene(scene);
						stage.show();
					} catch (Exception e) {
						System.out.println("Logged out");
					}
					logged=0;
				}
			}
		});
		stage.show();
	}

	// ready.fxml Passa al file set.fxml
	@FXML public void entra(ActionEvent event) throws Exception {
		root = FXMLLoader.load(getClass().getResource("set.fxml"));
		stage = (Stage)((Node)event.getSource()).getScene().getWindow();
		scene = new Scene(root, 850, 600);
		stage.setScene(scene);
		stage.setResizable(true);
		stage.show();
	}

	// ready.fxml e set.fxml chiude lo stage
	@FXML public void esci(ActionEvent event) throws Exception {
		stage = (Stage) exit.getScene().getWindow();
		stage.close();
	}

	// set.fxml si connette al server e entra in go.fxml
	@FXML public void connetti(ActionEvent event) throws Exception {
		//Il nome deve essere più corto di BLOCK_SIZE cifre
		if (nomeUtente.getText().getBytes().length<BLOCK_SIZE && nomeUtente.getText().getBytes().length!=0) {

			//Mi connetto al server
			id="127.0.0.1";
			if (indirizzo.getText()!="") {
				id=indirizzo.getText();
			}
			networking(id);

			//Creo la mia chiave privata e calcolo la pubblica
			myPair=ECC.keysGen();
			myPublicKey=myPair[1];

System.out.println("myPublicKey len: " + myPublicKey.length);
stampaBytes(myPublicKey);

			//Invio al server la chiave pubblica
			for(int i=0; i<pubL; i++) {
				doutC.writeByte(myPublicKey[i]);
			}


			byte[] keyPublicB = new byte[pubL];
			byte[] ECCResult = new byte[2*BLOCK_SIZE];
			byte[] check = new byte[2*BLOCK_SIZE]; //arriva criptato nonce||stringa_check
			byte[] isOkName = new byte[BLOCK_SIZE];

			//legge kpub del server
			for (int i=0; i<pubL; i++) {
				keyPublicB[i]=dinC.readByte();
			}


			//Calcola secretServer
			secretServer = new Secret(ECC.doECDH(myPair[0],keyPublicB));
System.out.println("secretServer calcolato.\n");

			//Vedo se al server va bene il nomeUtente, altrimenti ne devo proporre un altro
				//scelgo ed invio il nome
				this.username = rightLength(nomeUtente.getText().getBytes());
				for(int i=0; i<BLOCK_SIZE; i++) {
					doutC.writeByte(username[i]);
				}


				for(int i=0; i<2*BLOCK_SIZE; i++) {
					check[i] = dinC.readByte();
				}
				terabitia.cryptAES(check, secretServer.getSym(), secretServer.getIV(), "D".getBytes());

				//Isolo l'informazione necessaria
				for(int i=0; i<BLOCK_SIZE; i++) {
					isOkName[i]=check[i+BLOCK_SIZE];
				}

				if(byteToStringAdjLen(isOkName).equals("Nice Name")){
						System.out.println("\nTERMINALE DI: "+byteToStringAdjLen(username)+"\n");

					//Legge nonce||numeroClientsConnessi
					byte[] nonceConNumeroClients = new byte[2*BLOCK_SIZE];
					for (int i=0; i<2*BLOCK_SIZE; i++) {
						nonceConNumeroClients[i]=dinC.readByte();
					}
					terabitia.cryptAES(nonceConNumeroClients, secretServer.getSym(), secretServer.getIV(), "D".getBytes());
					byte[] numeroClientsConnessi = new byte[BLOCK_SIZE];
					for (int i=0; i<BLOCK_SIZE; i++) {
						numeroClientsConnessi[i]=nonceConNumeroClients[i+BLOCK_SIZE];
					}
System.out.println("N. clients: "+byteToInt(numeroClientsConnessi));
					//Riceve dal server le chiavi pubbliche e i nomi utente di tutti i clients e del server stesso
					hashtable = new Hashtable<>();
					Secret secret;
					byte[] infosClientB = new byte[5*BLOCK_SIZE];
					byte[] clientB = new byte[BLOCK_SIZE];


					//A questo punto il client tenterà di leggere i blocchi successivi solo se ci sono altri client
					for(int j=0; j<byteToInt(numeroClientsConnessi); j++){
System.out.println("iterazione "+j);
						for (int i=0; i<5*BLOCK_SIZE; i++) {
							infosClientB[i]=dinC.readByte();
						}
						terabitia.cryptAES(infosClientB, secretServer.getSym(), secretServer.getIV(), "D".getBytes());
						//vado a sovrascrivere il byte[] clientB
						for (int i=0; i<BLOCK_SIZE; i++) {
							clientB[i]=infosClientB[i+BLOCK_SIZE];
						}
						//vado a sovrascrivere il byte[] keyPublicB
						for (int i=0; i<pubL; i++) {
							keyPublicB[i]=infosClientB[i+2*BLOCK_SIZE];
						}

						//Gestisce attraverso ECC la chiave e genera la chiave comune
						secret = new Secret(ECC.doECDH(myPair[0],keyPublicB));
System.out.println("secret calcolato.");


						//Inserisce i valori (nomeClient, <chiaveAssociata, IV>) nella hashtable
						hashtable.put(byteToStringAdjLen(clientB), secret);
					}
System.out.println("\nFINE registrazione altri clients\n");

					//scambio o generazione ktot e IVtot
					byte[] KtotIVtot = new byte[2*BLOCK_SIZE];
					if(byteToInt(numeroClientsConnessi)==0){ 
						SecureRandom rand = new SecureRandom();
						rand.nextBytes(KtotIVtot);
					} 
					else {//il server fa lo stesso check del n. cl. connessi, nel caso fa partire la richiesta di scambio con il primo cl. della sua hasht
						//Crea un arraybyte lungo 5 BLOCK_SIZE
						byte[] infoKtot = new byte[5*BLOCK_SIZE];
						for(int i=0; i<5*BLOCK_SIZE; i++) {
	//System.out.println(i);
							infoKtot[i]=dinC.readByte();
						}

						//la decripta prendendo la chiave simm dalla hasht. del server
						terabitia.cryptAES(infoKtot, secretServer.getSym(), secretServer.getIV(), "D".getBytes());

						//Isola il secondo blocco per decidere la chiave simmetrica da usare
						byte[] mittente = new byte[BLOCK_SIZE];
						for (int i=0; i<BLOCK_SIZE; i++) {
							mittente[i] = infoKtot[i+BLOCK_SIZE];
						}
System.out.println("ktot ricevuta da: "+byteToStringAdjLen(mittente));
						//Isola il messaggio "avanzato" con la chiave simmetrica associata al mittente
						byte[] nonceKtotIVtot = new byte[3*BLOCK_SIZE];
						for (int i=0; i<3*BLOCK_SIZE; i++) {
							nonceKtotIVtot[i] = infoKtot[i+2*BLOCK_SIZE];
						}


						//Decifra il messaggio

						terabitia.cryptAES(nonceKtotIVtot, hashtable.get(byteToStringAdjLen(mittente)).getSym(), hashtable.get(byteToStringAdjLen(mittente)).getIV(), "D".getBytes());
						for(int i=0; i<2*BLOCK_SIZE; i++) {
							KtotIVtot[i] = nonceKtotIVtot[i+BLOCK_SIZE];
						}
					}
					secretTot= new Secret(KtotIVtot);

					//Carico la pagina go.fxml
					FXMLLoader loader = new FXMLLoader(getClass().getResource("go.fxml"));	
					root = loader.load();

					//Setto il titolo con il suo nome (in controller aprirò anche il thread Ascoltatore)
					ClientThreadLauncher controller = loader.getController();
					controller.displayName(username, hashtable.keySet());

					//Mostro la pagina go.fxml
					stage = (Stage)((Node)event.getSource()).getScene().getWindow();
					scene = new Scene(root);
					stage.setScene(scene);
					stage.show();
					logged=1;
				} else{
				nomeUtente.setText(byteToStringAdjLen(isOkName));
				sock.close();
			}
		} else {
			nomeUtente.setText("Il nome utente deve essere composto da meno di 17 caratteri e più di 0");
		}
	}

	// go.fxml si disconnette dal server e carica la ready.fxml
	@FXML public void disconnetti(ActionEvent event) throws Exception {

		byte[] messaggio = new byte[2*BLOCK_SIZE];

		byte[] random = generaNonce(0);
		for (int i = 0; i<BLOCK_SIZE; i++) {
			messaggio[i]=random[i];
		}
		byte[] logout = new byte[BLOCK_SIZE];
		logout = rightLength("Logout".getBytes());
		for (int i = 0; i<BLOCK_SIZE; i++) {
			messaggio[i+BLOCK_SIZE]=logout[i];
		}

		terabitia.cryptAES(messaggio, secretServer.getSym(), secretServer.getIV(), "E".getBytes());
		//Scrive il messaggio al server

		for(int i=0; i<2*BLOCK_SIZE; i++) {
			doutC.writeByte(messaggio[i]);
		}
		//Chiude la connessione
		try {
			sock.close();
			root = FXMLLoader.load(getClass().getResource("ready.fxml"));
			stage = (Stage)((Node)event.getSource()).getScene().getWindow();
			scene = new Scene(root, 850, 600);
			stage.setScene(scene);
			stage.show();
		} catch (Exception e) {
			System.out.println("Logged out");
		}
		logged=0;
	}

	// go.fxml cifra il messaggio e lo invia al server
	@FXML public void invia(ActionEvent event) throws Exception {
		//N.B. è necessario inizializzare queste due variabili ora perché così possiamo definire la lunghezza del messaggio
		byte[] nomeA = welcometext.getText().getBytes();
		byte[] hour = LocalTime.now().toString().getBytes();
		String dest = Lista.getValue();

		//Aggiungiamo la lunghezza del messaggio
		int lunghezzaNonPaddata = nomeA.length+" (".length()+hour.length-4+") ".length()+dest.length()+"->\n".length()+inputFieldThisClient.getText().getBytes().length+"\n".length();
		int mancante = BLOCK_SIZE-lunghezzaNonPaddata%BLOCK_SIZE;
		if (mancante == 16) mancante=0;
		int lunghezzaPaddata = lunghezzaNonPaddata + mancante;
		byte[] byteLunghezzaPaddata = intTobyte(lunghezzaPaddata); //Si ricordi che al massimo un messaggio può essere lungo 500 byte

		//Inizia il messaggio che verrà cifrato con la chiave privata e poi aggiungo all'arraybyte messaggio
		byte[] parteDaCifrare = new byte[lunghezzaPaddata];
		int index = 0;
		//Aggiungo il nome della persona che sta inviando il messaggio
		for(int i=0; i<nomeA.length; i++) {
			parteDaCifrare[index]=nomeA[i];
			index++;
		}
		//Aggiungo  ( al messaggio
		for(byte b : " (".getBytes()) {
			parteDaCifrare[index]=b;
			index++;
		}
		//Aggiungo l'ora
		for(int i=0; i<hour.length-4; i++) {
			parteDaCifrare[index]=hour[i];
			index++;
		}
		//Aggiungo )  al messaggio
		for(byte b : ") ".getBytes()) {
			parteDaCifrare[index]=b;
			index++;
		}
		//Aggiungo il destinatario al messaggio
		for(byte b : Lista.getValue().getBytes()) {
			parteDaCifrare[index]=b;
			index++;
		}
		//Aggiungo ->\n al messaggio
		for(byte b : "->\n".getBytes()) {
			parteDaCifrare[index]=b;
			index++;
		}
		//Aggiungo il messaggio: prende il messaggio da javafx e lo trasforma in bytes
		for(byte b : inputFieldThisClient.getText().getBytes()) {
			parteDaCifrare[index]=b;
			index++;
		}
		//Aggiungo \n al messaggio
		for(byte b : "\n".getBytes()) {
			parteDaCifrare[index]=b;
			index++;
		}
		//Mi segno il messaggio che ho inviato
		System.out.println("INVIO IL MESSAGGIO:");
		outputFieldAllClients.appendText("[io] "+byteToStringAdjLen(parteDaCifrare)+"\n");

		if(!titolo.equals(dest)){//se il messaggio e' per se stesso non viene inviato ma solo stampato
			//Avvertiamo il server di entrare in modalità messaggio
			byte[] entraInMessaggio= new byte[2*BLOCK_SIZE];
			//Scrivo la lunghezza e aggiungo del rumore
			byte[] primoBlocco = generaNonce(lunghezzaPaddata);
			for (int i=0; i<BLOCK_SIZE; i++) {
				entraInMessaggio[i]=primoBlocco[i];
			}
			//Scrivo la modality
			byte[] estMex = rightLength("messaggio".getBytes());
			for(int i=0; i<BLOCK_SIZE; i++) {
				entraInMessaggio[i+BLOCK_SIZE]=estMex[i];
			}
			//Crea il messaggio che conterrà la parte di interesse per il server in cui inserirà la parte cifrata tra clients
			byte[] messaggio = new byte[2*BLOCK_SIZE+lunghezzaPaddata];
			int counter=0;
			byte[] random = generaNonce(0);
			for (int i = 0; i<BLOCK_SIZE; i++) {
				messaggio[counter]=random[i];
				counter++;
			}
			//Aggiungo la persona a cui è rivolto il messaggio
			byte[] destinatario = rightLength(dest.getBytes());
			for(int i=0; i<BLOCK_SIZE; i++) {
				messaggio[counter]=destinatario[i];
				counter++;
			}
			//Cifra il messaggio privato
			if(dest.equals("All")) {
				terabitia.cryptAES(parteDaCifrare, secretTot.getSym(), secretTot.getIV(), "E".getBytes());

System.out.println("Invio a tutti, cripto con k e iv seguenti:");
System.out.println(byteToStringAdjLen(secretTot.getSym()));
System.out.println(byteToStringAdjLen(secretTot.getIV()));
				
			} else { //cerca K e IV nell'hashtable
				terabitia.cryptAES(parteDaCifrare, hashtable.get(byteToStringAdjLen(destinatario)).getSym(), hashtable.get(byteToStringAdjLen(destinatario)).getIV(), "E".getBytes());

System.out.println("Invio privato, cripto con k e iv seguenti:");
System.out.println(byteToStringAdjLen(hashtable.get(byteToStringAdjLen(destinatario)).getSym()));
System.out.println(byteToStringAdjLen(hashtable.get(byteToStringAdjLen(destinatario)).getIV()));
			}

			//Unisco la parte per il server con il messaggio privato (nonce || destinatario || parteDaCifrare)
			for(int i=0; i<lunghezzaPaddata; i++) {
				messaggio[i+2*BLOCK_SIZE]=parteDaCifrare[i];
			}
			
			terabitia.cryptAES(entraInMessaggio, secretServer.getSym(), secretServer.getIV(), "E".getBytes());
			terabitia.cryptAES(messaggio, secretServer.getSym(), secretServer.getIV(), "E".getBytes());
			//Scrive il messaggio al server

			try {
				for(int i=0; i<2*BLOCK_SIZE; i++) {
					doutC.writeByte(entraInMessaggio[i]);
				}
				for(int i=0; i<messaggio.length; i++) {
					doutC.writeByte(messaggio[i]);
				}
			} catch (Exception e) {
				System.out.println(e);
			}
		}
		inputFieldThisClient.setText("");
	}

	class Ascoltatore extends Thread {
		@Override
		public void run() {
			try{
				//Inizializzo il primo flow di blocchi per decidere in che modalità entrerò
				byte[] bloccoStandard = new byte[2*BLOCK_SIZE];

				while(true) {
					Terabitia terabitia = new Terabitia();
					//Il flow è costituito da nonce||modality
					for (int i=0; i<2*BLOCK_SIZE; i++) {
						bloccoStandard[i] = dinC.readByte();
					}
					//Decifro il messaggio
					terabitia.cryptAES(bloccoStandard, secretServer.getSym(), secretServer.getIV(), "D".getBytes());
					//Estraggo l'informazione della modality
					byte[] modality = new byte[BLOCK_SIZE];
					for (int i=0; i<BLOCK_SIZE; i++) {
						modality[i]=bloccoStandard[i+BLOCK_SIZE];
					}

					if("Login".equals(byteToStringAdjLen(modality))) {
						System.out.println("Login in corso...\n");
						byte[] logInFlow = new byte[5*BLOCK_SIZE];

						//Il flow è costituito da un nonce||nomeUtente||chiavePubblica (la chiave pubblica occupa TRE blocchi)
						for (int i=0; i<5*BLOCK_SIZE; i++) {
							logInFlow[i] = dinC.readByte();
						}
						//Decifro il messaggio
						terabitia.cryptAES(logInFlow, secretServer.getSym(), secretServer.getIV(), "D".getBytes());
						//Preparo il nome utente
						byte[] nuovoUtente = new byte[BLOCK_SIZE];
						for (int i=0; i<BLOCK_SIZE; i++) {
							nuovoUtente[i]=logInFlow[i+BLOCK_SIZE];
						}
						//Preparo lo spazio per la chiave pubblica
						byte[] keyPublicNuovoUtente = new byte[pubL];
						for (int i=0; i<pubL; i++) {
							keyPublicNuovoUtente[i] = logInFlow[i+2*BLOCK_SIZE];
						}

						Secret segretoNuovoUtente= new Secret();
						//Costruisce la chiave simmetrica insieme al IV
						try{
							segretoNuovoUtente = new Secret(ECC.doECDH(myPair[0],keyPublicNuovoUtente));
						} catch(Exception e) { 
							e.printStackTrace();
						}

						//Aggiunge il nuovo utente alla hashtable
						hashtable.put(byteToStringAdjLen(nuovoUtente), segretoNuovoUtente);
						//Aggiunge il nuovo utente alla Lista
						Lista.getItems().add(byteToStringAdjLen(nuovoUtente));
						outputFieldAllClients.appendText("Il client "+ byteToStringAdjLen(nuovoUtente) + " ha effettuato il login\n\n");

					} else if("Logout".equals(byteToStringAdjLen(modality))) {
						System.out.println("Logout in corso...\n");
						//prepara il bytearray in cui inserire il messaggio
						byte[] logOutFlow = new byte[2*BLOCK_SIZE];
						//riceve il flow nonce||nomeUtente (il nome utente è di chi si vuole scollegare)
						for (int i=0; i<2*BLOCK_SIZE; i++) {
							logOutFlow[i]=dinC.readByte();
						}
						//decifra
						terabitia.cryptAES(logOutFlow, secretServer.getSym(), secretServer.getIV(), "D".getBytes());
						//estrae l'informazione necessaria
						byte[] nomeUtente = new byte[BLOCK_SIZE];
						for (int i=0; i<BLOCK_SIZE; i++) {
							nomeUtente[i]=logOutFlow[i+BLOCK_SIZE];
						}
						//Rimuove il client dalla hashtable
						hashtable.remove(byteToStringAdjLen(nomeUtente));
						outputFieldAllClients.appendText("Il client "+ byteToStringAdjLen(nomeUtente) + " ha effettuato il logout\n\n");
						//Rimuove il client dalla tendina
						Platform.runLater(()->{
							Lista.setValue(byteToStringAdjLen(username));
							Lista.getItems().remove(byteToStringAdjLen(nomeUtente));
						});

					} else if("Ktot".equals(byteToStringAdjLen(modality))) {

						System.out.println("Richiesta ktot...\n");
						//Crea il blocco che indicherà al Server in che modalità entrare
						byte[] richiestaKtot = new byte[2*BLOCK_SIZE];
						//Legge il flow nonce||nomeUtente che vuole la ktot (mi serve per scegliere la chiave  da usare per cifrare)
						for (int i=0; i<2*BLOCK_SIZE; i++) {
							richiestaKtot[i] = dinC.readByte();
						}
						//Decifro il messaggio ricevuto
						terabitia.cryptAES(richiestaKtot, secretServer.getSym(), secretServer.getIV(), "D".getBytes());
						//Gestisco il messaggio estraendo l'informazione necessaria
						byte[] richiedente = new byte[BLOCK_SIZE];
						for (int i=0; i<BLOCK_SIZE; i++) {
							richiedente[i] = richiestaKtot[i+BLOCK_SIZE];
						}

						//creo l'array byte che invierò per avvertire il server di entrare nella modality ktot
						byte[] entraInKtot = new byte[2*BLOCK_SIZE];
						//Creo il nonce da aggiungere al messaggio
						byte[] random = generaNonce(0);
						for (int i=0; i<BLOCK_SIZE; i++) {
							entraInKtot[i]=random[i];
						}
						//Aggiungo la modality
						byte[] byteModality = rightLength("Ktot".getBytes());
						for (int i=0; i<BLOCK_SIZE; i++) {
							entraInKtot[i+BLOCK_SIZE]=byteModality[i];
						}
						//Cifro con la chiave del server
						terabitia.cryptAES(entraInKtot, secretServer.getSym(), secretServer.getIV(), "E".getBytes());
						//Invio la modality
						for (int i=0; i<2*BLOCK_SIZE; i++) {
							doutC.writeByte(entraInKtot[i]);
						}

						//creo l'arraybyte da inviare contenente le informazioni
						byte[] risposta = new byte[5*BLOCK_SIZE];
						//Aggiungo il nonce
						random = generaNonce(0);
						for (int i = 0; i<BLOCK_SIZE; i++) {
							risposta[i]=random[i];
						}
						//Aggiungo il mio nome che servirà al server per avvertire il client ricevente quale chiave privata usare
						for (int i=0; i<BLOCK_SIZE; i++) {
							risposta[i+BLOCK_SIZE]=richiedente[i];
						}

						//Creo un sottoarray cifrato con la chiave del client
						byte[] sottoArrayByte = new byte[3*BLOCK_SIZE];
						//aggiungo il nonce
						random = generaNonce(0);
						for (int i = 0; i<BLOCK_SIZE; i++) {
							sottoArrayByte[i]=random[i];
						}
						//aggiungo ktot||IVTot
						for (int i=0; i<2*BLOCK_SIZE; i++) {
							sottoArrayByte[i+BLOCK_SIZE]=secretTot.getAll()[i];
						}

						//Cifro il sottoArrayByte con la chiave del client
System.out.println("Cripto con k e iv seguenti:");
System.out.println(byteToStringAdjLen(hashtable.get(byteToStringAdjLen(richiedente)).getSym()));
System.out.println(byteToStringAdjLen(hashtable.get(byteToStringAdjLen(richiedente)).getIV())+"\n");

						terabitia.cryptAES(sottoArrayByte, hashtable.get(byteToStringAdjLen(richiedente)).getSym(), hashtable.get(byteToStringAdjLen(richiedente)).getIV(), "E".getBytes());
						
						//Unisci sottoArrayByte a risposta
						for (int i=0; i<3*BLOCK_SIZE; i++) {
							risposta[i+2*BLOCK_SIZE]=sottoArrayByte[i];
						}
						//cifri risposta
						terabitia.cryptAES(risposta, secretServer.getSym(), secretServer.getIV(), "E".getBytes());
						//Invio la risposta
						for (int i=0; i<5*BLOCK_SIZE; i++) {
							doutC.writeByte(risposta[i]);
						}
					} else if("messaggio".equals(byteToStringAdjLen(modality))) {
						System.out.println("MESSAGGIO RICEVUTO");
						// Estraggo l'informazione da bloccoStandard della lunghezza del messaggio (si noti che la lunghezza è salvata nei primi due byte)
						byte[] byteLunghezzaPaddata = new byte[BLOCK_SIZE];
						for (int i=0; i<2; i++) {
							byteLunghezzaPaddata[i]=bloccoStandard[i];
						}
						//Crea il byte[] lungo a sufficienza per contenere tutto il nonce||mittente||ricevente||messaggioMittente
						byte[] ciphertext = new byte[byteToInt(byteLunghezzaPaddata)+3*BLOCK_SIZE];
						for (int i=0; i<byteToInt(byteLunghezzaPaddata)+3*BLOCK_SIZE; i++) {
							ciphertext[i]=dinC.readByte();
						}
						//Decifra il messaggio
						terabitia.cryptAES(ciphertext, secretServer.getSym(), secretServer.getIV(), "D".getBytes());
						//Estrae il mittente
						byte[] mittente = new byte[BLOCK_SIZE];
						for (int i=0; i<BLOCK_SIZE; i++) {
							mittente[i]=ciphertext[i+BLOCK_SIZE];
						}
						//Estrae il destinatario
						byte[] destinatario = new byte[BLOCK_SIZE];
						for (int i=0; i<BLOCK_SIZE; i++) {
							destinatario[i]=ciphertext[i+2*BLOCK_SIZE];
						}
						//Decide la chiave di decifratura
						
						//Isola il messaggio del mittente
						byte[] messaggioMittente = new byte[byteToInt(byteLunghezzaPaddata)];
						for (int i=0; i<byteToInt(byteLunghezzaPaddata); i++) {
							messaggioMittente[i]=ciphertext[i+3*BLOCK_SIZE];
						}

						//Decifra il messaggio
						if("All".equals(byteToStringAdjLen(destinatario))){ 
							terabitia.cryptAES(messaggioMittente, secretTot.getSym(), secretTot.getIV(), "D".getBytes());
System.out.println("Pubblico. Decripto con k e iv seguenti:");
System.out.println(byteToStringAdjLen(secretTot.getSym()));
System.out.println(byteToStringAdjLen(secretTot.getIV()));
						} 
						else{//cerca K e IV nell'hashtable
							Secret paramDest= hashtable.get(byteToStringAdjLen(destinatario));
System.out.println("Privato. Decripto con k e iv seguenti:");
System.out.println(byteToStringAdjLen(hashtable.get(byteToStringAdjLen(mittente)).getSym()));
System.out.println(byteToStringAdjLen(hashtable.get(byteToStringAdjLen(mittente)).getIV()));

							terabitia.cryptAES(messaggioMittente, hashtable.get(byteToStringAdjLen(mittente)).getSym(), hashtable.get(byteToStringAdjLen(mittente)).getIV(), "D".getBytes());
						}
						//Gestisce il messaggio
						outputFieldAllClients.appendText(byteToStringAdjLen(messaggioMittente)+"\n");

						if(0x00==0x01) break;
					} else {
						System.out.println("Something went wrong in modality");
					}
				}
				sock.close(); 
			} catch(IOException e) {
				System.out.println("Il client ha eseguito il logout");
			}
		}
	}
}
