package com.prwatech.skillama.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompilerCatalogTest {

    @Test
    void catalogCoursesMapToTheRightFamily() {
        assertEquals(CompilerCatalog.CPP, CompilerCatalog.resolve(null, "C++ Programming"));
        assertEquals(CompilerCatalog.C, CompilerCatalog.resolve(null, "C Programming"));
        assertEquals(CompilerCatalog.DOTNET, CompilerCatalog.resolve(null, ".NET Programming"));
        assertEquals(CompilerCatalog.JAVA, CompilerCatalog.resolve(null, "Java Programming"));
        assertEquals(CompilerCatalog.GO, CompilerCatalog.resolve(null, "GOLANG"));
        assertEquals(CompilerCatalog.SCALA, CompilerCatalog.resolve(null, "Scala"));
        assertEquals(CompilerCatalog.SPARK_SCALA, CompilerCatalog.resolve(null, "Apache Spark & Scala"));
        assertEquals(CompilerCatalog.SPARK_PYTHON, CompilerCatalog.resolve(null, "PySpark"));
        assertEquals(CompilerCatalog.SQL, CompilerCatalog.resolve(null, "SQL"));
        assertEquals(CompilerCatalog.PYTHON, CompilerCatalog.resolve(null, "Python Course"));
        assertEquals(CompilerCatalog.PYTHON, CompilerCatalog.resolve(null, "Advanced Python"));
        assertEquals(CompilerCatalog.PYTHON_TESTING, CompilerCatalog.resolve(null, "Python Testing"));
        assertEquals(CompilerCatalog.PYTHON, CompilerCatalog.resolve(null, "Machine Learning"));
        assertEquals(CompilerCatalog.PRACTICE, CompilerCatalog.resolve(null, "Data science with AI"));
        assertEquals(CompilerCatalog.PRACTICE, CompilerCatalog.resolve(null, "Data Analyst"));
        assertEquals(CompilerCatalog.Kind.NONE, CompilerCatalog.resolve(null, "Deep Learning").kind());
        assertEquals(CompilerCatalog.Kind.NONE, CompilerCatalog.resolve(null, "Generative AI (Gen AI)").kind());
        assertEquals(CompilerCatalog.Kind.NONE, CompilerCatalog.resolve(null, "Power BI").kind());
    }

    @Test
    void javaCourseCannotSelectGo() {
        CompilerCatalog.RuntimeOption selected =
                CompilerCatalog.JAVA.select("go1.27");
        assertEquals("java17", selected.id());
        assertEquals("Java 17", selected.label());
    }

    @Test
    void pythonTestingPrefersPytestAndExplicitLanguageOverridesTheName() {
        assertTrue(CompilerCatalog.PYTHON_TESTING.preferPytest());
        assertFalse(CompilerCatalog.PYTHON.preferPytest());
        assertEquals(CompilerCatalog.JAVA, CompilerCatalog.resolve("java", "Python Course"));
        assertEquals(CompilerCatalog.Kind.NONE, CompilerCatalog.resolve("ai", "Java Programming").kind());
        assertNull(CompilerCatalog.NONE.select("java17"));
    }

    @Test
    void defaultsAreTheFirstListedRuntime() {
        assertEquals("python3.12", CompilerCatalog.PYTHON.select(null).id());
        assertEquals("python3.15", CompilerCatalog.PYTHON.select("python3.15").id());
        assertEquals("spark35-scala", CompilerCatalog.SPARK_SCALA.select("").id());
        assertEquals("go1.27", CompilerCatalog.GO.select(null).id());
        assertTrue(CompilerCatalog.isKnownLanguage("spark-python"));
        assertFalse(CompilerCatalog.isKnownLanguage("ruby"));
    }
}
