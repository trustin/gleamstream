package com.limelight.nvstream.input.cipher;

import javax.crypto.SecretKey;

public interface InputCipher {
    void initialize(SecretKey key, byte[] iv, int ivOffset, int ivLength);
    int getEncryptedSize(int plaintextSize);
    void encrypt(byte[] inputData, int inputLength, byte[] outputData, int outputOffset);
}
