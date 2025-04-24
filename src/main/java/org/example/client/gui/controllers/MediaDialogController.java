package org.example.client.gui.controllers;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import org.example.client.gui.service.AudioRecorderService;
import org.example.shared.model.enums.MessageType;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.sound.sampled.LineUnavailableException;

public class MediaDialogController {

    @FXML
    private ComboBox<String> mediaTypeComboBox;

    @FXML
    private TextField filePathField;

    @FXML
    private VBox previewContainer;

    @FXML
    private HBox recordAudioContainer;

    @FXML
    private Button recordButton;

    @FXML
    private Label recordingTimeLabel;

    @FXML
    private ProgressBar audioLevelIndicator;

    @FXML
    private Button sendButton;

    private File selectedFile;
    private MessageType selectedType;
    private boolean isRecording = false;
    private Timer recordingTimer;
    private int recordingSeconds = 0;

    private final AudioRecorderService audioRecorderService = new AudioRecorderService();

    // Interface for handling send action
    public interface MediaSendHandler {
        void onMediaSelected(File file, MessageType type);
    }

    private MediaSendHandler sendHandler;

    @FXML
    public void initialize() {
        // Initialize media type combo box
        mediaTypeComboBox.setItems(FXCollections.observableArrayList(
                "Image", "Vidéo", "Document", "Audio"
        ));

        // Add listener to the combo box
        mediaTypeComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                switch (newVal) {
                    case "Image":
                        selectedType = MessageType.IMAGE;
                        recordAudioContainer.setVisible(false);
                        recordAudioContainer.setManaged(false);
                        break;
                    case "Vidéo":
                        selectedType = MessageType.VIDEO;
                        recordAudioContainer.setVisible(false);
                        recordAudioContainer.setManaged(false);
                        break;
                    case "Document":
                        selectedType = MessageType.DOCUMENT;
                        recordAudioContainer.setVisible(false);
                        recordAudioContainer.setManaged(false);
                        break;
                    case "Audio":
                        selectedType = MessageType.AUDIO;
                        recordAudioContainer.setVisible(true);
                        recordAudioContainer.setManaged(true);
                        break;
                }

                // Clear the file selection when changing type
                selectedFile = null;
                filePathField.clear();
                updatePreview();
            }
        });

        // Set default selected type
        mediaTypeComboBox.getSelectionModel().select(0);

        // Disable send button initially
        sendButton.setDisable(true);
    }

    /**
     * Sets the handler for send actions.
     *
     * @param handler The handler
     */
    public void setSendHandler(MediaSendHandler handler) {
        this.sendHandler = handler;
    }

    @FXML
    private void handleBrowseFile(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Sélectionner un fichier");

        // Set extension filters based on selected type
        switch (selectedType) {
            case IMAGE:
                fileChooser.getExtensionFilters().add(
                        new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
                break;
            case VIDEO:
                fileChooser.getExtensionFilters().add(
                        new FileChooser.ExtensionFilter("Vidéos", "*.mp4", "*.avi", "*.mov", "*.wmv", "*.flv", "*.mkv"));
                break;
            case AUDIO:
                fileChooser.getExtensionFilters().add(
                        new FileChooser.ExtensionFilter("Fichiers audio", "*.mp3", "*.wav", "*.ogg", "*.aac", "*.wma", "*.flac"));
                break;
            case DOCUMENT:
                fileChooser.getExtensionFilters().add(
                        new FileChooser.ExtensionFilter("Tous les fichiers", "*.*"));
                break;
        }

        // Show file chooser dialog
        File file = fileChooser.showOpenDialog(filePathField.getScene().getWindow());
        if (file != null) {
            selectedFile = file;
            filePathField.setText(file.getAbsolutePath());
            sendButton.setDisable(false);
            updatePreview();
        }
    }

    @FXML
    private void handleRecordAudio(ActionEvent event) {
        if (isRecording) {
            stopRecording();
        } else {
            startRecording();
        }
    }

    private void startRecording() {
        try {
            // Start recording
            audioRecorderService.startRecording();
            isRecording = true;

            // Update UI
            recordButton.setText("■ Arrêter");
            recordingTimeLabel.setText("00:00");

            // Start timer
            recordingSeconds = 0;
            recordingTimer = new Timer();
            recordingTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    recordingSeconds++;
                    Platform.runLater(() -> {
                        int minutes = recordingSeconds / 60;
                        int seconds = recordingSeconds % 60;
                        recordingTimeLabel.setText(String.format("%02d:%02d", minutes, seconds));
                    });
                }
            }, 1000, 1000);

        } catch (LineUnavailableException e) {
            System.err.println("Error starting recording: " + e.getMessage());
        }
    }

    private void stopRecording() {
        try {
            // Stop the timer
            if (recordingTimer != null) {
                recordingTimer.cancel();
                recordingTimer = null;
            }

            // Stop recording and get the recorded file
            File audioFile = audioRecorderService.stopRecording();
            isRecording = false;

            // Update UI
            recordButton.setText("🎤 Enregistrer");

            // If we have a valid audio file, set it as the selected file
            if (audioFile != null && audioFile.exists()) {
                selectedFile = audioFile;
                filePathField.setText("Enregistrement audio (" + formatDuration(recordingSeconds) + ")");
                sendButton.setDisable(false);
                updatePreview();
            }
        } catch (IOException e) {
            System.err.println("Error stopping recording: " + e.getMessage());
        }
    }

    private String formatDuration(int seconds) {
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void updatePreview() {
        previewContainer.getChildren().clear();

        if (selectedFile == null) {
            return;
        }

        switch (selectedType) {
            case IMAGE:
                try {
                    Image image = new Image(selectedFile.toURI().toString());
                    ImageView imageView = new ImageView(image);

                    // Limit preview size
                    imageView.setFitWidth(300);
                    imageView.setFitHeight(200);
                    imageView.setPreserveRatio(true);

                    previewContainer.getChildren().add(imageView);
                } catch (Exception e) {
                    previewContainer.getChildren().add(new Label("Erreur de chargement de l'image"));
                }
                break;

            case VIDEO:
                try {
                    Media media = new Media(selectedFile.toURI().toString());
                    MediaPlayer mediaPlayer = new MediaPlayer(media);
                    MediaView mediaView = new MediaView(mediaPlayer);

                    // Limit preview size
                    mediaView.setFitWidth(300);
                    mediaView.setFitHeight(200);
                    mediaView.setPreserveRatio(true);

                    // Add play/pause button
                    Button playButton = new Button("▶ Play");
                    playButton.setOnAction(e -> {
                        if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                            mediaPlayer.pause();
                            playButton.setText("▶ Play");
                        } else {
                            mediaPlayer.play();
                            playButton.setText("⏸ Pause");
                        }
                    });

                    VBox videoBox = new VBox(10, mediaView, playButton);
                    previewContainer.getChildren().add(videoBox);
                } catch (Exception e) {
                    previewContainer.getChildren().add(new Label("Erreur de chargement de la vidéo"));
                }
                break;

            case AUDIO:
                try {
                    HBox audioPlayer = new HBox(10);
                    audioPlayer.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                    Button playButton = new Button("▶");

                    if (selectedFile.exists() && selectedFile.getName().toLowerCase().endsWith(".wav")) {
                        Media media = new Media(selectedFile.toURI().toString());
                        MediaPlayer mediaPlayer = new MediaPlayer(media);

                        playButton.setOnAction(e -> {
                            if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                                mediaPlayer.pause();
                                playButton.setText("▶");
                            } else {
                                mediaPlayer.play();
                                playButton.setText("⏸");
                            }
                        });
                    } else {
                        // For non-playable files, just show a placeholder
                        playButton.setDisable(true);
                    }

                    Label audioLabel = new Label(selectedFile.getName());

                    audioPlayer.getChildren().addAll(playButton, audioLabel);
                    previewContainer.getChildren().add(audioPlayer);
                } catch (Exception e) {
                    previewContainer.getChildren().add(new Label("Erreur de chargement de l'audio"));
                }
                break;

            case DOCUMENT:
                Label docIcon = new Label("📄");
                docIcon.setStyle("-fx-font-size: 32px;");

                Label docName = new Label(selectedFile.getName());

                Label docSize = new Label(formatFileSize(selectedFile.length()));
                docSize.setStyle("-fx-text-fill: #888;");

                VBox docBox = new VBox(5, docIcon, docName, docSize);
                docBox.setAlignment(javafx.geometry.Pos.CENTER);

                previewContainer.getChildren().add(docBox);
                break;
        }
    }

    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " octets";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
        }
    }

    @FXML
    private void handleSend(ActionEvent event) {
        if (selectedFile != null && sendHandler != null) {
            sendHandler.onMediaSelected(selectedFile, selectedType);
            closeDialog();
        }
    }

    @FXML
    private void handleCancel(ActionEvent event) {
        // Stop recording if in progress
        if (isRecording) {
            try {
                audioRecorderService.stopRecording();
                if (recordingTimer != null) {
                    recordingTimer.cancel();
                }
            } catch (IOException e) {
                // Ignore
            }
        }

        closeDialog();
    }

    private void closeDialog() {
        Stage stage = (Stage) sendButton.getScene().getWindow();
        stage.close();
    }
}