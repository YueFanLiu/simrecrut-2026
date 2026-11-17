package fr.isep.simrecrut.config;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

/** Verify desktop lookup, literal parsing and isolation from database configuration. */
class DesktopMailEnvironmentTest {
    @TempDir Path directory;
    @Test void loadsDesktopMailboxAndOverridesOnlyMailValues() throws Exception {
        Path desktop = Files.createDirectories(directory.resolve("Desktop"));
        Files.writeString(desktop.resolve("simrecrut-gmail.private.env"), "# private test\nSMTP_USERNAME=teammate@example.test\nSMTP_PASSWORD='literal$()=value'\nSMTP_PORT=587\n");
        var env = new MockEnvironment().withProperty("user.home",directory.toString())
                .withProperty("SMTP_USERNAME","old@example.test").withProperty("DB_URL","unchanged");
        new DesktopMailEnvironment().postProcessEnvironment(env,null);
        assertEquals("teammate@example.test",env.getProperty("SMTP_USERNAME"));
        assertEquals("literal$()=value",env.getProperty("SMTP_PASSWORD"));
        assertEquals("unchanged",env.getProperty("DB_URL"));
    }
    @Test void supportsRedirectedDesktopAndRejectsNonMailKeys() throws Exception {
        Path file = directory.resolve("private.env");
        Files.writeString(file,"SMTP_HOST=smtp.gmail.com\n");
        var env = new MockEnvironment().withProperty("SIMRECRUT_MAIL_FILE",file.toString());
        new DesktopMailEnvironment().postProcessEnvironment(env,null);
        assertEquals("smtp.gmail.com",env.getProperty("SMTP_HOST"));
        Files.writeString(file,"DB_PASSWORD=not-allowed\n");
        assertThrows(IllegalStateException.class,()->new DesktopMailEnvironment().postProcessEnvironment(new MockEnvironment().withProperty("SIMRECRUT_MAIL_FILE",file.toString()),null));
    }
    @Test void missingDesktopFileKeepsExistingConfiguration() {
        var env = new MockEnvironment().withProperty("user.home",directory.toString()).withProperty("SMTP_HOST","existing");
        new DesktopMailEnvironment().postProcessEnvironment(env,null);
        assertEquals("existing",env.getProperty("SMTP_HOST"));
    }
}
