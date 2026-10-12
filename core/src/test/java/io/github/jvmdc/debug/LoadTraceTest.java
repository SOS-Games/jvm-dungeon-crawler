package io.github.jvmdc.debug;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadTraceTest {
    @Test
    void slowerStepIsListedFirst() {
        LoadTrace trace = new LoadTrace();
        trace.total = 16;
        trace.done = 4;
        trace.add("fill voxels", 2_000_000L);
        trace.add("mesh shell", 9_000_000L);
        trace.add("fill voxels", 1_000_000L);
        trace.noteChunk(4_000_000L);
        String report = trace.report();
        assertTrue(report.contains("chunk 4 of 16"));
        assertTrue(report.contains("x2"));
        assertTrue(report.contains("slowest chunk"));
        assertTrue(report.indexOf("mesh shell") < report.indexOf("fill voxels"));
    }

    @Test
    void nestedStepIsNotCountedTwice() {
        LoadTrace trace = new LoadTrace();
        trace.begin("place props");
        trace.begin("wall depth");
        trace.end();
        trace.end();
        String report = trace.report();
        assertTrue(report.contains("place props"));
        assertTrue(report.contains("wall depth"));
        assertTrue(report.contains("x1"));
    }
}
