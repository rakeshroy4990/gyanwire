package com.gyanwire.eval;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;

public final class EvalSearchMain {

    private EvalSearchMain() {
    }

    public static void main(String[] args) throws Exception {
        Path root = EvalRunner.repoRoot();
        Path golden = root.resolve("eval/golden");
        Path reports = root.resolve("eval/reports");
        Path written = new EvalRunner().writeReport(golden, reports);
        System.out.println(new ObjectMapper().writeValueAsString(
                new ObjectMapper().readTree(written.toFile())));
        System.out.println("Wrote " + written);
    }
}
