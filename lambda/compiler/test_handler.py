import os
import unittest

import handler


class HandlerPolicyTest(unittest.TestCase):
    def test_family_allowlist_blocks_other_runtimes(self):
        previous = os.environ.get("COMPILER_FAMILY")
        os.environ["COMPILER_FAMILY"] = "java"
        try:
            result = handler.handler({"runtime": "go1.27", "code": "package main"}, None)
        finally:
            if previous is None:
                os.environ.pop("COMPILER_FAMILY", None)
            else:
                os.environ["COMPILER_FAMILY"] = previous
        self.assertEqual("error", result["status"])
        self.assertIn("not available", result["error"])

    def test_rejects_empty_code_and_oversized_stdin(self):
        self.assertEqual("error", handler.handler({"runtime": "python3.12", "code": "  "}, None)["status"])
        huge = "x" * (handler.MAX_STDIN_BYTES + 1)
        result = handler.handler({"runtime": "c17", "code": "int main(){return 0;}", "stdin": huge}, None)
        self.assertIn("stdin", result["error"])

    def test_child_env_strips_aws_credentials(self):
        os.environ["AWS_ACCESS_KEY_ID"] = "test-key"
        os.environ["AWS_SECRET_ACCESS_KEY"] = "test-secret"
        os.environ["AWS_SESSION_TOKEN"] = "test-token"
        env = handler.child_env()
        self.assertNotIn("AWS_ACCESS_KEY_ID", env)
        self.assertNotIn("AWS_SECRET_ACCESS_KEY", env)
        self.assertNotIn("AWS_SESSION_TOKEN", env)
        self.assertEqual("/tmp", env["HOME"])

    def test_pytest_detection_only_for_auto_runner(self):
        self.assertTrue(handler.wants_pytest("def test_one():\n    assert True\n", "auto"))
        self.assertFalse(handler.wants_pytest("def test_one():\n    pass\n", "script"))
        self.assertFalse(handler.wants_pytest("print(1)\n", "auto"))

    def test_java_method_snippet_is_wrapped_and_not_run(self):
        snippet = """
// This method calculates factorial of a number
public int factorial(int n) {
    if (n <= 1) return 1;  // Base case
    /* Recursive call - multi-line comment
       for clarity on logic flow */
    return n * factorial(n - 1);
}
/**
 * Calculates factorial used in Javadoc generation
 * @param n input number
 * @return factorial result
 */
"""
        name, source, has_main = handler.prepare_java_source(snippet)
        self.assertEqual("Main", name)
        self.assertFalse(has_main)
        self.assertIn("public class Main {", source)
        self.assertIn("public int factorial", source)
        self.assertIn("@param n", source)

    def test_complete_java_program_is_left_alone(self):
        program = "public class Hello {\n  public static void main(String[] args) {\n    System.out.println(1);\n  }\n}\n"
        name, source, has_main = handler.prepare_java_source(program)
        self.assertEqual("Hello", name)
        self.assertTrue(has_main)
        self.assertNotIn("public class Main", source)

    def test_java_and_scala_names(self):
        self.assertEqual("Hello", handler.public_class_name("public class Hello { }"))
        self.assertEqual("Main", handler.public_class_name("class Hidden {}"))
        self.assertEqual("WordCount", handler.scala_type_name("object WordCount { def main(args: Array[String]) = () }"))

    def test_sqlite_rejects_dot_commands_and_attach(self):
        self.assertIsNotNone(handler.sqlite_rejection(".shell ls"))
        self.assertIsNotNone(handler.sqlite_rejection("ATTACH DATABASE 'x' AS other;"))
        self.assertIsNone(handler.sqlite_rejection("SELECT 1;"))

    def test_cpp_is_not_treated_as_c_by_family_sets(self):
        self.assertIn("cpp17", handler.FAMILIES["native"])
        self.assertIn("c17", handler.FAMILIES["native"])
        self.assertNotIn("cpp17", handler.FAMILIES["java"])


if __name__ == "__main__":
    unittest.main()
