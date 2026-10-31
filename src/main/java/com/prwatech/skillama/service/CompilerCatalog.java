package com.prwatech.skillama.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps a Skillama course to a compiler family and the runtimes that family accepts.
 * The ids match {@code lambda/compiler/handler.py}.
 */
public final class CompilerCatalog {

    public enum Kind {
        /** Restricted pandas sandbox. CSV lessons stay here. */
        PRACTICE,
        /** A skillama-compiler-* Lambda. */
        COMPILER,
        /** AI-simulated. No sandbox. */
        NONE
    }

    public record RuntimeOption(String id, String label) {}

    public record Profile(
            String languageId,
            Kind kind,
            String lambdaFamily,
            String badge,
            boolean preferPytest,
            List<RuntimeOption> runtimes
    ) {
        public RuntimeOption select(String requested) {
            if (requested != null && !requested.isBlank()) {
                String id = requested.trim();
                for (RuntimeOption option : runtimes) {
                    if (option.id().equals(id)) {
                        return option;
                    }
                }
            }
            return runtimes.isEmpty() ? null : runtimes.get(0);
        }
    }

    private static final List<RuntimeOption> PYTHON_VERSIONS = List.of(
            new RuntimeOption("python3.12", "Python 3.12"),
            new RuntimeOption("python3.13", "Python 3.13"),
            new RuntimeOption("python3.14", "Python 3.14"),
            new RuntimeOption("python3.15", "Python 3.15"));

    public static final Profile PYTHON = compiler(
            "python", "python", "Python compiler", false, PYTHON_VERSIONS);
    public static final Profile PYTHON_TESTING = compiler(
            "python-testing", "python", "Python compiler", true, PYTHON_VERSIONS);
    public static final Profile PRACTICE = new Profile(
            "python-practice", Kind.PRACTICE, null, "Python sandbox", false, List.of());
    public static final Profile JAVA = compiler("java", "java", "Java compiler", false, List.of(
            new RuntimeOption("java17", "Java 17"),
            new RuntimeOption("java21", "Java 21"),
            new RuntimeOption("java25", "Java 25")));
    public static final Profile C = compiler("c", "native", "C compiler", false, List.of(
            new RuntimeOption("c17", "C17"),
            new RuntimeOption("c11", "C11")));
    public static final Profile CPP = compiler("cpp", "native", "C++ compiler", false, List.of(
            new RuntimeOption("cpp17", "C++17"),
            new RuntimeOption("cpp20", "C++20")));
    public static final Profile DOTNET = compiler("dotnet", "dotnet", ".NET compiler", false, List.of(
            new RuntimeOption("dotnet8", ".NET 8"),
            new RuntimeOption("dotnet10", ".NET 10")));
    public static final Profile GO = compiler("go", "go", "Go compiler", false, List.of(
            new RuntimeOption("go1.27", "Go 1.27"),
            new RuntimeOption("go1.26", "Go 1.26")));
    public static final Profile SCALA = compiler("scala", "scala", "Scala compiler", false, List.of(
            new RuntimeOption("scala3", "Scala 3"),
            new RuntimeOption("scala213", "Scala 2.13")));
    public static final Profile SQL = compiler("sql", "sql", "SQLite", false, List.of(
            new RuntimeOption("sqlite3", "SQLite 3")));
    public static final Profile SPARK_PYTHON = compiler("spark-python", "spark", "PySpark", false, List.of(
            new RuntimeOption("spark35-python", "Spark 3.5"),
            new RuntimeOption("spark42-python", "Spark 4.2")));
    public static final Profile SPARK_SCALA = compiler("spark-scala", "spark", "Spark", false, List.of(
            new RuntimeOption("spark35-scala", "Spark 3.5"),
            new RuntimeOption("spark42-scala", "Spark 4.2")));
    public static final Profile NONE = new Profile(null, Kind.NONE, null, "Compiler", false, List.of());

    private static final Map<String, Profile> BY_LANGUAGE = Map.ofEntries(
            Map.entry(PYTHON.languageId(), PYTHON),
            Map.entry(PYTHON_TESTING.languageId(), PYTHON_TESTING),
            Map.entry(PRACTICE.languageId(), PRACTICE),
            Map.entry(JAVA.languageId(), JAVA),
            Map.entry(C.languageId(), C),
            Map.entry(CPP.languageId(), CPP),
            Map.entry(DOTNET.languageId(), DOTNET),
            Map.entry(GO.languageId(), GO),
            Map.entry(SCALA.languageId(), SCALA),
            Map.entry(SQL.languageId(), SQL),
            Map.entry(SPARK_PYTHON.languageId(), SPARK_PYTHON),
            Map.entry(SPARK_SCALA.languageId(), SPARK_SCALA),
            Map.entry("ai", NONE));

    private CompilerCatalog() {}

    public static boolean isKnownLanguage(String languageId) {
        if (languageId == null || languageId.isBlank()) {
            return false;
        }
        return BY_LANGUAGE.containsKey(languageId.trim().toLowerCase(Locale.ROOT));
    }

    public static Profile resolve(String compilerLanguage, String courseName) {
        String lang = compilerLanguage == null ? "" : compilerLanguage.trim().toLowerCase(Locale.ROOT);
        if (!lang.isEmpty() && !"auto".equals(lang)) {
            Profile explicit = BY_LANGUAGE.get(lang);
            if (explicit != null) {
                return explicit;
            }
        }
        return infer(courseName);
    }

    static Profile infer(String courseName) {
        String n = courseName == null ? "" : courseName.toLowerCase(Locale.ROOT);
        if (n.contains("pyspark")) return SPARK_PYTHON;
        if (n.contains("spark")) return SPARK_SCALA;
        if (n.contains("c++") || n.contains("cpp")) return CPP;
        if (n.contains("c#") || n.contains(".net") || n.contains("csharp") || n.contains("dotnet")) return DOTNET;
        if (n.contains("golang") || n.equals("go") || n.startsWith("go ")) return GO;
        if (n.contains("scala")) return SCALA;
        if (n.contains("java") && !n.contains("javascript")) return JAVA;
        if (n.contains("power bi") || n.contains("powerbi")) return NONE;
        if (n.contains("deep learning")) return NONE;
        if (n.contains("generative") || n.contains("gen ai") || n.contains("genai")) return NONE;
        if (n.contains("data analyst")) return PRACTICE;
        if (n.contains("data science") || n.contains("data-science")) return PRACTICE;
        if (n.contains("machine learning")) return PYTHON;
        if (n.contains("python") && n.contains("test")) return PYTHON_TESTING;
        if (n.contains("python")) return PYTHON;
        if (n.contains("sql")) return SQL;
        if (n.contains("c programming") || n.equals("c") || n.startsWith("c ")) return C;
        return NONE;
    }

    private static Profile compiler(String languageId, String lambdaFamily, String badge,
                                    boolean preferPytest, List<RuntimeOption> runtimes) {
        return new Profile(languageId, Kind.COMPILER, lambdaFamily, badge, preferPytest, runtimes);
    }
}
