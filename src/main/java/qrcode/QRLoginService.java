package qrcode;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class QRLoginService {

    // Map token → expiration
    private static final Map<String, Long> tokenStore = new HashMap<>();
    private static final long TOKEN_EXPIRATION_MS = 5 * 60 * 1000; // 5 minutes

    // Générer token aléatoire
    public static String createToken() {
        String token = UUID.randomUUID().toString();
        long expiration = Instant.now().toEpochMilli() + TOKEN_EXPIRATION_MS;
        tokenStore.put(token, expiration);
        return token;
    }

    // Vérifier token côté serveur
    public static boolean validateToken(String token) {
        Long expiration = tokenStore.get(token);
        if (expiration == null) return false;        // token inconnu
        if (Instant.now().toEpochMilli() > expiration) {
            tokenStore.remove(token);               // token expiré
            return false;
        }
        tokenStore.remove(token);                   // supprimer pour éviter replay
        return true;
    }

    // Générer QR code PNG à partir du token
    public static void generateQRCode(String token, String filePath, int width, int height)
            throws WriterException, IOException {

        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(token, BarcodeFormat.QR_CODE, width, height);

        Path path = FileSystems.getDefault().getPath(filePath);
        MatrixToImageWriter.writeToPath(bitMatrix, "PNG", path);
    }

    // Générer QR code en mémoire (byte array)
    public static byte[] generateQRCodeBytes(String token, int width, int height)
            throws WriterException, IOException {

        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(token, BarcodeFormat.QR_CODE, width, height);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", baos);
        return baos.toByteArray();
    }
}
