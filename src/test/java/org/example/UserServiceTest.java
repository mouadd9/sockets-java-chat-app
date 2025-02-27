package org.example;

import org.example.Entities.Utilisateur;
import org.example.repository.JsonUserRepository;
import org.example.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

public class UserServiceTest {
    
    private UserService userService;
    private JsonUserRepository userRepository;
    private File tempUsersFile;

    @BeforeEach
    public void setUp(@TempDir final Path tempDir) throws Exception {
        // Créer un fichier JSON temporaire
        tempUsersFile = new File(tempDir.toFile(), "utilisateurs.json");
        try (FileWriter writer = new FileWriter(tempUsersFile)) {
            writer.write("[]");
        }

        // Configuration du repository avec le fichier temporaire
        userRepository = new JsonUserRepository();
        Field usersFileField = JsonUserRepository.class.getDeclaredField("usersFile");
        usersFileField.setAccessible(true);
        usersFileField.set(userRepository, tempUsersFile);

        // Injection du repository dans le service via réflexion
        userService = new UserService();
        Field repositoryField = UserService.class.getDeclaredField("userRepository");
        repositoryField.setAccessible(true);
        repositoryField.set(userService, userRepository);
    }

    @Test
    public void testRegisterUser_Success() throws IOException {
        // Test d'enregistrement d'un nouvel utilisateur
        boolean result = userService.registerUser("new@test.com", "password123");
        
        assertTrue(result, "L'enregistrement devrait réussir pour un nouvel utilisateur");
        Optional<Utilisateur> user = userRepository.findByEmail("new@test.com");
        assertTrue(user.isPresent(), "L'utilisateur devrait être trouvé après l'enregistrement");
        assertEquals("password123", user.get().getPassword());
    }

    @Test
    public void testRegisterUser_Duplicate() throws IOException {
        // Premier enregistrement
        userService.registerUser("existing@test.com", "password123");
        
        // Tentative d'enregistrement en double
        boolean result = userService.registerUser("existing@test.com", "newpassword");
        
        assertFalse(result, "L'enregistrement devrait échouer pour un email existant");
    }

    @Test
    public void testAuthenticateUser_Success() throws IOException {
        // Enregistrer d'abord un utilisateur
        userService.registerUser("auth@test.com", "password123");
        
        // Tester l'authentification
        Optional<Utilisateur> result = userService.authenticateUser("auth@test.com", "password123");
        
        assertTrue(result.isPresent(), "L'authentification devrait réussir avec les bons identifiants");
        assertTrue(result.get().isOnline(), "L'utilisateur devrait être marqué comme en ligne après l'authentification");
    }

    @Test
    public void testAuthenticateUser_Failure() throws IOException {
        // Enregistrer un utilisateur
        userService.registerUser("auth@test.com", "password123");
        
        // Tester l'authentification avec un mauvais mot de passe
        Optional<Utilisateur> result = userService.authenticateUser("auth@test.com", "wrongpassword");
        
        assertTrue(result.isEmpty(), "L'authentification devrait échouer avec un mauvais mot de passe");
    }

    @Test
    public void testSetUserOnlineStatus() throws IOException {
        // Enregistrer un utilisateur
        userService.registerUser("status@test.com", "password");
        
        // Mettre à jour le statut
        userService.setUserOnlineStatus("status@test.com", true);
        
        Optional<Utilisateur> user = userRepository.findByEmail("status@test.com");
        assertTrue(user.isPresent());
        assertTrue(user.get().isOnline(), "L'utilisateur devrait être en ligne");

        // Mettre hors ligne
        userService.setUserOnlineStatus("status@test.com", false);
        user = userRepository.findByEmail("status@test.com");
        assertFalse(user.get().isOnline(), "L'utilisateur devrait être hors ligne");
    }

    @Test
    public void testAddContact_Success() throws IOException {
        // Créer deux utilisateurs
        userService.registerUser("user1@test.com", "password1");
        userService.registerUser("user2@test.com", "password2");
        
        // Ajouter le contact
        boolean result = userService.addContact("user1@test.com", "user2@test.com");
        
        assertTrue(result, "L'ajout du contact devrait réussir");
        Optional<Utilisateur> user = userRepository.findByEmail("user1@test.com");
        assertTrue(user.isPresent());
        assertTrue(user.get().getContactEmails().contains("user2@test.com"), 
                  "La liste des contacts devrait contenir le nouvel email");
    }

    @Test
    public void testAddContact_NonExistentUser() throws IOException {
        // Créer un utilisateur
        userService.registerUser("user1@test.com", "password1");
        
        // Tenter d'ajouter un contact inexistant
        boolean result = userService.addContact("user1@test.com", "nonexistent@test.com");
        
        assertFalse(result, "L'ajout d'un contact inexistant devrait échouer");
    }
}