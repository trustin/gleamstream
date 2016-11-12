package com.limelight.nvstream.input.cipher;

import static kr.motd.gleamstream.Panic.panic;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class AesGcmInputCipher implements InputCipher {

    private static final int TAG_LENGTH = 16;

    private SecretKey key;
    private byte[] iv;
    private int ivOffset;
    private int ivLength;

    private final byte[] rawCipherOut = new byte[128];

    @Override
    public final int getEncryptedSize(int plaintextSize) {
        // GCM uses no padding + 16 bytes tag for message authentication
        return plaintextSize + TAG_LENGTH;
    }

    @Override
    public final void initialize(SecretKey key, byte[] iv, int ivOffset, int ivLength) {
        this.key = key;
        this.iv = iv;
        this.ivOffset = ivOffset;
        this.ivLength = ivLength;
    }

    @Override
    public final void encrypt(byte[] inputData, int inputOffset, int inputLength,
                              byte[] outputData, int outputOffset) {
        // Reconstructing the cipher on every invocation really sucks but we have to do it
        // because of the way NVIDIA is using GCM where each message is tagged. Java doesn't
        // have an easy way that I know of to get a tag out mid-stream.
        try {
            final Cipher cipher = newCipher();
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH * 8, iv, ivOffset, ivLength));

            final int outLen = cipher.doFinal(inputData, inputOffset, inputLength, rawCipherOut);
            assert outLen == getEncryptedSize(inputLength);

            // This is also non-ideal. Java gives us <ciphertext><tag> but we want to send <tag><ciphertext>
            // so we'll take the output and arraycopy it into the right spot in the output buffer
            System.arraycopy(rawCipherOut, inputLength, outputData, outputOffset, 16);
            System.arraycopy(rawCipherOut, 0, outputData, outputOffset + 16, inputLength);
        } catch (Exception e) {
            throw panic(e);
        }
    }

    protected Cipher newCipher() throws Exception {
        return Cipher.getInstance("AES/GCM/NoPadding");
    }
}
