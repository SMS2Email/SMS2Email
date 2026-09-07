package io.github.sms2email.sms2email;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * A custom TrustManager that trusts certificates from both the Android system trust store and the
 * user's certificate store. This allows the app to work with certificates signed by custom CAs that
 * the user has imported into their device.
 */
public class AndroidTrustManager implements X509TrustManager {
  private final X509TrustManager defaultTrustManager;

  public AndroidTrustManager() throws KeyStoreException, NoSuchAlgorithmException {
    // Get the Android KeyStore which includes both system and user certificates
    KeyStore keyStore = KeyStore.getInstance("AndroidCAStore");
    try {
      keyStore.load(null, null);
    } catch (Exception e) {
      // If loading fails, continue with null keystore which will use system defaults
      keyStore = null;
    }

    // Initialize the trust manager factory with the Android keystore
    TrustManagerFactory tmf =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tmf.init(keyStore);

    // Get the X509TrustManager from the factory
    TrustManager[] trustManagers = tmf.getTrustManagers();
    X509TrustManager foundTrustManager = null;
    for (TrustManager tm : trustManagers) {
      if (tm instanceof X509TrustManager) {
        foundTrustManager = (X509TrustManager) tm;
        break;
      }
    }

    if (foundTrustManager == null) {
      throw new KeyStoreException("Could not find X509TrustManager");
    }

    this.defaultTrustManager = foundTrustManager;
  }

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType)
      throws CertificateException {
    defaultTrustManager.checkClientTrusted(chain, authType);
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType)
      throws CertificateException {
    defaultTrustManager.checkServerTrusted(chain, authType);
  }

  @Override
  public X509Certificate[] getAcceptedIssuers() {
    return defaultTrustManager.getAcceptedIssuers();
  }
}
