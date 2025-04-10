package org.example.service;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.FloatControl;
import java.awt.*;
import java.net.URL;
import org.example.config.NotificationConfig;

public class NotificationService {
    private static NotificationService instance;
    private final NotificationConfig config;
    private Clip notificationSound;
    private TrayIcon trayIcon;
    private int unreadCount = 0;
    private Stage mainStage;

    private NotificationService() {
        config = NotificationConfig.getInstance();
        initializeSound();
        setupSystemTray();
    }

    public static synchronized NotificationService getInstance() {
        if (instance == null) {
            instance = new NotificationService();
        }
        return instance;
    }

    private void initializeSound() {
        try {
            URL soundUrl = getClass().getResource("/sounds/notification.wav");
            if (soundUrl != null) {
                System.out.println("Chargement du son depuis: " + soundUrl);
                AudioInputStream audioStream = AudioSystem.getAudioInputStream(soundUrl);
                notificationSound = AudioSystem.getClip();
                notificationSound.open(audioStream);
                
                // Ajuster le volume si possible
                if (notificationSound.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gainControl = (FloatControl) notificationSound.getControl(FloatControl.Type.MASTER_GAIN);
                    gainControl.setValue(6.0f); // Augmenter le volume de 6 dB
                }
                
                System.out.println("Son chargé avec succès");
            } else {
                System.err.println("Le fichier de son n'a pas été trouvé");
            }
        } catch (Exception e) {
            System.err.println("Erreur lors du chargement du son: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupSystemTray() {
        if (SystemTray.isSupported() && config.isTrayEnabled()) {
            try {
                SystemTray tray = SystemTray.getSystemTray();
                URL iconUrl = getClass().getResource("/icons/app_icon.png");
                if (iconUrl != null) {
                    Image icon = Toolkit.getDefaultToolkit().getImage(iconUrl);
                    trayIcon = new TrayIcon(icon, "Chat Application");
                    trayIcon.setImageAutoSize(true);
                    tray.add(trayIcon);
                }
            } catch (Exception e) {
                System.err.println("Erreur lors de l'initialisation du system tray: " + e.getMessage());
            }
        }
    }

    public void setMainStage(Stage stage) {
        this.mainStage = stage;
    }

    public void showNotification(String senderEmail, String message) {
        if (!isApplicationFocused()) {
            Platform.runLater(() -> {
                // Afficher une alerte JavaFX si activée
                if (config.isPopupEnabled()) {
                    Alert alert = new Alert(AlertType.INFORMATION);
                    alert.setTitle("Nouveau message");
                    alert.setHeaderText(senderEmail);
                    alert.setContentText(message);
                    alert.initStyle(StageStyle.UTILITY);
                    alert.show();
                }

                // Jouer le son si activé
                if (config.isSoundEnabled()) {
                    playNotificationSound();
                }

                // Afficher une notification système si activée
                if (config.isTrayEnabled() && trayIcon != null) {
                    trayIcon.displayMessage(
                        "Nouveau message de " + senderEmail,
                        message,
                        TrayIcon.MessageType.INFO
                    );
                }

                // Incrémenter le compteur de messages non lus
                incrementUnreadCount();
            });
        }
    }

    private void incrementUnreadCount() {
        unreadCount++;
        updateTitleBadge();
    }

    public void decrementUnreadCount() {
        if (unreadCount > 0) {
            unreadCount--;
            updateTitleBadge();
        }
    }

    private void updateTitleBadge() {
        if (mainStage != null) {
            Platform.runLater(() -> {
                String baseTitle = "Chat Application";
                if (unreadCount > 0) {
                    mainStage.setTitle(String.format("%s (%d)", baseTitle, unreadCount));
                } else {
                    mainStage.setTitle(baseTitle);
                }
            });
        }
    }

    private void playNotificationSound() {
        if (notificationSound != null) {
            try {
                System.out.println("Lecture du son de notification");
                notificationSound.setFramePosition(0);
                notificationSound.start();
                // Attendre que le son soit terminé avant de le réinitialiser
                Thread.sleep(100); // Petit délai pour s'assurer que le son est lu
            } catch (Exception e) {
                System.err.println("Erreur lors de la lecture du son: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.err.println("Le son de notification n'est pas initialisé");
        }
    }

    private boolean isApplicationFocused() {
        return mainStage != null && mainStage.isFocused();
    }

    public void resetUnreadCount() {
        unreadCount = 0;
        updateTitleBadge();
    }
} 