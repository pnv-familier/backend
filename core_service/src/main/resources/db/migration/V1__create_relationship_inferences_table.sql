-- Migration script for relationship_inferences table
-- This table stores inferred relationships between family members
-- Uses email instead of user_id for better integration with AI service (MongoDB)

CREATE TABLE IF NOT EXISTS relationship_inferences (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_id VARCHAR(255) NOT NULL,
    user1_email VARCHAR(255) NOT NULL,
    user2_email VARCHAR(255) NOT NULL,
    relation_type VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Unique constraint to prevent duplicate relationships
    CONSTRAINT uk_user_email_pair UNIQUE (user1_email, user2_email),
    
    -- Indexes for better query performance
    INDEX idx_family_id (family_id),
    INDEX idx_user1_email (user1_email),
    INDEX idx_user2_email (user2_email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Add comment to table
ALTER TABLE relationship_inferences COMMENT = 'Stores inferred relationships between family members. Uses email for AI service integration';
