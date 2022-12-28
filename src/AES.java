import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.BadPaddingException;

import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

public class AES {

    private static SecretKeySpec secretKey;
    private static byte[] key;

    public static void setKey(String myKey) {
        MessageDigest sha = null;
        try {
            key = myKey.getBytes("UTF-8");
            sha = MessageDigest.getInstance("SHA-1");
            key = sha.digest(key);
            key = Arrays.copyOf(key, 16);
            secretKey = new SecretKeySpec(key, "AES");
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    public static boolean processFile(File original, File aegisFile, String secret, boolean isEncryption) {
        try {
            setKey(secret);
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            if (isEncryption) {
                cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            } else {
                cipher.init(Cipher.DECRYPT_MODE, secretKey);
            }

            // Performs encryption/decryption on the file
            try (FileInputStream in = new FileInputStream(original);
                 FileOutputStream out = new FileOutputStream(aegisFile)) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(cipher.update(buffer, 0, len));
                }
                out.write(cipher.doFinal());
            }

            return true;
        } catch (IOException | InvalidKeyException | NoSuchAlgorithmException | BadPaddingException |
                 IllegalBlockSizeException | NoSuchPaddingException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    // Fill the original file with random bytes to make its content more difficult to recover
    public static void secureDelete(File original, File aegisFile, boolean isProcessSuccessful) {
        if (isProcessSuccessful) {
            SecureRandom random = new SecureRandom();
            try (FileInputStream in = new FileInputStream(original);
                 FileOutputStream out = new FileOutputStream(original)) {
                // Determine the size of the file
                long size = in.getChannel().size();

                // Overwrite the file with random bytes
                byte[] buffer = new byte[1024];
                while (size > 0) {
                    random.nextBytes(buffer);
                    int len = (int) Math.min(buffer.length, size);
                    out.write(buffer, 0, len);
                    size -= len;
                }


            } catch (IOException e) {
                System.out.println(e.getMessage());
            }

            // Delete the file then rename the temporary Aegis file to the original
            original.delete();
            aegisFile.renameTo(original);
        } else {
            // If the encryption/decryption failed then just delete the temporary Aegis file
            aegisFile.delete();
        }
    }
}
