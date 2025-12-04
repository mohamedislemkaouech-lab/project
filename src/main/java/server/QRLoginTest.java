package server;

import qrcode.QRLoginService;
import com.google.zxing.WriterException;
import java.io.IOException;

public class QRLoginTest {
    public static void main(String[] args) throws IOException, WriterException {
        // 1️⃣ Créer token
        String token = QRLoginService.createToken();
        System.out.println("Token généré : " + token);

        // 2️⃣ Générer QR code dans le projet
        String filePath = "qr-login.png"; // sera créé dans le dossier racine du projet
        QRLoginService.generateQRCode(token, filePath, 300, 300);
        System.out.println("QR code généré : " + filePath);

        // 3️⃣ Simuler scan côté client
        System.out.println("Simulation scan côté client...");
        boolean valid = QRLoginService.validateToken(token);
        if (valid) {
            System.out.println("Authentification réussie !");
        } else {
            System.out.println("Token invalide ou expiré.");
        }

        // 4️⃣ Tester un token invalide
        boolean invalid = QRLoginService.validateToken("fake-token");
        System.out.println("Authentification avec fake-token : " + invalid);
    }
}
