package com.limelight.nvstream.input.cipher;

import static kr.motd.gleamstream.Panic.panic;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;

public final class AesCbcInputCipher implements InputCipher {

    private Cipher cipher;

    @Override
    public void initialize(SecretKey key, byte[] iv, int ivOffset, int ivLength) {
        try {
            cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv, ivOffset, ivLength));
        } catch (Exception e) {
            throw panic(e);
        }
    }

    @Override
    public int getEncryptedSize(int plaintextSize) {
        // CBC requires padding to the next multiple of 16
        return (plaintextSize + 15) / 16 * 16;
    }

    private int inPlacePadData(byte[] data, int length) {
        // This implements the PKCS7 padding algorithm

        if (length % 16 == 0) {
            // Already a multiple of 16
            return length;
        }

        int paddedLength = getEncryptedSize(length);
        byte paddingByte = (byte) (16 - length % 16);

        for (int i = length; i < paddedLength; i++) {
            data[i] = paddingByte;
        }

        return paddedLength;
    }

    @Override
    public void encrypt(byte[] inputData, int inputLength, byte[] outputData, int outputOffset) {
        int encryptedLength = inPlacePadData(inputData, inputLength);
        try {
            cipher.update(inputData, 0, encryptedLength, outputData, outputOffset);
        } catch (ShortBufferException e) {
            throw panic(e);
        }
    }
}
