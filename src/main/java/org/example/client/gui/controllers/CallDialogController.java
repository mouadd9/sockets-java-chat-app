package org.example.client.gui.controllers;

import java.net.URL;
import java.util.ResourceBundle;

import org.example.client.gui.service.CallManager;
import org.example.shared.model.CallSession;
import org.example.shared.model.User;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

/**
 * Contrôleur pour la fenêtre de dialogue d'appel entrant.
 */
public class CallDialogController implements Initializable {
    
    @FXML private Label callerNameLabel;
    @FXML private Label callStatusLabel;
    @FXML private Button acceptButton;
    @FXML private Button rejectButton;
    
    private CallSession callSession;
    private User caller;
    private String callerIp;
    private int callerPort;
    private Runnable onAcceptCallback;
    private Runnable onRejectCallback;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Rien à initialiser ici
    }
    
    /**
     * Initialise le dialogue avec les informations d'appel.
     * 
     * @param session La session d'appel
     * @param caller L'utilisateur qui appelle
     * @param callerIp L'adresse IP de l'appelant
     * @param callerPort Le port UDP de l'appelant
     * @param onAccept Callback à exécuter si l'appel est accepté
     * @param onReject Callback à exécuter si l'appel est rejeté
     */
    public void initData(CallSession session, User caller, String callerIp, int callerPort, 
                         Runnable onAccept, Runnable onReject) {
        this.callSession = session;
        this.caller = caller;
        this.callerIp = callerIp;
        this.callerPort = callerPort;
        this.onAcceptCallback = onAccept;
        this.onRejectCallback = onReject;
        
        // Mettre à jour l'interface utilisateur
        Platform.runLater(() -> {
            callerNameLabel.setText("Appel de " + caller.getDisplayNameOrEmail());
        });
    }
    
    /**
     * Gère l'acceptation de l'appel.
     */
    @FXML
    private void handleAcceptCall(ActionEvent event) {
        // Mettre à jour l'interface utilisateur
        acceptButton.setDisable(true);
        rejectButton.setDisable(true);
        callStatusLabel.setText("Connexion en cours...");
        
        // Accepter l'appel via le CallManager
        CallManager.getInstance().acceptCall(callSession, callerIp, callerPort);
        
        // Exécuter le callback d'acceptation
        if (onAcceptCallback != null) {
            onAcceptCallback.run();
        }
        
        // Fermer la fenêtre
        closeDialog();
    }
    
    /**
     * Gère le rejet de l'appel.
     */
    @FXML
    private void handleRejectCall(ActionEvent event) {
        // Mettre à jour l'interface utilisateur
        acceptButton.setDisable(true);
        rejectButton.setDisable(true);
        callStatusLabel.setText("Appel refusé");
        
        // Exécuter le callback de rejet
        if (onRejectCallback != null) {
            onRejectCallback.run();
        }
        
        // Fermer la fenêtre
        closeDialog();
    }
    
    /**
     * Ferme la fenêtre de dialogue.
     */
    private void closeDialog() {
        Platform.runLater(() -> {
            Stage stage = (Stage) acceptButton.getScene().getWindow();
            stage.close();
        });
    }
}
