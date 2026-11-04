package fr.isep.simrecrut.workspace;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Activate periodic cleanup in both authenticated and local workspace modes. */
@Configuration
@EnableScheduling
public class RetentionScheduler {}
