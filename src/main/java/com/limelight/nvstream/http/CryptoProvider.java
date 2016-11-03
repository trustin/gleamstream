package com.limelight.nvstream.http;

import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;

public interface CryptoProvider {
    X509Certificate getClientCertificate();
    RSAPrivateKey getClientPrivateKey();
    byte[] getPemEncodedClientCertificate();
}
