package com.limelight.nvstream.input.cipher;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.security.Provider.Service;
import java.security.Security;

import javax.crypto.Cipher;
import javax.crypto.CipherSpi;

public final class FastAesGcmInputCipher extends AesGcmInputCipher {

    private final Service service;
    private final Method engineSetPadding;
    private final Method engineSetMode;
    private final Constructor<Cipher> constructor;

    public FastAesGcmInputCipher() throws Exception {
        service = Security.getProvider("SunJCE").getService("Cipher", "AES");
        if (service == null) {
            throw new IllegalStateException("could not find SunJCE AES cipher");
        }

        engineSetPadding = CipherSpi.class.getDeclaredMethod("engineSetPadding", String.class);
        engineSetPadding.setAccessible(true);

        engineSetMode = CipherSpi.class.getDeclaredMethod("engineSetMode", String.class);
        engineSetMode.setAccessible(true);

        constructor = Cipher.class.getDeclaredConstructor(CipherSpi.class, String.class);
        constructor.setAccessible(true);

        // Try to create a new Cipher to see if the fast path actually works.
        newCipher();
    }

    @Override
    protected Cipher newCipher() throws Exception {
        CipherSpi cipherSpi = (CipherSpi) service.newInstance(null);
        engineSetPadding.invoke(cipherSpi, "NoPadding");
        engineSetMode.invoke(cipherSpi, "GCM");
        return constructor.newInstance(cipherSpi, "AES/GCM/NoPadding");
    }
}
