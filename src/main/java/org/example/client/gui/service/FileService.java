package org.example.client.gui.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.example.shared.model.Message;
import org.example.shared.model.enums.MessageType;

/**
 * Service for handling file operations for multimedia messages.
 */
public class FileService {

    // Base directory for storing media files
    private static final String MEDIA_DIR = System.getProperty("user.dir") + File.separator +
            "media_files";

    // Subdirectories for different media types
    private static final String IMAGES_DIR = MEDIA_DIR + File.separator + "images";
    private static final String VIDEOS_DIR = MEDIA_DIR + File.separator + "videos";
    private static final String DOCUMENTS_DIR = MEDIA_DIR + File.separator + "documents";
    private static final String AUDIO_DIR = MEDIA_DIR + File.separator + "audio";

    // Maximum file size (20MB)
    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024;

    /**
     * Constructor - creates necessary directories if they don't exist.
     */
    public FileService() {
        createDirectories();
    }

    /**
     * Creates all necessary directories for storing media files.
     */

    private void createDirectories() {
        try {
            File mediaDir = new File(MEDIA_DIR);
            if (!mediaDir.exists()) {
                boolean created = mediaDir.mkdirs();
                System.out.println("Created main media directory: " + created + " at " + mediaDir.getAbsolutePath());
            } else {
                System.out.println("Main media directory already exists at: " + mediaDir.getAbsolutePath());
            }

            // Create each subdirectory individually and log results
            File imagesDir = new File(IMAGES_DIR);
            if (!imagesDir.exists()) {
                boolean created = imagesDir.mkdirs();
                System.out.println("Created images directory: " + created + " at " + imagesDir.getAbsolutePath());
            } else {
                System.out.println("Images directory already exists at: " + imagesDir.getAbsolutePath());
            }

            File videosDir = new File(VIDEOS_DIR);
            if (!videosDir.exists()) {
                boolean created = videosDir.mkdirs();
                System.out.println("Created videos directory: " + created + " at " + videosDir.getAbsolutePath());
            } else {
                System.out.println("Videos directory already exists at: " + videosDir.getAbsolutePath());
            }

            File documentsDir = new File(DOCUMENTS_DIR);
            if (!documentsDir.exists()) {
                boolean created = documentsDir.mkdirs();
                System.out.println("Created documents directory: " + created + " at " + documentsDir.getAbsolutePath());
            } else {
                System.out.println("Documents directory already exists at: " + documentsDir.getAbsolutePath());
            }

            File audioDir = new File(AUDIO_DIR);
            if (!audioDir.exists()) {
                boolean created = audioDir.mkdirs();
                System.out.println("Created audio directory: " + created + " at " + audioDir.getAbsolutePath());
            } else {
                System.out.println("Audio directory already exists at: " + audioDir.getAbsolutePath());
            }

            // Log summary
            System.out.println("Directory structure setup complete. Media files will be stored in: " + MEDIA_DIR);
        } catch (Exception e) {
            System.err.println("Error creating directories: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Saves a file to the appropriate directory and returns the path where it was saved.
     */
    public String saveFile(File file, MessageType type, String originalFilename) throws IOException {
        System.out.println("//// Saving file of type " + type);
        // Check file size
        if (file.length() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File is too large. Maximum size is " +
                    (MAX_FILE_SIZE / (1024 * 1024)) + "MB");
        }

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

        System.out.println("Full path for saving: " + fullPath);

        // Copy the file
        try (InputStream in = new FileInputStream(file);
             OutputStream out = new FileOutputStream(fullPath)) {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        }

        // IMPORTANT: Always use the same separator character (/) for storage in the database
        // We'll handle the platform-specific conversion when retrieving the file
        return type.name().toLowerCase() + "/" + uniqueFilename;
    }

    /**
     * Gets the full path to a media file from its relative path.
     */
    public File getFile(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            throw new IllegalArgumentException("Relative path cannot be null or empty");
        }

        // Debug the input path
        System.out.println("Original relative path: " + relativePath);

        // Split the path into type and filename
        String[] parts = relativePath.split("/");
        if (parts.length < 2) {
            parts = relativePath.split("\\\\"); // Try Windows separator
        }

        // If we can't split, use the raw path (suboptimal but better than failing)
        if (parts.length < 2) {
            System.out.println("Warning: Cannot split path properly: " + relativePath);
            String normalizedPath = relativePath.replace('/', File.separatorChar).replace('\\', File.separatorChar);
            File file = new File(MEDIA_DIR + File.separator + normalizedPath);
            System.out.println("Falling back to direct path: " + file.getAbsolutePath());
            return file;
        }

        String typeDir = parts[0];
        String filename = parts[1];

        // Construct proper directory based on media type
        String typeDirectory;
        switch (typeDir.toLowerCase()) {
            case "image":
                typeDirectory = IMAGES_DIR;
                break;
            case "video":
                typeDirectory = VIDEOS_DIR;
                break;
            case "document":
                typeDirectory = DOCUMENTS_DIR;
                break;
            case "audio":
                typeDirectory = AUDIO_DIR;
                break;
            default:
                // Fallback to base directory with the original relative path
                System.out.println("Unknown media type: " + typeDir);
                String normalizedPath = relativePath.replace('/', File.separatorChar).replace('\\', File.separatorChar);
                File file = new File(MEDIA_DIR + File.separator + normalizedPath);
                System.out.println("Falling back to direct path: " + file.getAbsolutePath());
                return file;
        }

        // Construct the full path
        File file = new File(typeDirectory + File.separator + filename);
        System.out.println("Getting media file at: " + file.getAbsolutePath());
        System.out.println("File exists: " + file.exists());

        return file;
    }
    /**
     * Detects the message type based on the file extension.
     *
     * @param filename The name of the file
     * @return The detected message type
     */
    public MessageType detectMessageType(String filename) {
        String extension = getFileExtension(filename).toLowerCase();

        // Image formats
        if (extension.matches("\\.(jpg|jpeg|png|gif|bmp|webp)$")) {
            return MessageType.IMAGE;
        }

        // Video formats
        if (extension.matches("\\.(mp4|avi|mov|wmv|flv|mkv|webm)$")) {
            return MessageType.VIDEO;
        }

        // Audio formats
        if (extension.matches("\\.(mp3|wav|ogg|aac|wma|flac)$")) {
            return MessageType.AUDIO;
        }

        // Default to document for all other types
        return MessageType.DOCUMENT;
    }

    /**
     * Gets the MIME type for a file.
     *
     * @param file The file
     * @return The MIME type
     */
    public String getMimeType(File file) throws IOException {
        return Files.probeContentType(file.toPath());
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