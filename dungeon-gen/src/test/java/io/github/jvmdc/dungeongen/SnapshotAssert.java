package io.github.jvmdc.dungeongen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Compares a rendered map with a text file on the test classpath. */
public final class SnapshotAssert {
    private SnapshotAssert() {
    }

    public static void matches(String resource, String actual) {
        String expected;
        try (InputStream in = SnapshotAssert.class.getClassLoader().getResourceAsStream(resource)) {
            assertTrue(in != null, resource);
            expected = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new AssertionError(resource, e);
        }
        assertEquals(expected, actual, resource);
    }
}
