#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""把面试手册 Markdown 渲染成带中文排版的 DOCX。

用法：
    python build_docx.py <input.md> <output.docx> [--no-cover]
"""
import re
import sys

from docx import Document
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

BODY = "\u5b8b\u4f53"              # 宋体
HEAD = "\u5fae\u8f6f\u96c5\u9ed1"    # 微软雅黑
MONO = "Consolas"
INK = RGBColor(0x1A, 0x1A, 0x1A)
GRAY = RGBColor(0x44, 0x44, 0x44)
ACCENT = RGBColor(0x1F, 0x4E, 0x79)
CODE = RGBColor(0xA6, 0x2B, 0x2B)
LINK = RGBColor(0x0B, 0x53, 0x94)

DENSE = False   # --compact：压缩行距与字号，适合速记卡这类短文档


def dense(a, b):
    return b if DENSE else a


def set_font(run, name=BODY, east=None, size=None, bold=None, color=None):
    run.font.name = name
    rpr = run._element.get_or_add_rPr()
    rfonts = rpr.find(qn("w:rFonts"))
    if rfonts is None:
        rfonts = OxmlElement("w:rFonts")
        rpr.append(rfonts)
    rfonts.set(qn("w:ascii"), name)
    rfonts.set(qn("w:hAnsi"), name)
    rfonts.set(qn("w:eastAsia"), east or name)
    if size is not None:
        run.font.size = Pt(size)
    if bold is not None:
        run.font.bold = bold
    if color is not None:
        run.font.color.rgb = color


def shade(parent, fill):
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), fill)
    parent.append(shd)


def border(p, side="left", color="BFBFBF", size="18"):
    ppr = p._p.get_or_add_pPr()
    pbdr = ppr.find(qn("w:pBdr"))
    if pbdr is None:
        pbdr = OxmlElement("w:pBdr")
        ppr.append(pbdr)
    el = OxmlElement("w:" + side)
    el.set(qn("w:val"), "single")
    el.set(qn("w:sz"), size)
    el.set(qn("w:space"), "6")
    el.set(qn("w:color"), color)
    pbdr.append(el)


def page_field(paragraph):
    run = paragraph.add_run()
    begin = OxmlElement("w:fldChar")
    begin.set(qn("w:fldCharType"), "begin")
    instr = OxmlElement("w:instrText")
    instr.set(qn("xml:space"), "preserve")
    instr.text = " PAGE "
    end = OxmlElement("w:fldChar")
    end.set(qn("w:fldCharType"), "end")
    for el in (begin, instr, end):
        run._r.append(el)
    set_font(run, BODY, size=9, color=RGBColor(0x80, 0x80, 0x80))


BOLD_RE = re.compile(r"(\*\*.+?\*\*)")
CODE_RE = re.compile(r"(`[^`]+`|\[[^\]]+\]\([^)]+\))")


def emit(paragraph, text, size, color, bold, font):
    for piece in CODE_RE.split(text):
        if not piece:
            continue
        if piece.startswith("`") and piece.endswith("`") and len(piece) > 2:
            set_font(paragraph.add_run(piece[1:-1]), MONO, east=HEAD,
                     size=size - 0.8, bold=bold, color=CODE)
        elif piece.startswith("[") and "](" in piece:
            m = re.match(r"\[([^\]]+)\]\(([^)]+)\)", piece)
            run = paragraph.add_run(m.group(1))
            set_font(run, font, size=size, bold=bold, color=LINK)
            run.font.underline = True
        else:
            set_font(paragraph.add_run(piece), font, size=size, bold=bold, color=color)


def inline(paragraph, text, size=10.5, color=INK, bold_all=False, font=BODY):
    """解析 **粗体** / `代码` / [链接](url)，支持粗体里嵌代码。"""
    for seg in BOLD_RE.split(text):
        if not seg:
            continue
        if seg.startswith("**") and seg.endswith("**") and len(seg) > 4:
            emit(paragraph, seg[2:-2], size, color, True, font)
        else:
            emit(paragraph, seg, size, color, bold_all, font)


def body(doc, text, space_after=4):
    p = doc.add_paragraph()
    pf = p.paragraph_format
    pf.space_before = Pt(0)
    pf.space_after = Pt(space_after)
    pf.line_spacing = dense(1.45, 1.3)
    inline(p, text, size=dense(10.5, 9.6))
    return p


def heading(doc, text, level):
    p = doc.add_paragraph()
    pf = p.paragraph_format
    pf.keep_with_next = True
    if level == 2:
        pf.space_before = Pt(dense(16, 10))
        pf.space_after = Pt(dense(6, 3))
        inline(p, text, size=dense(14, 12.5), color=ACCENT, bold_all=True, font=HEAD)
        border(p, "bottom", "C9D6E4", "6")
    elif level == 3:
        pf.space_before = Pt(dense(11, 7))
        pf.space_after = Pt(dense(4, 2))
        inline(p, text, size=dense(11.5, 10.8), color=RGBColor(0x2E, 0x2E, 0x2E), bold_all=True, font=HEAD)
    else:
        pf.space_before = Pt(9)
        pf.space_after = Pt(3)
        inline(p, text, size=11, color=GRAY, bold_all=True, font=HEAD)


def quote(doc, lines):
    for line in lines:
        p = doc.add_paragraph()
        pf = p.paragraph_format
        pf.left_indent = Cm(0.55)
        pf.right_indent = Cm(0.3)
        pf.space_before = Pt(2)
        pf.space_after = Pt(2)
        pf.line_spacing = 1.4
        border(p, "left")
        shade(p._p.get_or_add_pPr(), "F6F8FA")
        inline(p, line, size=10, color=RGBColor(0x33, 0x33, 0x33))


def code_block(doc, lines):
    p = doc.add_paragraph()
    pf = p.paragraph_format
    pf.left_indent = Cm(0.4)
    pf.space_before = Pt(4)
    pf.space_after = Pt(9)
    pf.line_spacing = 1.15
    pf.keep_together = True
    shade(p._p.get_or_add_pPr(), "F3F4F6")
    border(p, "left", "D0D7DE", "12")
    for i, line in enumerate(lines):
        if i:
            p.add_run().add_break()
        set_font(p.add_run(line if line else " "), MONO, east=HEAD, size=8.5,
                 color=RGBColor(0x24, 0x29, 0x2E))


def bullet(doc, text, level=0, label=None):
    p = doc.add_paragraph()
    pf = p.paragraph_format
    pf.left_indent = Cm(0.55 + 0.55 * level)
    pf.first_line_indent = Cm(-0.42)
    pf.space_before = Pt(0)
    pf.space_after = Pt(2)
    pf.line_spacing = dense(1.4, 1.28)
    marker = label or ("\u2022" if level == 0 else "\u25e6")
    set_font(p.add_run(marker + " "), BODY, size=dense(10.5, 9.6),
             bold=bool(label), color=ACCENT)
    inline(p, text, size=dense(10.5, 9.6))


TABLE_WIDTHS = {
    2: [4.8, 11.4],
    3: [3.4, 6.4, 6.4],
    4: [2.4, 4.2, 4.8, 4.8],
    5: [1.3, 2.5, 3.3, 5.6, 3.5],
}


def no_split(row):
    trpr = row._tr.get_or_add_trPr()
    el = OxmlElement("w:cantSplit")
    el.set(qn("w:val"), "true")
    trpr.append(el)


def table(doc, rows):
    header, rest = rows[0], rows[1:]
    ncols = len(header)
    t = doc.add_table(rows=1, cols=ncols)
    t.style = "Table Grid"
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    t.autofit = False
    widths = TABLE_WIDTHS.get(ncols, [16.2 / ncols] * ncols)
    if sum(widths) < 15.5:
        widths = [w * 16.2 / sum(widths) for w in widths]

    hdr = t.rows[0]
    no_split(hdr)
    for i, text in enumerate(header):
        cell = hdr.cells[i]
        cell.width = Cm(widths[i])
        p = cell.paragraphs[0]
        p.paragraph_format.space_before = Pt(3)
        p.paragraph_format.space_after = Pt(3)
        p.paragraph_format.line_spacing = 1.2
        inline(p, text, size=9, bold_all=True, font=HEAD)
        shade(cell._tc.get_or_add_tcPr(), "DCE6F1")

    trpr = hdr._tr.get_or_add_trPr()
    repeat = OxmlElement("w:tblHeader")
    repeat.set(qn("w:val"), "true")
    trpr.append(repeat)

    for row in rest:
        new_row = t.add_row()
        no_split(new_row)
        cells = new_row.cells
        for i in range(ncols):
            text = row[i] if i < len(row) else ""
            cells[i].width = Cm(widths[i])
            p = cells[i].paragraphs[0]
            p.paragraph_format.space_before = Pt(2)
            p.paragraph_format.space_after = Pt(2)
            p.paragraph_format.line_spacing = 1.25
            inline(p, text, size=dense(8.8, 8.2))

    gap = doc.add_paragraph()
    gap.paragraph_format.space_before = Pt(0)
    gap.paragraph_format.space_after = Pt(2)
    gap.paragraph_format.line_spacing = 0.6


def setup(doc, running_head):
    normal = doc.styles["Normal"]
    normal.font.name = BODY
    normal.font.size = Pt(10.5)
    normal.element.rPr.rFonts.set(qn("w:eastAsia"), BODY)
    normal.paragraph_format.line_spacing = 1.45
    normal.paragraph_format.space_after = Pt(4)

    sec = doc.sections[0]
    sec.page_width = Cm(21.0)
    sec.page_height = Cm(29.7)
    sec.top_margin = Cm(2.2)
    sec.bottom_margin = Cm(2.0)
    sec.left_margin = Cm(2.4)
    sec.right_margin = Cm(2.4)

    fp = sec.footer.paragraphs[0]
    fp.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_font(fp.add_run("\u2014 "), BODY, size=9, color=RGBColor(0x80, 0x80, 0x80))
    page_field(fp)
    set_font(fp.add_run(" \u2014"), BODY, size=9, color=RGBColor(0x80, 0x80, 0x80))

    hp = sec.header.paragraphs[0]
    hp.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    set_font(hp.add_run(running_head), HEAD, size=8.5, color=RGBColor(0x9A, 0x9A, 0x9A))


def cover(doc, title, subtitle, metas):
    for _ in range(5):
        doc.add_paragraph()
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(10)
    inline(p, title, size=30, color=ACCENT, bold_all=True, font=HEAD)

    p2 = doc.add_paragraph()
    p2.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p2.paragraph_format.space_after = Pt(4)
    inline(p2, subtitle, size=13, color=RGBColor(0x4A, 0x4A, 0x4A), font=HEAD)

    line = doc.add_paragraph()
    line.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_font(line.add_run("\u2014" * 18), HEAD, size=9, color=RGBColor(0xC0, 0xC0, 0xC0))

    for _ in range(2):
        doc.add_paragraph()
    for meta in metas:
        pm = doc.add_paragraph()
        pm.alignment = WD_ALIGN_PARAGRAPH.CENTER
        pm.paragraph_format.space_after = Pt(6)
        inline(pm, meta, size=10.5, color=GRAY, font=HEAD)
    doc.add_page_break()


def toc(doc, headings):
    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(10)
    inline(p, "\u76ee \u5f55", size=16, color=ACCENT, bold_all=True, font=HEAD)
    for text in headings:
        line = doc.add_paragraph()
        line.paragraph_format.left_indent = Cm(0.4)
        line.paragraph_format.space_after = Pt(3)
        line.paragraph_format.line_spacing = 1.3
        inline(line, text, size=10.5, color=RGBColor(0x25, 0x25, 0x25))
    doc.add_page_break()


def cells_of(line):
    return [c.strip() for c in line.strip().strip("|").split("|")]


def convert(md_path, docx_path, with_cover=True):
    lines = open(md_path, encoding="utf-8").read().split("\n")
    title = "面试手册"
    for line in lines[:6]:
        if line.startswith("# "):
            title = line[2:].strip()

    doc = Document()
    setup(doc, title)
    if with_cover:
        cover(doc, title,
              "\u6f58\u4fca\u6770 \u00b7 2026 \u5c4a \u00b7 \u8d35\u5dde\u5927\u5b66 \u8f6f\u4ef6\u5de5\u7a0b",
              ["\u4f9d\u636e\uff1a\u4e2a\u4eba\u7b80\u5386 + \u300c\u5473\u77e5 \u00b7 \u667a\u6167\u70b9\u9910\u300d\u9879\u76ee\u771f\u5b9e\u4ee3\u7801",
               "\u9002\u7528\uff1a\u540e\u7aef\u5f00\u53d1 / AI \u5e94\u7528\u5f00\u53d1 / Java \u5f00\u53d1 \u6821\u62db\u9762\u8bd5"])
        toc(doc, [l[3:].strip() for l in lines if l.startswith("## ")])
    else:
        tp = doc.add_paragraph()
        tp.paragraph_format.space_after = Pt(14)
        inline(tp, title, size=20, color=ACCENT, bold_all=True, font=HEAD)

    i, n = 0, len(lines)
    in_code, buf = False, []
    while i < n:
        raw = lines[i]
        s = raw.strip()

        if s.startswith("```"):
            if in_code:
                code_block(doc, buf)
                buf, in_code = [], False
            else:
                in_code = True
            i += 1
            continue
        if in_code:
            buf.append(raw.rstrip())
            i += 1
            continue
        if not s or re.fullmatch(r"-{3,}", s):
            i += 1
            continue
        if s.startswith("# "):
            i += 1
            continue
        if s.startswith("#### "):
            heading(doc, s[5:], 4)
            i += 1
            continue
        if s.startswith("### "):
            heading(doc, s[4:], 3)
            i += 1
            continue
        if s.startswith("## "):
            heading(doc, s[3:], 2)
            i += 1
            continue

        if s.startswith("|") and i + 1 < n and re.match(r"\|\s*[-: ]+\|", lines[i + 1].strip()):
            rows = [cells_of(s)]
            i += 2
            while i < n and lines[i].strip().startswith("|"):
                rows.append(cells_of(lines[i]))
                i += 1
            table(doc, rows)
            continue

        if s.startswith(">"):
            qs = []
            while i < n and lines[i].strip().startswith(">"):
                qs.append(lines[i].strip()[1:].strip())
                i += 1
            quote(doc, qs)
            continue

        m = re.match(r"^(\s*)- \[([ xX])\]\s+(.*)$", raw)
        if m:
            mark = "\u2611 " if m.group(2).lower() == "x" else "\u2610 "
            bullet(doc, mark + m.group(3).strip())
            i += 1
            continue

        m = re.match(r"^(\s*)[-*]\s+(.*)$", raw)
        if m:
            bullet(doc, m.group(2).strip(), 1 if len(m.group(1)) >= 2 else 0)
            i += 1
            continue

        m = re.match(r"^(\s*)(\d+)\.\s+(.*)$", raw)
        if m:
            bullet(doc, m.group(3).strip(), 1 if len(m.group(1)) >= 2 else 0,
                   label=m.group(2) + ".")
            i += 1
            continue

        body(doc, s)
        i += 1

    if in_code and buf:
        code_block(doc, buf)

    doc.save(docx_path)
    return docx_path


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if "--compact" in sys.argv:
        DENSE = True
    convert(args[0], args[1], with_cover="--no-cover" not in sys.argv)
    print("saved:", args[1])
