package com.limelight.nvstream.http;

import static kr.motd.gleamstream.Panic.panic;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.function.Supplier;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.motd.gleamstream.Preferences;

public class DefaultCryptoProvider implements CryptoProvider {

    private static final Logger logger = LoggerFactory.getLogger(DefaultCryptoProvider.class);

    private final File certFile = new File(Preferences.SETTINGS_DIR + File.separator + "client.crt");
    private final File keyFile = new File(Preferences.SETTINGS_DIR + File.separator + "client.key");

    private X509Certificate cert;
    private RSAPrivateKey key;
    private byte[] pemCertBytes;

    static {
        // Install the Bouncy Castle provider
        Security.addProvider(new BouncyCastleProvider());
    }

    private static byte[] loadFileToBytes(File f) {
        if (!f.exists()) {
            return null;
        }

        try {
            DataInputStream fin = new DataInputStream(new FileInputStream(f));
            byte[] fileData = new byte[(int) f.length()];
            fin.readFully(fileData);
            fin.close();
            return fileData;
        } catch (IOException e) {
            throw panic("Failed to read a file: " + f, e);
        }
    }

    private boolean loadCertKeyPair() {
        byte[] certBytes = loadFileToBytes(certFile);
        byte[] keyBytes = loadFileToBytes(keyFile);

        // If either file was missing, we definitely can't succeed
        if (certBytes == null || keyBytes == null) {
            logger.info("Missing cert or key; need to generate a new one");
            return false;
        }

        try {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509", "BC");
            cert = (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(certBytes));
            pemCertBytes = certBytes;

            KeyFactory keyFactory = KeyFactory.getInstance("RSA", "BC");
            key = (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (CertificateException e) {
            // May happen if the cert is corrupt
            logger.warn("Corrupted certificate");
            return false;
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            // Should never happen
            throw panic(e);
        } catch (InvalidKeySpecException e) {
            throw panic("Corrupted key?", e);
        }

        logger.info("Loaded key pair from disk");
        return true;
    }

    private boolean generateCertKeyPair() {
        byte[] snBytes = new byte[8];
        new SecureRandom().nextBytes(snBytes);

        KeyPair keyPair;
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC");
            keyPairGenerator.initialize(2048);
            keyPair = keyPairGenerator.generateKeyPair();
        } catch (Exception e) {
            // Should never happen
            throw panic(e);
        }

        Date now = new Date();

        // Expires in 20 years
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(now);
        calendar.add(Calendar.YEAR, 20);
        Date expirationDate = calendar.getTime();

        BigInteger serial = new BigInteger(snBytes).abs();

        X500NameBuilder nameBuilder = new X500NameBuilder(BCStyle.INSTANCE);
        nameBuilder.addRDN(BCStyle.CN, "NVIDIA GameStream Client");
        X500Name name = nameBuilder.build();

        X509v3CertificateBuilder certBuilder = new X509v3CertificateBuilder(name, serial, now, expirationDate,
                                                                            Locale.ENGLISH, name,
                                                                            SubjectPublicKeyInfo.getInstance(
                                                                                    keyPair.getPublic()
                                                                                           .getEncoded()));
        try {
            ContentSigner sigGen = new JcaContentSignerBuilder("SHA1withRSA").setProvider(
                    BouncyCastleProvider.PROVIDER_NAME).build(keyPair.getPrivate());
            cert = new JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME)
                                                    .getCertificate(certBuilder.build(sigGen));
            key = (RSAPrivateKey) keyPair.getPrivate();
        } catch (Exception e) {
            // Nothing should go wrong here
            throw panic(e);
        }

        logger.info("Generated a new key pair");

        // Save the resulting pair
        saveCertKeyPair();

        return true;
    }

    private void saveCertKeyPair() {
        try {
            FileOutputStream certOut = new FileOutputStream(certFile);
            FileOutputStream keyOut = new FileOutputStream(keyFile);

            // Write the certificate in OpenSSL PEM format (important for the server)
            StringWriter strWriter = new StringWriter();
            JcaPEMWriter pemWriter = new JcaPEMWriter(strWriter);
            pemWriter.writeObject(cert);
            pemWriter.close();

            // Line endings MUST be UNIX for the PC to accept the cert properly
            OutputStreamWriter certWriter = new OutputStreamWriter(certOut);
            String pemStr = strWriter.getBuffer().toString();
            for (int i = 0; i < pemStr.length(); i++) {
                char c = pemStr.charAt(i);
                if (c != '\r') {
                    certWriter.append(c);
                }
            }
            certWriter.close();

            // Write the private out in PKCS8 format
            keyOut.write(key.getEncoded());

            certOut.close();
            keyOut.close();

            logger.info("Saved the generated key pair");
        } catch (IOException e) {
            // This isn't good because it means we'll have
            // to re-pair next time
            logger.warn("Failed to save the generated key pair", e);
        }
    }

    @Override
    public synchronized X509Certificate getClientCertificate() {
        return lazyGet(() -> cert);
    }

    @Override
    public synchronized RSAPrivateKey getClientPrivateKey() {
        return lazyGet(() -> key);
    }

    private <T> T lazyGet(Supplier<T> supplier) {
        // Use a lock here to ensure only one guy will be generating or loading
        // the certificate and key at a time
        final T value = supplier.get();
        if (value != null) {
            return value;
        }

        // No loaded key yet, let's see if we have one on disk
        if (loadCertKeyPair()) {
            // Got one
            return supplier.get();
        }

        // Try to generate a new key pair
        if (!generateCertKeyPair()) {
            // Failed
            return null;
        }

        // Load the generated pair
        loadCertKeyPair();
        return supplier.get();
    }

    @Override
    public synchronized byte[] getPemEncodedClientCertificate() {
        getClientCertificate();

        // Return a cached value if we have it
        return pemCertBytes;
    }
}
