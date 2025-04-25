package org.example.server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.example.shared.model.enums.MessageType;

/**
 * Service for handling file operations on the server side.
 */
public class ServerFileService {

    // Base directory for storing media files
    private static final String MEDIA_DIR = System.getProperty("user.dir") + File.separator +
            "media_files";

    // Subdirectories for different media types
    private static final String IMAGES_DIR = MEDIA_DIR + File.separator + "images";
    private static final String VIDEOS_DIR = MEDIA_DIR + File.separator + "videos";
    private static final String DOCUMENTS_DIR = MEDIA_DIR + File.separator + "documents";
    private static final String AUDIO_DIR = MEDIA_DIR + File.separator + "audio";

    /**
     * Creates all necessary directories for storing media files.
     */
    public void ensureMediaDirectoriesExist() throws IOException {
        Files.createDirectories(Paths.get(IMAGES_DIR));
        Files.createDirectories(Paths.get(VIDEOS_DIR));
        Files.createDirectories(Paths.get(DOCUMENTS_DIR));
        Files.createDirectories(Paths.get(AUDIO_DIR));
    }

    /**
     * Gets the full path to a media file from its relative path.
     *
     * @param relativePath The relative path stored in the message
     * @return The full path to the file
     */
    public File getFile(String relativePath) {
        return new File(MEDIA_DIR + File.separator + relativePath);
    }

    /**
     * Saves a file to the appropriate directory and returns the path where it was saved.
     *
     * @param inputStream The input stream of the file
     * @param type The type of media
     * @param originalFilename The original filename
     * @return The path where the file was saved (relative to the media directory)
     * @throws IOException If an I/O error occurs
     */
    public String saveFile(InputStream inputStream, MessageType type, String originalFilename) throws IOException {
        // Get the appropriate directory
        String directory;
        switch (type) {
            case IMAGE:
                directory = IMAGES_DIR;
                break;
            case VIDEO:
                directory = VIDEOS_DIR;
                break;
            case DOCUMENT:
                directory = DOCUMENTS_DIR;
                break;
            case AUDIO:
                directory = AUDIO_DIR;
                break;
            default:
                throw new IllegalArgumentException("Invalid file type: " + type);
        }

        // Generate a unique filename to avoid collisions
        String fileExtension = getFileExtension(originalFilename);
        String uniqueFilename = UUID.randomUUID().toString() + fileExtension;
        String fullPath = directory + File.separator + uniqueFilename;

        // Save the file
        try (FileOutputStream fos = new FileOutputStream(fullPath)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        }

        // Return the relative path to be stored in the message
        return type.name().toLowerCase() + "/" + uniqueFilename;
    }

    /**
     * Gets the file extension from a filename.
     *
     * @param filename The filename
     * @return The file extension (including the dot)
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex >= 0) {
            return filename.substring(lastDotIndex);
        }
        return "";
    }

    /**
     * Deletes a file if it exists.
     *
     * @param relativePath The relative path to the file
     * @return true if the file was deleted, false otherwise
     */
    public boolean deleteFile(String relativePath) {
        File file = getFile(relativePath);
        if (file.exists()) {
            return file.delete();
        }
        return false;
    }
}