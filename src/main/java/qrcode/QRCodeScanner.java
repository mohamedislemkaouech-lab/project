package qrcode;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class QRCodeScanner {

    private static final Logger logger = LoggerFactory.getLogger(QRCodeScanner.class);
    private static final Gson gson = new Gson();

    private final Map<DecodeHintType, Object> hints;
    private final MultiFormatReader reader;

    public QRCodeScanner() {
        this.hints = createDefaultHints();
        this.reader = new MultiFormatReader();
        this.reader.setHints(hints);
    }

    private Map<DecodeHintType, Object> createDefaultHints() {
        Map<DecodeHintType, Object> hints = new HashMap<>();
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        return hints;
    }

    public String scanFromFile(String filePath) throws IOException, NotFoundException {
        logger.info("Scanning QR code from file: {}", filePath);

        File file = new File(filePath);
        if (!file.exists()) {
            throw new IOException("File not found: " + filePath);
        }

        BufferedImage image = ImageIO.read(file);
        if (image == null) {
            throw new IOException("Could not read image from file: " + filePath);
        }

        String result = scanFromImage(image);
        logger.info("Successfully scanned QR code from file");
        return result;
    }

    public String scanFromImage(BufferedImage image) throws NotFoundException {
        logger.debug("Scanning QR code from BufferedImage");

        if (image == null) {
            throw new IllegalArgumentException("Image cannot be null");
        }

        BufferedImageLuminanceSource source = new BufferedImageLuminanceSource(image);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

        Result result = reader.decode(bitmap);
        String text = result.getText();

        logger.debug("Successfully decoded QR code: {} characters", text.length());
        return text;
    }

    public String scanFromBytes(byte[] imageBytes) throws IOException, NotFoundException {
        logger.debug("Scanning QR code from byte array");

        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalArgumentException("Image bytes cannot be null or empty");
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
        BufferedImage image = ImageIO.read(bais);

        if (image == null) {
            throw new IOException("Could not parse image from byte array");
        }

        return scanFromImage(image);
    }

    public String scanFromBase64(String base64Image) throws IOException, NotFoundException {
        logger.debug("Scanning QR code from Base64 string");

        if (base64Image == null || base64Image.isEmpty()) {
            throw new IllegalArgumentException("Base64 string cannot be null or empty");
        }

        String base64Data = base64Image;
        if (base64Image.startsWith("data:image")) {
            int commaIndex = base64Image.indexOf(',');
            if (commaIndex != -1) {
                base64Data = base64Image.substring(commaIndex + 1);
            }
        }

        byte[] imageBytes = Base64.getDecoder().decode(base64Data);
        return scanFromBytes(imageBytes);
    }

    public AuthQRData scanAuthQRCode(String filePath) throws IOException, NotFoundException {
        logger.info("Scanning authentication QR code");

        String qrText = scanFromFile(filePath);
        return parseAuthQRData(qrText);
    }

    public PairingQRData scanPairingQRCode(String filePath) throws IOException, NotFoundException {
        logger.info("Scanning pairing QR code");

        String qrText = scanFromFile(filePath);
        return parsePairingQRData(qrText);
    }

    public AuthQRData parseAuthQRData(String jsonData) throws JsonSyntaxException {
        logger.debug("Parsing authentication QR data");

        JsonObject json = JsonParser.parseString(jsonData).getAsJsonObject();

        AuthQRData authData = new AuthQRData();
        authData.token = json.get("token").getAsString();
        authData.url = json.get("url").getAsString();
        authData.expirationTime = json.get("exp").getAsLong();
        authData.type = json.get("type").getAsString();

        logger.info("Parsed auth QR code - Type: {}, Expires: {}", authData.type, authData.expirationTime);
        return authData;
    }

    public PairingQRData parsePairingQRData(String jsonData) throws JsonSyntaxException {
        logger.debug("Parsing pairing QR data");

        JsonObject json = JsonParser.parseString(jsonData).getAsJsonObject();

        PairingQRData pairingData = new PairingQRData();
        pairingData.code = json.get("code").getAsString();
        pairingData.deviceId = json.get("device").getAsString();
        pairingData.url = json.get("url").getAsString();
        pairingData.type = json.get("type").getAsString();

        logger.info("Parsed pairing QR code - Device: {}, Type: {}", pairingData.deviceId, pairingData.type);
        return pairingData;
    }

    public boolean isTokenExpired(AuthQRData authData) {
        if (authData == null) {
            return true;
        }
        long currentTime = System.currentTimeMillis();
        boolean expired = currentTime > authData.expirationTime;

        if (expired) {
            logger.warn("Token expired at: {}, Current time: {}", authData.expirationTime, currentTime);
        }

        return expired;
    }

    public long getTimeUntilExpiration(AuthQRData authData) {
        if (authData == null) {
            return 0;
        }
        long currentTime = System.currentTimeMillis();
        long remaining = (authData.expirationTime - currentTime) / 1000;
        return Math.max(0, remaining);
    }

    public static class AuthQRData {
        public String token;
        public String url;
        public long expirationTime;
        public String type;

        @Override
        public String toString() {
            return String.format("AuthQRData{token='%s...', url='%s', exp=%d, type='%s'}",
                    token != null && token.length() > 8 ? token.substring(0, 8) : token,
                    url, expirationTime, type);
        }
    }

    public static class PairingQRData {
        public String code;
        public String deviceId;
        public String url;
        public String type;

        @Override
        public String toString() {
            return String.format("PairingQRData{code='%s...', device='%s', url='%s', type='%s'}",
                    code != null && code.length() > 8 ? code.substring(0, 8) : code,
                    deviceId, url, type);
        }
    }

    public static void main(String[] args) {
        System.out.println("=".repeat(50));
        System.out.println("QR Code Scanner - Test");
        System.out.println("=".repeat(50));

        try {
            QRCodeScanner scanner = new QRCodeScanner();

            // Test scanning
            String testData = "Hello from QR Code Scanner!";

            // Create a test QR code
            QRCodeGenerator generator = new QRCodeGenerator();
            generator.generateQRCodeToFile(testData, "scan-test.png");

            System.out.println("\nScanning test QR code...");
            String result = scanner.scanFromFile("scan-test.png");

            System.out.println("Scanned data: " + result);
            System.out.println("Match: " + testData.equals(result));

            System.out.println("\n✅ Test completed!");

        } catch (Exception e) {
            System.err.println("\n❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}