-- Independent identity and access-control foundation.
-- Apply through Flyway after V1 and V2.
-- All timestamps are stored and interpreted in UTC.
-- Logical references are validated by application services.
-- No account credentials or default administrator are created.

CREATE TABLE `sim_user` (
                            `id` BIGINT NOT NULL AUTO_INCREMENT,
                            `public_id` CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
                            `username` VARCHAR(20) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
                            `email` VARCHAR(254) COLLATE utf8mb4_0900_as_ci NOT NULL,
                            `password_hash` VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
                            `status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE',
                            `email_verified_at` DATETIME(3) NULL,
                            `password_updated_at` DATETIME(3) NOT NULL,
                            `security_version` BIGINT NOT NULL DEFAULT 0,
                            `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                            `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                            PRIMARY KEY (`id`),
                            UNIQUE KEY `uq_sim_user_public_id` (`public_id`),
                            UNIQUE KEY `uq_sim_user_username` (`username`),
                            UNIQUE KEY `uq_sim_user_email` (`email`),
                            CONSTRAINT `ck_sim_user_status`
                                CHECK (`status` IN ('ACTIVE', 'DISABLED')),
                            CONSTRAINT `ck_sim_user_security_version`
                                CHECK (`security_version` >= 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sim_role` (
                            `id` BIGINT NOT NULL AUTO_INCREMENT,
                            `role_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
                            `name` VARCHAR(128) NOT NULL,
                            `description` VARCHAR(512) NULL,
                            `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                            `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                            PRIMARY KEY (`id`),
                            UNIQUE KEY `uq_sim_role_code` (`role_code`),
                            CONSTRAINT `ck_sim_role_code`
                                CHECK (`role_code` IN ('CANDIDATE', 'HR', 'RESEARCHER', 'ADMIN'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Logical reference: user_id -> sim_user.id.
-- Logical reference: role_id -> sim_role.id.
CREATE TABLE `sim_user_role` (
                                 `id` BIGINT NOT NULL AUTO_INCREMENT,
                                 `user_id` BIGINT NOT NULL,
                                 `role_id` BIGINT NOT NULL,
                                 `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                                 `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                                 PRIMARY KEY (`id`),
                                 UNIQUE KEY `uq_sim_user_role` (`user_id`, `role_id`),
                                 KEY `ix_sim_user_role_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sim_permission` (
                                  `id` BIGINT NOT NULL AUTO_INCREMENT,
                                  `permission_code` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
                                  `name` VARCHAR(128) NOT NULL,
                                  `description` VARCHAR(512) NULL,
                                  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                                  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                                  PRIMARY KEY (`id`),
                                  UNIQUE KEY `uq_sim_permission_code` (`permission_code`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Logical reference: role_id -> sim_role.id.
-- Logical reference: permission_id -> sim_permission.id.
CREATE TABLE `sim_role_permission` (
                                       `id` BIGINT NOT NULL AUTO_INCREMENT,
                                       `role_id` BIGINT NOT NULL,
                                       `permission_id` BIGINT NOT NULL,
                                       `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                                       `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                                       PRIMARY KEY (`id`),
                                       UNIQUE KEY `uq_sim_role_permission` (`role_id`, `permission_id`),
                                       KEY `ix_sim_role_permission_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `sim_role` (`role_code`, `name`, `description`)
VALUES
    ('CANDIDATE', 'Candidate', 'Self-service candidate account.'),
    ('HR', 'HR', 'Self-service account for managing owned recruitment jobs.'),
    ('RESEARCHER', 'Researcher', 'Research access granted by an administrator.'),
    ('ADMIN', 'Administrator', 'Controlled account and system administration.');
