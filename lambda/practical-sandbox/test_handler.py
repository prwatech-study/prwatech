"""Unit tests for the practical-sandbox static guard and exec namespace.

Run from this directory: python3 -m unittest test_handler -v

boto3 and the data libs are stubbed if absent so the guard logic is testable
without the Lambda container image.
"""

import io
import os
import sys
import types
import unittest
from contextlib import redirect_stdout

os.environ.setdefault("DATASET_BUCKET", "test-bucket")
sys.modules.setdefault("boto3", types.SimpleNamespace(client=lambda *a, **k: None))
for _name in ("pandas", "numpy", "matplotlib"):
    try:
        __import__(_name)
    except ImportError:
        sys.modules[_name] = types.ModuleType(_name)

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import handler  # noqa: E402

OOP_SUPER = (
    "class Vehicle:\n"
    "    def __init__(self, brand):\n"
    "        self.brand = brand\n"
    "class Car(Vehicle):\n"
    "    def __init__(self, brand, doors):\n"
    "        super().__init__(brand)\n"
    "        self.doors = doors\n"
    "c = Car('Tata', 4)\n"
    "print(c.brand, c.doors)\n"
)

OOP_EXPLICIT_PARENT_INIT = (
    "class A:\n"
    "    def __init__(self, x):\n"
    "        self.x = x\n"
    "class B:\n"
    "    def __init__(self, y):\n"
    "        self.y = y\n"
    "class C(A, B):\n"
    "    def __init__(self, x, y):\n"
    "        A.__init__(self, x)\n"
    "        B.__init__(self, y)\n"
    "print(C(1, 2).x)\n"
)


class ValidateAllowsCourseMaterialTests(unittest.TestCase):
    def test_super_and_inherited_init(self):
        self.assertEqual(handler.validate(OOP_SUPER), [])

    def test_explicit_parent_init_call(self):
        self.assertEqual(handler.validate(OOP_EXPLICIT_PARENT_INIT), [])

    def test_teachable_dunders_and_decorators(self):
        code = (
            "class Box:\n"
            "    def __len__(self):\n"
            "        return 3\n"
            "    def __str__(self):\n"
            "        return 'box'\n"
            "    @staticmethod\n"
            "    def kind():\n"
            "        return 'k'\n"
            "    @classmethod\n"
            "    def make(cls):\n"
            "        return cls()\n"
            "print(type(Box()).__name__, hasattr(Box, 'kind'))\n"
        )
        self.assertEqual(handler.validate(code), [])

    def test_dunder_main_guard(self):
        self.assertEqual(
            handler.validate("if __name__ == '__main__':\n    print('hi')"), []
        )


class ValidateStillRejectsEscapesTests(unittest.TestCase):
    def assert_rejected(self, code):
        self.assertTrue(handler.validate(code), f"expected rejection: {code!r}")

    def test_class_introspection_chain(self):
        self.assert_rejected("x = ().__class__.__mro__[1].__subclasses__()")

    def test_globals_hop_off_allowed_dunder(self):
        self.assert_rejected("f = str.__init__.__globals__")

    def test_getattr_string_bypass(self):
        self.assert_rejected("x = getattr((), '__class__')")

    def test_bare_dunder_names(self):
        self.assert_rejected("b = __builtins__")
        self.assert_rejected("f = __build_class__")

    def test_io_and_dynamic_exec(self):
        for code in ("open('x')", "eval('1')", "exec('1')", "input()", "vars()"):
            self.assert_rejected(code)

    def test_disallowed_import(self):
        self.assert_rejected("import os")


class ExecNamespaceTests(unittest.TestCase):
    def run_code(self, code):
        g = handler.build_sandbox_globals(None, None)
        buf = io.StringIO()
        with redirect_stdout(buf):
            exec(compile(code, "<t>", "exec"), g)
        return buf.getvalue()

    def test_class_statement_executes(self):
        self.assertEqual(self.run_code(OOP_SUPER), "Tata 4\n")

    def test_multiple_inheritance_executes(self):
        self.assertEqual(self.run_code(OOP_EXPLICIT_PARENT_INIT), "1\n")

    def test_exceptions_bound(self):
        out = self.run_code(
            "try:\n"
            "    1 / 0\n"
            "except ZeroDivisionError as e:\n"
            "    print(type(e).__name__)\n"
        )
        self.assertEqual(out, "ZeroDivisionError\n")

    def test_blocked_names_stay_nameerrors_at_runtime(self):
        with self.assertRaises(NameError):
            self.run_code("getattr((), '__class__')")


if __name__ == "__main__":
    unittest.main()
