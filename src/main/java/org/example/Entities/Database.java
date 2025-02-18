package org.example.Entities;/*package Entities;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Database {
    private static final String DB_FOLDER = "database";
    private static final String USERS_FILE = DB_FOLDER + "/users.json";
    private static final String MESSAGES_FILE = DB_FOLDER + "/messages.json";
    private static final String GROUPS_FILE = DB_FOLDER + "/groups.json";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    static {
        new File(DB_FOLDER).mkdirs();
        initializeFiles();
    }

    private static void initializeFiles() {
        try {
            createFileIfNotExists(USERS_FILE, new ArrayList<Utilisateur>());
            createFileIfNotExists(MESSAGES_FILE, new ArrayList<Message>());
            createFileIfNotExists(GROUPS_FILE, new ArrayList<Group>());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void createFileIfNotExists(String filePath, Object defaultContent) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            String jsonContent = gson.toJson(defaultContent);
            Files.write(Paths.get(filePath), jsonContent.getBytes());
        }
    }

    public static void saveUsers(List<Utilisateur> users) throws IOException {
        String json = gson.toJson(users);
        Files.write(Paths.get(USERS_FILE), json.getBytes());
    }

    public static List<Utilisateur> loadUsers() throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(USERS_FILE)));
        return gson.fromJson(content, ArrayList.class);
    }

    public static void saveMessages(List<Message> messages) throws IOException {
        String json = gson.toJson(messages);
        Files.write(Paths.get(MESSAGES_FILE), json.getBytes());
    }

    public static List<Message> loadMessages() throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(MESSAGES_FILE)));
        return gson.fromJson(content, ArrayList.class);
    }

    public static void saveGroups(List<Group> groups) throws IOException {
        String json = gson.toJson(groups);
        Files.write(Paths.get(GROUPS_FILE), json.getBytes());
    }

    public static List<Group> loadGroups() throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(GROUPS_FILE)));
        return gson.fromJson(content, ArrayList.class);
    }
}
*/