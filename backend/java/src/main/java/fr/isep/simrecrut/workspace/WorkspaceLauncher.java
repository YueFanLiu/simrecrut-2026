package fr.isep.simrecrut.workspace;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Local single-user adapter; separate from the authenticated team backend entry point. */
@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@ComponentScan(basePackageClasses = WorkspaceLauncher.class)
@Profile("resume-workspace")
public class WorkspaceLauncher {
    public static void main(String[] args) {
        new org.springframework.boot.builder.SpringApplicationBuilder(WorkspaceLauncher.class)
            .profiles("resume-workspace").run(args);
    }
}
