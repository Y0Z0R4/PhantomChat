import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.util.Base64;
import java.security.SecureRandom;

public class AESHelper {

    private static final String ALGORITHM = "AES";
    private static final String CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final int AES_KEY_SIZE = 256;

    public static SecretKey generateSecretKey() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
        keyGenerator.init(AES_KEY_SIZE); // AES-256
        return keyGenerator.generateKey();
    }

    public static String encrypt(String data, SecretKey secretKey) throws Exception {
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        IvParameterSpec iv = new IvParameterSpec(generateRandomIv());
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv);

        byte[] encryptedData = cipher.doFinal(data.getBytes());
        byte[] ivBytes = iv.getIV();
        byte[] encryptedDataWithIv = new byte[ivBytes.length + encryptedData.length];
        System.arraycopy(ivBytes, 0, encryptedDataWithIv, 0, ivBytes.length);
        System.arraycopy(encryptedData, 0, encryptedDataWithIv, ivBytes.length, encryptedData.length);

        return Base64.getEncoder().encodeToString(encryptedDataWithIv);
    }

    public static String decrypt(String encryptedData, SecretKey secretKey) throws Exception {
        byte[] encryptedDataBytes = Base64.getDecoder().decode(encryptedData);
        byte[] ivBytes = new byte[16]; // 128-bit IV
        System.arraycopy(encryptedDataBytes, 0, ivBytes, 0, ivBytes.length);

        IvParameterSpec iv = new IvParameterSpec(ivBytes);
        byte[] actualEncryptedData = new byte[encryptedDataBytes.length - ivBytes.length];
        System.arraycopy(encryptedDataBytes, ivBytes.length, actualEncryptedData, 0, actualEncryptedData.length);

        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, iv);

        byte[] decryptedData = cipher.doFinal(actualEncryptedData);
        return new String(decryptedData);
    }

    private static byte[] generateRandomIv() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] iv = new byte[16]; // 128-bit IV
        secureRandom.nextBytes(iv);
        return iv;
    }
}
