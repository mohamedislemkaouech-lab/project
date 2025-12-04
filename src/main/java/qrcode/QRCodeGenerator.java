package qrcode;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * QR Code Generator for Modern Authentication System
 * Now generates URLs for mobile scanning
 */
public class QRCodeGenerator {

    private static final Logger logger = LoggerFactory.getLogger(QRCodeGenerator.class);
    private static final int DEFAULT_WIDTH = 400;
    private static final int DEFAULT_HEIGHT = 400;
    private static final int MAX_CHARACTERS = 1800;

    private final int width;
    private final int height;
    private final Map<EncodeHintType, Object> hints;
    private boolean autoDisplay = true;

    public QRCodeGenerator() {
        this(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    public QRCodeGenerator(int width, int height) {
        this.width = width;
        this.height = height;
        this.hints = createDefaultHints();
    }

    private Map<EncodeHintType, Object> createDefaultHints() {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1);
        return hints;
    }

    private BitMatrix generateBitMatrix(String data) throws WriterException {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("Data cannot be null or empty");
        }

        if (data.length() > MAX_CHARACTERS) {
            logger.warn("Data length ({}) exceeds recommended maximum ({})", data.length(), MAX_CHARACTERS);
        }

        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        return qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, width, height, hints);
    }

    public void displayQRCode(BufferedImage image, String title, String data) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame(title);
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

            JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
            mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

            JLabel imageLabel = new JLabel(new ImageIcon(image));
            imageLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 2));
            mainPanel.add(imageLabel, BorderLayout.CENTER);

            JPanel infoPanel = new JPanel(new BorderLayout());
            String displayData = data.length() > 100 ? data.substring(0, 100) + "..." : data;
            JTextArea dataArea = new JTextArea("URL/Data:\n" + displayData);
            dataArea.setEditable(false);
            dataArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
            dataArea.setBackground(new Color(240, 240, 240));
            dataArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            dataArea.setLineWrap(true);
            dataArea.setWrapStyleWord(true);

            JScrollPane scrollPane = new JScrollPane(dataArea);
            scrollPane.setPreferredSize(new Dimension(400, 80));
            infoPanel.add(scrollPane, BorderLayout.CENTER);

            JButton closeButton = new JButton("Close");
            closeButton.addActionListener(e -> frame.dispose());
            JPanel buttonPanel = new JPanel();
            buttonPanel.add(closeButton);
            infoPanel.add(buttonPanel, BorderLayout.SOUTH);

            mainPanel.add(infoPanel, BorderLayout.SOUTH);
            frame.add(mainPanel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            logger.info("QR Code displayed: {}", title);
        });
    }

    public void setAutoDisplay(boolean autoDisplay) {
        this.autoDisplay = autoDisplay;
    }

    public void generateQRCodeToFile(String data, String filePath) throws WriterException, IOException {
        logger.info("Generating QR code to file: {}", filePath);

        BitMatrix bitMatrix = generateBitMatrix(data);
        Path path = FileSystems.getDefault().getPath(filePath);
        MatrixToImageWriter.writeToPath(bitMatrix, "PNG", path);

        logger.info("QR code saved to: {}", filePath);

        if (autoDisplay) {
            BufferedImage image = MatrixToImageWriter.toBufferedImage(bitMatrix);
            displayQRCode(image, "QR Code: " + filePath, data);
        }
    }

    public BufferedImage generateQRCodeImage(String data) throws WriterException {
        logger.debug("Generating QR code image for data length: {}", data.length());

        BitMatrix bitMatrix = generateBitMatrix(data);
        BufferedImage image = MatrixToImageWriter.toBufferedImage(bitMatrix);

        if (autoDisplay) {
            displayQRCode(image, "QR Code Generated", data);
        }

        return image;
    }

    public String generateQRCodeBase64(String data) throws WriterException, IOException {
        logger.debug("Generating Base64 QR code");

        BufferedImage image = generateQRCodeImage(data);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "PNG", baos);
            byte[] imageBytes = baos.toByteArray();
            return Base64.getEncoder().encodeToString(imageBytes);
        }
    }

    public String generateQRCodeDataURL(String data) throws WriterException, IOException {
        logger.debug("Generating data URL for QR code");

        String base64Image = generateQRCodeBase64(data);
        return "data:image/png;base64," + base64Image;
    }

    public byte[] generateQRCodeBytes(String data) throws WriterException, IOException {
        logger.debug("Generating QR code as byte array");

        BufferedImage image = generateQRCodeImage(data);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "PNG", baos);
            return baos.toByteArray();
        }
    }

    /**
     * NEW: Generate mobile-friendly QR code with URL
     */
    public String generateMobileQRCode(String serverUrl, String token, String sessionId)
            throws WriterException, IOException {
        String mobileUrl = String.format("%s/verify?token=%s&session=%s&from=mobile",
                serverUrl, token, sessionId);

        return generateQRCodeDataURL(mobileUrl);
    }

    /**
     * NEW: Generate simple URL QR code for mobile
     */
    public void generateURLQRCode(String url, String filename) throws WriterException, IOException {
        // Ensure URL starts with http:// or https://
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }

        logger.info("Generating URL QR code: {}", url);
        generateQRCodeToFile(url, filename);
    }

    public boolean isDataSizeValid(String data) {
        return data != null && data.length() <= MAX_CHARACTERS;
    }

    public Map<String, Integer> getDimensions() {
        return Map.of("width", width, "height", height);
    }

    public static class Builder {
        private int width = DEFAULT_WIDTH;
        private int height = DEFAULT_HEIGHT;
        private boolean autoDisplay = true;

        public Builder width(int width) {
            this.width = width;
            return this;
        }

        public Builder height(int height) {
            this.height = height;
            return this;
        }

        public Builder size(int size) {
            this.width = size;
            this.height = size;
            return this;
        }

        public Builder autoDisplay(boolean autoDisplay) {
            this.autoDisplay = autoDisplay;
            return this;
        }

        public QRCodeGenerator build() {
            QRCodeGenerator generator = new QRCodeGenerator(width, height);
            generator.setAutoDisplay(autoDisplay);
            return generator;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static void main(String[] args) throws IOException {
        System.out.println("=".repeat(50));
        System.out.println("QR Code Generator - Mobile Test");
        System.out.println("=".repeat(50));

        try {
            QRCodeGenerator generator = new QRCodeGenerator();

            System.out.println("\nTest 1: Google URL (test mobile scanning)");
            generator.generateQRCodeToFile(
                    "https://www.google.com",
                    "test-google.png"
            );
            System.out.println("✓ test-google.png generated");

            System.out.println("\nTest 2: Local server URL");
            String localUrl = "http://192.168.1.100:8080";
            generator.generateURLQRCode(localUrl, "test-local.png");
            System.out.println("✓ test-local.png generated");

            System.out.println("\nTest 3: Mobile login URL");
            String mobileLoginUrl = "http://192.168.1.100:8080/verify?token=TEST123&session=SESS456";
            generator.generateQRCodeToFile(mobileLoginUrl, "test-mobile-login.png");
            System.out.println("✓ test-mobile-login.png generated");

            System.out.println("\n" + "=".repeat(50));
            System.out.println("QR codes generated successfully!");
            System.out.println("Transfer these .png files to your phone and scan them.");

        } catch (Exception e) {
            System.err.println("\n❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}