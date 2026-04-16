#!/usr/bin/env python3
"""Parse db/seed_english.local.sql and emit a Kotlin BundledQuestion list.

SQL block shape (one per question):
    WITH q AS (INSERT INTO "Questions" ("Text", "Level", "Explanation", "CreatedAt")
               VALUES ('<text>', '<level>', '<explanation>', now()) RETURNING "Id")
    INSERT INTO "QuestionOptions" ("QuestionId", "Text", "IsCorrect")
    SELECT "Id", v.text, v.correct FROM q, (VALUES
        ('<opt>', true|false),
        ...
    ) AS v(text, correct);
"""
from __future__ import annotations

import io
import re
import sys
from pathlib import Path

# Windows defaults stdout to cp1252; force utf-8 so em-dashes and arrows survive.
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", newline="\n")


# Matches ONE SQL string literal:  '…' with '' as an escape for a single quote.
SQL_STRING = r"'((?:[^']|'')*)'"

QUESTION_RE = re.compile(
    r"INSERT INTO \"Questions\" \(\"Text\", \"Level\", \"Explanation\", \"CreatedAt\"\)\s*"
    r"VALUES \(" + SQL_STRING + r",\s*" + SQL_STRING + r",\s*(NULL|" + SQL_STRING + r"),\s*now\(\)\)",
    re.DOTALL,
)

OPTION_RE = re.compile(
    r"\(\s*" + SQL_STRING + r",\s*(true|false)\s*\)",
    re.DOTALL,
)


def unquote_sql(raw: str) -> str:
    return raw.replace("''", "'")


def kotlin_string(s: str) -> str:
    # Kotlin string literal: escape backslashes, double-quotes, dollar, and $ interpolation.
    escaped = (
        s.replace("\\", "\\\\")
         .replace("\"", "\\\"")
         .replace("$", "\\$")
         .replace("\r", "")
    )
    # Preserve newlines as \n inside single-line Kotlin strings.
    escaped = escaped.replace("\n", "\\n")
    return "\"" + escaped + "\""


def main() -> None:
    sql_path = Path(sys.argv[1])
    sql = sql_path.read_text(encoding="utf-8")

    # Walk the file block by block: find each question's INSERT, then grab its
    # options from the following (VALUES …) AS v(text, correct); segment.
    out = []
    qid = 1
    oid = 1

    cursor = 0
    while True:
        m = QUESTION_RE.search(sql, cursor)
        if not m:
            break
        text_raw, level_raw = m.group(1), m.group(2)
        # group(3) is "NULL" or the full quoted literal; group(4) is inner if quoted
        explanation_raw = m.group(4) if m.group(3) != "NULL" else None

        text = unquote_sql(text_raw)
        level = unquote_sql(level_raw)
        explanation = unquote_sql(explanation_raw) if explanation_raw is not None else None

        # Options block follows, terminated by `) AS v(text, correct);`
        opts_start = m.end()
        opts_end = sql.find(") AS v(text, correct);", opts_start)
        if opts_end < 0:
            raise SystemExit(f"Couldn't find options terminator for question {qid} ({text[:60]!r})")

        opts_slice = sql[opts_start:opts_end]
        options = []
        for om in OPTION_RE.finditer(opts_slice):
            opt_text = unquote_sql(om.group(1))
            opt_correct = om.group(2).lower() == "true"
            options.append((oid, opt_text, opt_correct))
            oid += 1

        if not options:
            raise SystemExit(f"No options parsed for question {qid}: {text[:80]!r}")

        out.append({
            "id": qid,
            "text": text,
            "level": level,
            "explanation": explanation,
            "options": options,
        })
        qid += 1
        cursor = opts_end + len(") AS v(text, correct);")

    # Emit Kotlin.
    print("// AUTO-GENERATED from db/seed_english.local.sql — do not edit by hand.")
    print("// Regenerate by running scripts/generate_bundled_questions.py or equivalent.")
    print("package com.quizmemo.offline")
    print()
    print("internal object BundledQuestions {")
    print("    val all: List<BundledQuestion> = listOf(")
    for q in out:
        print(f"        BundledQuestion(")
        print(f"            id = {q['id']},")
        print(f"            text = {kotlin_string(q['text'])},")
        print(f"            level = {kotlin_string(q['level'])},")
        if q['explanation'] is None:
            print(f"            explanation = null,")
        else:
            print(f"            explanation = {kotlin_string(q['explanation'])},")
        print(f"            options = listOf(")
        for (oid, ot, oc) in q['options']:
            print(f"                BundledOption({oid}, {kotlin_string(ot)}, {str(oc).lower()}),")
        print(f"            ),")
        print(f"        ),")
    print("    )")
    print("}")

    print(f"// {len(out)} questions parsed", file=sys.stderr)


if __name__ == "__main__":
    main()
