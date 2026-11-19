"""生成 12 种支持格式的回归样本。

在 parser 容器内运行：
    python -m parser.tests.generate_samples
"""

from __future__ import annotations

import subprocess
from pathlib import Path

from docx import Document
from openpyxl import Workbook
from pptx import Presentation
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "data" / "format_samples"


def convert_with_libreoffice(source: Path, target_ext: str, target_name: str) -> Path:
    target = OUT / target_name
    if target.exists():
        target.unlink()
    subprocess.run(
        [
            "soffice", "--headless", "--norestore",
            "--convert-to", target_ext,
            "--outdir", str(OUT), str(source),
        ],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=180,
    )
    produced = OUT / f"{source.stem}.{target_ext}"
    if not produced.exists():
        candidates = [p for p in OUT.glob(f"{source.stem}.*") if p.suffix.lower() == f".{target_ext}"]
        if not candidates:
            raise RuntimeError(f"LibreOffice 未生成目标文件: {produced}")
        produced = candidates[0]
    if produced != target:
        produced.replace(target)
    return target


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    marker = "格式回归测试 FormatRegression 2026"

    (OUT / "sample.txt").write_text(marker + "\n第二段内容。\n", encoding="utf-8")
    (OUT / "sample.md").write_text(
        "# 格式回归测试\n\nFormatRegression 2026\n\n## 第二节\n表格回归内容。\n",
        encoding="utf-8",
    )
    (OUT / "sample.csv").write_text(
        "字段,值\n标记,FormatRegression\n说明,格式回归测试\n", encoding="utf-8"
    )

    doc = Document()
    doc.add_heading("格式回归测试", level=1)
    doc.add_paragraph(marker)
    table = doc.add_table(rows=2, cols=2)
    table.cell(0, 0).text = "字段"
    table.cell(0, 1).text = "值"
    table.cell(1, 0).text = "标记"
    table.cell(1, 1).text = "FormatRegression"
    docx_path = OUT / "sample.docx"
    doc.save(docx_path)
    convert_with_libreoffice(docx_path, "doc", "sample.doc")

    pdf_source = OUT / "sample_pdf_source.docx"
    doc.save(pdf_source)
    convert_with_libreoffice(pdf_source, "pdf", "sample.pdf")
    pdf_source.unlink()

    workbook = Workbook()
    sheet = workbook.active
    sheet.title = "回归"
    sheet.append(["字段", "值"])
    sheet.append(["标记", "FormatRegression"])
    xlsx_path = OUT / "sample.xlsx"
    workbook.save(xlsx_path)
    convert_with_libreoffice(xlsx_path, "xls", "sample.xls")

    prs = Presentation()
    slide = prs.slides.add_slide(prs.slide_layouts[1])
    slide.shapes.title.text = "格式回归测试"
    slide.placeholders[1].text = marker
    pptx_path = OUT / "sample.pptx"
    prs.save(pptx_path)
    convert_with_libreoffice(pptx_path, "ppt", "sample.ppt")

    image = Image.new("RGB", (800, 200), "white")
    draw = ImageDraw.Draw(image)
    draw.text((40, 80), "OCRTEST123", fill="black")
    image.save(OUT / "sample.png")
    image.save(OUT / "sample.jpg")

    print(f"已生成 {len(list(OUT.iterdir()))} 个样本到 {OUT}")


if __name__ == "__main__":
    main()
