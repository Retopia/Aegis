import javafx.concurrent.Task;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.BadPaddingException;

import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

public class AES {
    // Enum to specify different types of errors for better reporting
    public enum ProcessingError {
        NONE(""),
        INVALID_KEY("Invalid password"),
        FILE_TOO_LARGE("File is too large to process"),
        FILE_ACCESS_ERROR("Cannot access file"),
        DECRYPTION_ERROR("Unable to decrypt file. The file might be corrupted or the password is incorrect"),
        ENCRYPTION_ERROR("Unable to encrypt file"),
        UNKNOWN_ERROR("An unknown error occurred");

        private final String message;

        ProcessingError(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    // Result class to provide more information about the operation
    public static class ProcessingResult {
        private final boolean success;
        private final ProcessingError error;
        private final String details;

        public ProcessingResult(boolean success, ProcessingError error, String details) {
            this.success = success;
            this.error = error;
            this.details = details;
        }

        public boolean isSuccess() { return success; }
        public ProcessingError getError() { return error; }
        public String getDetails() { return details; }
    }

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

    public static ProcessingResult processFile(File original, File aegisFile, String secret, boolean isEncryption, Task<Void> task) {
        if (task.isCancelled()) {
            return new ProcessingResult(false, ProcessingError.NONE, "Operation cancelled by user");
        }

        // Check file size before processing
        long fileSize = original.length();
        long maxSize = Runtime.getRuntime().maxMemory() - (100 * 1024 * 1024); // Leave 100MB buffer
        if (fileSize > maxSize) {
            return new ProcessingResult(false, ProcessingError.FILE_TOO_LARGE,
                    String.format("File size %.2f MB exceeds maximum supported size %.2f MB",
                            fileSize / (1024.0 * 1024.0), maxSize / (1024.0 * 1024.0)));
        }

        FileInputStream inputStream = null;
        FileOutputStream outputStream = null;
        byte[] input = null;

        try {
            setKey(secret);
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(isEncryption ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, secretKey);

            // Read file
            try {
                inputStream = new FileInputStream(original);
                input = Files.readAllBytes(Paths.get(original.toURI()));
                inputStream.close();
            } catch (IOException e) {
                return new ProcessingResult(false, ProcessingError.FILE_ACCESS_ERROR,
                        "Could not read file: " + e.getMessage());
            }

            if (task.isCancelled()) {
                return new ProcessingResult(false, ProcessingError.NONE, "Operation cancelled by user");
            }

            // Process file
            byte[] outputBytes;
            try {
                outputBytes = cipher.doFinal(input);
            } catch (BadPaddingException e) {
                return new ProcessingResult(false,
                        isEncryption ? ProcessingError.ENCRYPTION_ERROR : ProcessingError.DECRYPTION_ERROR,
                        e.getMessage());
            } catch (IllegalBlockSizeException e) {
                return new ProcessingResult(false,
                        isEncryption ? ProcessingError.ENCRYPTION_ERROR : ProcessingError.DECRYPTION_ERROR,
                        "Invalid data block size: " + e.getMessage());
            }

            // Write output
            try {
                outputStream = new FileOutputStream(aegisFile);
                outputStream.write(outputBytes);
                outputStream.close();
            } catch (IOException e) {
                return new ProcessingResult(false, ProcessingError.FILE_ACCESS_ERROR,
                        "Could not write output file: " + e.getMessage());
            }

            return new ProcessingResult(true, ProcessingError.NONE, "");

        } catch (InvalidKeyException e) {
            return new ProcessingResult(false, ProcessingError.INVALID_KEY, e.getMessage());
        } catch (Exception e) {
            return new ProcessingResult(false, ProcessingError.UNKNOWN_ERROR, e.getMessage());
        } finally {
            // Clean up resources
            try {
                if (inputStream != null) inputStream.close();
                if (outputStream != null) outputStream.close();
            } catch (IOException e) {
                System.err.println("Error closing streams: " + e.getMessage());
            }
        }
    }

    public static void secureDelete(File original, File aegisFile, boolean isProcessSuccessful) {
        if (isProcessSuccessful) {
            try {
                // Get file size
                long length = original.length();

                // First overwrite with zeros
                try (FileOutputStream out = new FileOutputStream(original)) {
                    byte[] zeros = new byte[4096];
                    long remaining = length;
                    while (remaining > 0) {
                        int size = (int) Math.min(zeros.length, remaining);
                        out.write(zeros, 0, size);
                        remaining -= size;
                    }
                }

                // Then overwrite with random data
                SecureRandom random = new SecureRandom();
                try (FileOutputStream out = new FileOutputStream(original)) {
                    byte[] randomData = new byte[4096];
                    long remaining = length;
                    while (remaining > 0) {
                        random.nextBytes(randomData);
                        int size = (int) Math.min(randomData.length, remaining);
                        out.write(randomData, 0, size);
                        remaining -= size;
                    }
                }

                // Delete and rename
                if (!original.delete()) {
                    System.err.println("Warning: Could not delete original file");
                    return;
                }
                if (!aegisFile.renameTo(original)) {
                    System.err.println("Error: Could not rename temporary file");
                }
            } catch (IOException e) {
                System.err.println("Error during secure delete: " + e.getMessage());
            }
        } else {
            // Clean up temporary file
            if (!aegisFile.delete()) {
                System.err.println("Warning: Could not delete temporary file");
            }
        }
    }
}