import java.math.BigInteger;
import java.security.*;
import javax.crypto.*;
import java.util.Base64;

public class DiffieHellman {

    // Diffie-Hellman parameters: p (prime), g (generator)
    private static final BigInteger P = new BigInteger(
            "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63BFFFFFFFFFFFFFFFF",
            16
    );
    private static final BigInteger G = new BigInteger("2");

    private KeyPair keyPair;

    public DiffieHellman() throws NoSuchAlgorithmException {
        // Generate the key pair (private key, public key)
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("DH");
        keyPairGenerator.initialize(2048); // 2048-bit key size
        this.keyPair = keyPairGenerator.generateKeyPair();
    }

    public BigInteger getPublicKey() {
        // Return the public key as BigInteger
        return new BigInteger(1, keyPair.getPublic().getEncoded());
    }

    public BigInteger getPrivateKey() {
        // Return the private key as BigInteger
        return new BigInteger(1, keyPair.getPrivate().getEncoded());
    }

    public BigInteger generateSharedSecret(BigInteger otherPublicKey) {
        // Use the other party's public key and our private key to generate the shared secret
        return otherPublicKey.modPow(getPrivateKey(), P);
    }

    public static String generateSharedKey(BigInteger sharedSecret) {
        // Hash the shared secret using SHA-256 to derive a 256-bit key for AES-256
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] key = sha256.digest(sharedSecret.toByteArray());
            return Base64.getEncoder().encodeToString(key);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
