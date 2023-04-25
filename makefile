all: ECC.class Terabitia.class Secret.class ChiaviS.class libTerabitia.so ClientThreadLauncher.class ServerThreadLauncher.class 

ECC.class: ECC.java
	javac8 ECC.java

Secret.class: Secret.java
	javac8 Secret.java

ChiaviS.class: ChiaviS.java
	javac8 ChiaviS.java

ClientThreadLauncher.class: ClientThreadLauncher.java
	javac8 ClientThreadLauncher.java

ServerThreadLauncher.class: ServerThreadLauncher.java
	javac8 ServerThreadLauncher.java -Xlint

Terabitia.class: Terabitia.java
	javac8 Terabitia.java

Terabitia.h: Terabitia.java
	javah8 Terabitia

AES_Lib.o: AES_Lib.c
	gcc -c -fPIC AES_Lib.c

libTerabitia.so: Terabitia.c AES_Lib.o Terabitia.h
	gcc -I/usr/lib/jvm/jdk1.8.0_202/include -I/usr/lib/jvm/jdk1.8.0_202/include/linux -fPIC -shared -z noexecstack\
		Terabitia.c AES_Lib.o -o libTerabitia.so


