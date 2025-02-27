package org.example;

import org.example.Entities.Utilisateur;
import org.example.repository.JsonUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;




public class JsonUserRepositoryTest {

    private JsonUserRepository userRepository;
    private File tempUsersFile;

    @BeforeEach
    public void setUp(@TempDir final Path tempDir) throws Exception {
        // Create temporary JSON file with empty array content
        tempUsersFile = new File(tempDir.toFile(), "utilisateurs.json");
        try (FileWriter writer = new FileWriter(tempUsersFile)) {
            writer.write("[]");
        }
        // Instantiate repository and override its usersFile field using reflection
        userRepository = new JsonUserRepository();
        final Field usersFileField = JsonUserRepository.class.getDeclaredField("usersFile");
        usersFileField.setAccessible(true);
        usersFileField.set(userRepository, tempUsersFile);
    }

    @Test
    public void testLoadUsersInitiallyEmpty() throws IOException {
        final List<Utilisateur> users = userRepository.loadUsers();
        assertNotNull(users);
        assertTrue(users.isEmpty(), "User list should be empty initially");
    }

    @Test
    public void testSaveUserAndFindByEmail() throws IOException {
        final Utilisateur user = new Utilisateur("test@example.com", "secret");
        userRepository.saveUser(user);

        final Optional<Utilisateur> found = userRepository.findByEmail("test@example.com");
        assertTrue(found.isPresent(), "User should be found after saving");
        assertEquals("test@example.com", found.get().getEmail());
        assertEquals("secret", found.get().getPassword());
    }

    @Test
    public void testUpdateUserStatus() throws IOException {
        final Utilisateur user = new Utilisateur("status@example.com", "pwd");
        userRepository.saveUser(user);

        // Initially offline
        final Optional<Utilisateur> beforeUpdate = userRepository.findByEmail("status@example.com");
        assertTrue(beforeUpdate.isPresent());
        assertFalse(beforeUpdate.get().isOnline(), "User should be offline initially");

        // Update user status
        userRepository.updateUserStatus("status@example.com", true);
        final Optional<Utilisateur> afterUpdate = userRepository.findByEmail("status@example.com");
        assertTrue(afterUpdate.isPresent());
        assertTrue(afterUpdate.get().isOnline(), "User should be online after update");
    }

    @Test
    public void testVerifyUser() throws IOException {
        final Utilisateur user = new Utilisateur("verify@example.com", "mypassword");
        userRepository.saveUser(user);

        // Correct credentials
        assertTrue(userRepository.verifyUser("verify@example.com", "mypassword"), "Verification should pass");

        // Incorrect password
        assertFalse(userRepository.verifyUser("verify@example.com", "wrongpassword"), "Verification should fail with wrong password");

        // Non-existent user
        assertFalse(userRepository.verifyUser("nonexistent@example.com", "any"), "Verification should fail for non-existent user");
    }
}