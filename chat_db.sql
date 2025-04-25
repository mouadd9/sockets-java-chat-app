-- Table pour les utilisateurs (User)
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    is_online BOOLEAN NOT NULL,
    created_at DATETIME NOT NULL,
    last_login_at DATETIME NULL,
    profile_picture_url VARCHAR(255) DEFAULT 'default_avatar.png'
) ENGINE=InnoDB;

-- Table pour les contacts
CREATE TABLE IF NOT EXISTS contacts (
    user_id BIGINT NOT NULL,
    contact_user_id BIGINT NOT NULL,
    added_at DATETIME NOT NULL,
    PRIMARY KEY (user_id, contact_user_id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (contact_user_id) REFERENCES users(id)
) ENGINE=InnoDB;

-- Table pour les groupes (utilisation de backticks pour le mot réservé)
CREATE TABLE IF NOT EXISTS `groups` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    owner_user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    profile_picture_url VARCHAR(255) DEFAULT 'default_group.png',
    FOREIGN KEY (owner_user_id) REFERENCES users(id)
) ENGINE=InnoDB;

-- Table pour l'appartenance aux groupes (GroupMembership)
CREATE TABLE IF NOT EXISTS group_memberships (
    user_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    joined_at DATETIME NOT NULL,
    PRIMARY KEY (user_id, group_id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (group_id) REFERENCES `groups`(id)
) ENGINE=InnoDB;

-- Table pour les messages (Message ou Message_old)
CREATE TABLE IF NOT EXISTS messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sender_user_id BIGINT NOT NULL,
    receiver_user_id BIGINT, -- Nullable pour les messages directs
    group_id BIGINT,         -- Nullable pour les messages de groupe
    content TEXT NOT NULL,
    timestamp DATETIME NOT NULL,
    status VARCHAR(50) NOT NULL,
    FOREIGN KEY (sender_user_id) REFERENCES users(id),
    FOREIGN KEY (receiver_user_id) REFERENCES users(id),
    FOREIGN KEY (group_id) REFERENCES `groups`(id)
) ENGINE=InnoDB;


-- Add the new columns to the messages table
ALTER TABLE messages
    ADD COLUMN message_type VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    ADD COLUMN file_name VARCHAR(255),
    ADD COLUMN file_size BIGINT,
    ADD COLUMN mime_type VARCHAR(255);

-- Create a directory to store media files if it doesn't exist
-- Note: This SQL comment is just to remind you to create this directory on the server.
-- You'll need to create the media files directory in your application code:
-- e.g., mkdir -p /path/to/your/application/media_files

-- Note: For a production environment, you might want to consider:
-- 1. Using a dedicated file storage service
-- 2. Implementing file deduplication
-- 3. Setting up proper backup systems for media