package eu.amadeos.apps.familytree.bootstrap;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationTest {
    @Test
    void theFileToImportComesFromTheArguments() {
        assertEquals(Optional.empty(), AmadeFamilyTreeApplication.file(new String[]{}));
        assertEquals(Optional.of(Path.of("/home/r/tree.ged")),
                AmadeFamilyTreeApplication.file(new String[]{"--x", "/home/r/tree.ged"}));
        assertEquals(Optional.of(Path.of("/home/r/my tree.ged")),
                AmadeFamilyTreeApplication.file(new String[]{"file:///home/r/my%20tree.ged"}));
    }

    @Test
    void versionFromTheJarOrDev() {
        assertEquals("dev", AmadeFamilyTreeApplication.version());
    }
}
