package fr.isep.simrecrut.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verify the adapter cannot reuse another authenticated user's temporary index. */
class UserResumeServicesTest {
    @TempDir Path directory;
    @Test void isolatesUsersAndReusesOnlyOwnService() throws Exception {
        UserResumeServices users = new UserResumeServices(directory.toString());
        try {
            ResumeService first = users.forUser(1);
            assertSame(first, users.forUser(1));
            ResumeService second = users.forUser(2);
            assertNotSame(first, second);
            assertEquals(directory.resolve("1").toAbsolutePath(), first.data);
            assertEquals(directory.resolve("2").toAbsolutePath(), second.data);
            assertThrows(IllegalArgumentException.class, () -> users.forUser(0));
        } finally { users.shutdown(); }
    }
}
