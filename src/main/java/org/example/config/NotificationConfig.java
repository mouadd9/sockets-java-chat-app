package org.example.config;

import java.util.prefs.Preferences;

/**
 * Classe de configuration des notifications de l'application.
 * Implémente le pattern Singleton pour garantir une seule instance.
 * Gère les préférences de notification (son, popup, notifications système) de manière persistante.
 */
public class NotificationConfig {
    // Clés pour accéder aux préférences stockées
    private static final String PREF_SOUND_ENABLED = "notification_sound_enabled";
    private static final String PREF_POPUP_ENABLED = "notification_popup_enabled";
    private static final String PREF_TRAY_ENABLED = "notification_tray_enabled";
    
    // Instance unique de la classe (pattern Singleton)
    private static NotificationConfig instance;
    // Objet Preferences pour stocker les préférences de l'utilisateur
    private final Preferences preferences;

    /**
     * Constructeur privé pour empêcher l'instanciation directe.
     * Initialise l'objet Preferences pour le package courant.
     */
    private NotificationConfig() {
        preferences = Preferences.userNodeForPackage(NotificationConfig.class);
    }

    /**
     * Retourne l'instance unique de NotificationConfig.
     * Si l'instance n'existe pas, elle est créée.
     * @return L'instance unique de NotificationConfig
     */
    public static synchronized NotificationConfig getInstance() {
        if (instance == null) {
            instance = new NotificationConfig();
        }
        return instance;
    }

    /**
     * Vérifie si les notifications sonores sont activées.
     * @return true si les notifications sonores sont activées, false sinon
     */
    public boolean isSoundEnabled() {
        return preferences.getBoolean(PREF_SOUND_ENABLED, true);
    }

    /**
     * Active ou désactive les notifications sonores.
     * @param enabled true pour activer, false pour désactiver
     */
    public void setSoundEnabled(boolean enabled) {
        preferences.putBoolean(PREF_SOUND_ENABLED, enabled);
    }

    /**
     * Vérifie si les notifications popup sont activées.
     * @return true si les notifications popup sont activées, false sinon
     */
    public boolean isPopupEnabled() {
        return preferences.getBoolean(PREF_POPUP_ENABLED, true);
    }

    /**
     * Active ou désactive les notifications popup.
     * @param enabled true pour activer, false pour désactiver
     */
    public void setPopupEnabled(boolean enabled) {
        preferences.putBoolean(PREF_POPUP_ENABLED, enabled);
    }

    /**
     * Vérifie si les notifications système (tray) sont activées.
     * @return true si les notifications système sont activées, false sinon
     */
    public boolean isTrayEnabled() {
        return preferences.getBoolean(PREF_TRAY_ENABLED, true);
    }

    /**
     * Active ou désactive les notifications système (tray).
     * @param enabled true pour activer, false pour désactiver
     */
    public void setTrayEnabled(boolean enabled) {
        preferences.putBoolean(PREF_TRAY_ENABLED, enabled);
    }
} 