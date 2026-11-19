"""12 种支持格式的解析回归测试。

在 parser 容器内运行：
    python -m parser.tests.test_formats
"""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

from parser.pipeline.router import create_default_router
from parser.tests.generate_samples import OUT, main as generate_samples

EXPECTED = {
    "txt": "FormatRegression",
    "md": "FormatRegression",
    "csv": "FormatRegression",
    "docx": "FormatRegression",
    "doc": "FormatRegression",
    "pdf": "FormatRegression",
    "xlsx": "FormatRegression",
    "xls": "FormatRegression",
    "pptx": "FormatRegression",
    "ppt": "FormatRegression",
    "png": ("OCR", "123"),
    "jpg": ("OCR", "123"),
}


class FormatRegressionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        generate_samples()
        cls.router = create_default_router()

    def test_all_supported_formats(self) -> None:
        failures = []
        for ext, tokens in EXPECTED.items():
            tokens = tokens if isinstance(tokens, tuple) else (tokens,)
            path = OUT / f"sample.{ext}"
            with self.subTest(ext=ext):
                if not path.exists():
                    failures.append(f"{ext}: 样本不存在")
                    continue
                try:
                    result = self.router.parse(str(path))
                except Exception as exc:
                    failures.append(f"{ext}: 解析异常 {exc}")
                    continue
                elements = result.get("elements", [])
                content = "\n".join(str(e.get("content", "")) for e in elements)
                metadata = result.get("metadata", {})
                if not elements:
                    failures.append(f"{ext}: 无元素")
                missing = [t for t in tokens if t not in content]
                if missing:
                    failures.append(f"{ext}: 缺少标记 {missing}，实际片段={content[:80]!r}")
                if metadata.get("placeholder"):
                    failures.append(f"{ext}: 仍为占位解析")
                print(f"[OK] {ext:4s} elements={len(elements):2d} backend={metadata.get('ocr_engine', '-')}")
        if failures:
            self.fail("格式回归失败:\n" + "\n".join(failures))


if __name__ == "__main__":
    sys.exit(0 if unittest.main(exit=False).result.wasSuccessful() else 1)
