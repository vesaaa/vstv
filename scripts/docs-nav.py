#!/usr/bin/env python3
"""按 docs/README.md 的目录，给每篇帮助生成顶部的目录树和底部的上一篇 / 下一篇。

改了目录（加篇、改名、调顺序）后运行一次：python3 scripts/docs-nav.py
"""
import re
from pathlib import Path

DOCS = Path(__file__).resolve().parent.parent / "docs"
TOP = ("<!-- nav-top -->", "<!-- /nav-top -->")
BOTTOM = ("<!-- nav-bottom -->", "<!-- /nav-bottom -->")


def read_index():
    """返回 [(分节标题, [(标题, 文件名), ...]), ...]"""
    sections, current = [], None
    for line in (DOCS / "README.md").read_text(encoding="utf-8").splitlines():
        if line.startswith("## "):
            current = (line[3:].strip(), [])
            sections.append(current)
        m = re.match(r"\d+\.\s+\[([^\]]+)\]\(([^)#]+\.md)\)", line)
        if m and current:
            current[1].append((m.group(1), m.group(2)))
    return sections


def replace_block(text, markers, block, at_top):
    start, end = markers
    pattern = re.compile(re.escape(start) + r".*?" + re.escape(end) + r"\n?", re.S)
    if pattern.search(text):
        return pattern.sub(lambda _: block, text, count=1)
    if at_top:
        # 放在一级标题下面
        lines = text.split("\n", 1)
        return lines[0] + "\n\n" + block + (lines[1].lstrip("\n") if len(lines) > 1 else "")
    return text.rstrip("\n") + "\n\n" + block


def main():
    sections = read_index()
    pages = [p for _, items in sections for p in items]
    for i, (title, name) in enumerate(pages):
        path = DOCS / name
        text = path.read_text(encoding="utf-8")
        text = text.replace("[返回目录](README.md)\n\n", "")

        tree = []
        for sec, items in sections:
            tree.append(f"- {sec}")
            for t, n in items:
                tree.append(f"  - **{t}**（当前）" if n == name else f"  - [{t}]({n})")
        top = (
            f"{TOP[0]}\n<details>\n<summary>帮助目录</summary>\n\n"
            + "\n".join(tree)
            + f"\n\n</details>\n{TOP[1]}\n\n"
        )

        nav = []
        if i > 0:
            nav.append(f"上一篇：[{pages[i - 1][0]}]({pages[i - 1][1]})")
        nav.append("[帮助目录](README.md)")
        if i < len(pages) - 1:
            nav.append(f"下一篇：[{pages[i + 1][0]}]({pages[i + 1][1]})")
        bottom = f"{BOTTOM[0]}\n---\n\n{' · '.join(nav)}\n{BOTTOM[1]}\n"

        text = replace_block(text, TOP, top, at_top=True)
        text = replace_block(text, BOTTOM, bottom, at_top=False)
        text = re.sub(r"\n{3,}", "\n\n", text)
        path.write_text(text, encoding="utf-8")
    print(f"{len(pages)} 篇已更新")


if __name__ == "__main__":
    main()
