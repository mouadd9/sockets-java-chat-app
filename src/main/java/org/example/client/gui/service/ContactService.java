package org.example.client.gui.service;

import java.io.IOException;
import java.util.List;

import org.example.shared.dao.ContactDAO;
import org.example.shared.dao.UserDAO;
import org.example.shared.model.Contact;
import org.example.shared.model.User;

/**
 * Service dédié à la gestion des contacts.
 */
public class ContactService {

    private final UserDAO userDAO;
    private final ContactDAO contactDAO;

    public ContactService() {
        this.userDAO = new UserDAO();
        this.contactDAO = new ContactDAO();
    }

    public List<String> getContacts(final String userEmail) throws IOException {
        final User user = userDAO.findUserByEmail(userEmail);
        if (user == null) {
            throw new IOException("Utilisateur non trouvé: " + userEmail);
        }
        return contactDAO.getContactsByUserId(user.getId());
    }

    public boolean addContact(final String userEmail, final String contactEmail) throws IOException {
        final User user = userDAO.findUserByEmail(userEmail);
        final User contactUser = userDAO.findUserByEmail(contactEmail);

        if (user == null) {
            throw new IOException("Utilisateur non trouvé: " + userEmail);
        }
        if (contactUser == null) {
            throw new IOException("Contact non trouvé: " + contactEmail);
        }
        if (user.getId() == contactUser.getId()) {
            throw new IllegalArgumentException("Vous ne pouvez pas vous ajouter vous-même comme contact.");
        }

        final Contact newContact = new Contact(user.getId(), contactUser.getId());
        contactDAO.createContact(newContact);
        return true;
    }

    public boolean removeContact(final String userEmail, final String contactEmail) throws IOException {
        final User user = userDAO.findUserByEmail(userEmail);
        final User contactUser = userDAO.findUserByEmail(contactEmail);

        if (user == null) {
            throw new IOException("Utilisateur non trouvé: " + userEmail);
        }
        if (contactUser == null) {
            return false;
        }

        return contactDAO.deleteContact(user.getId(), contactUser.getId());
    }
}
