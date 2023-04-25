#include <string.h>
#include <inttypes.h>
#include "Terabitia.h"
#include "AES_Lib.h"

JNIEXPORT void JNICALL Java_Terabitia_Setup
  (JNIEnv *env, jobject obj, jbyteArray masterKeyj, jbyteArray roundKeyj) {
	jbyte *masterKey = (*env)->GetByteArrayElements(env, masterKeyj, 0);
	jbyte *roundKey = (*env)->GetByteArrayElements(env, roundKeyj, 0);

	uint8_t roundKeyBuf[NR_ROUNDS+1][WORDS_IN_KEY][BYTES_IN_WORD];

	uint8_t masterKeyBuf[WORDS_IN_KEY][BYTES_IN_WORD];
	for(int i=0; i<WORDS_IN_KEY; i++) {
		for(int j=0; j<BYTES_IN_WORD; j++) {
			masterKeyBuf[i][j]=masterKey[BYTES_IN_WORD*i+j];
		}
	}

	roundKeyGen(roundKeyBuf, masterKeyBuf);

	for(int i=0; i<NR_ROUNDS+1; i++) {
		for(int j=0; j<WORDS_IN_KEY; j++) {
			for(int k=0; k<BYTES_IN_WORD; k++) {
				roundKey[BYTES_IN_WORD*WORDS_IN_KEY*i+BYTES_IN_WORD*j+k]=roundKeyBuf[i][j][k];
			}
		}
	}

	(*env)->ReleaseByteArrayElements(env, roundKeyj, roundKey, 0);
}


JNIEXPORT void JNICALL Java_Terabitia_cryptAES
  (JNIEnv *env, jobject obj, jbyteArray messagej, jbyteArray roundKeyj, jbyteArray IVj, jbyteArray modalityj) {
	//La chiave e l'initial vector in realtà sono passati come esadecimali
	jsize messagelength=(*env)->GetArrayLength(env,messagej); //E' già paddato
	jbyte *message = (*env)->GetByteArrayElements(env, messagej, 0);
	jbyte *roundKey = (*env)->GetByteArrayElements(env, roundKeyj, 0);
	jbyte *IV = (*env)->GetByteArrayElements(env, IVj, 0);
	jbyte *modality = (*env)->GetByteArrayElements(env, modalityj, 0);

	uint8_t roundKeyBuf[NR_ROUNDS+1][WORDS_IN_KEY][BYTES_IN_WORD];
	for(int i=0; i<NR_ROUNDS+1; i++) {
		for(int j=0; j<WORDS_IN_KEY; j++) {
			for(int k=0; k<BYTES_IN_WORD; k++) {
				roundKeyBuf[i][j][k]=roundKey[BYTES_IN_WORD*WORDS_IN_KEY*i+BYTES_IN_WORD*j+k];
			}
		}
	}

	if (*(modality) == 'E') { 
		encryptCBC(message, messagelength, roundKeyBuf, IV); 
	} else if (*(modality) == 'D') {
		decryptCBC(message, messagelength, roundKeyBuf, IV);
	}

	(*env)->ReleaseByteArrayElements(env, messagej, message, 0);
	return;
}




