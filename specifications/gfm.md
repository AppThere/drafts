# GitHub Flavored Markdown

## Identity

| | |
|---|---|
| Spec name | GitHub Flavored Markdown Spec |
| Version | 0.29-gfm, dated 2019-04-06 |
| Canonical URL | <https://github.github.com/gfm/> |
| Base standard | CommonMark 0.29 |
| Current CommonMark | 0.31.2 (2024-01-28), <https://spec.commonmark.org/0.31.2/> |
| License | CC BY-SA 4.0 |
| Reference implementation | `cmark-gfm` (C), <https://github.com/github/cmark-gfm> |
| Conformance suite | ~650 examples embedded in `spec.txt` |

GFM is a **strict superset of CommonMark**. It adds five extensions and modifies nothing in the
base grammar. Anything that parses as CommonMark parses identically as GFM.

### Version drift — read this before you target "GFM"

The published GFM spec is pinned to CommonMark 0.29 and has not been revised since 2019.
Three things have moved since:

- **CommonMark advanced to 0.31.2.** Changes affecting parsers: Unicode symbols now count as
  punctuation for emphasis flanking; `<search>` added as a known HTML block element; `<source>`
  removed as an HTML block start condition; inline HTML comment rules relaxed to match the HTML
  spec. `cmark-gfm` tracks these ahead of the published GFM document.
- **github.com renders more than the spec describes.** Footnotes, alerts, `$`/`$$` math,
  Mermaid diagrams, auto-linked issue/commit/user references, and YAML front matter stripping
  are live on the site but absent from 0.29-gfm.
- **Sanitization is post-processing.** GFM produces HTML; github.com then filters it.
  Raw HTML that the spec says passes through will be stripped on the site.

For a word processor, target **CommonMark 0.31.2 + the five GFM extensions**, and treat
github.com's site-specific extras as a separate optional layer.

## Parsing model

Two passes, and the order is load-bearing:

1. **Block structure.** Consume the document line by line, maintaining a stack of open blocks.
   Each line first matches against open blocks (continuation), then tries to open new blocks,
   and remaining text becomes paragraph content. Inline content is accumulated as raw text.
2. **Inline parsing.** Walk the block tree; parse the raw text of each leaf block for emphasis,
   links, code spans, autolinks, and raw HTML.

Link reference definitions are collected during pass 1 and resolved during pass 2, which is
why a definition can appear *after* the reference that uses it.

### Block types

**Container blocks** (contain other blocks):
- Block quote — `>` marker, optionally followed by one space
- List — ordered (`1.` `1)`, start number 0–999999999) and bullet (`-` `+` `*`)
- List item

**Leaf blocks** (contain inline content or nothing):
- Thematic break — 3+ of `-`, `_`, or `*`, spaces allowed between
- ATX heading — 1–6 `#` then space; optional closing sequence
- Setext heading — paragraph text underlined by `=` (h1) or `-` (h2)
- Indented code block — 4 spaces; cannot interrupt a paragraph
- Fenced code block — 3+ backticks or tildes; info string; backtick fences cannot contain backticks in the info string
- HTML block — 7 start conditions with distinct end conditions
- Link reference definition
- Paragraph
- Blank line

## Precedence rules that trip up implementations

These are where naive parsers diverge from the spec. Each has explicit examples upstream.

**Tabs.** Tabs are not expanded to spaces globally. They advance to the next 4-column tab stop,
and only when the tab is being consumed for block structure indentation. A tab inside content
is preserved verbatim. Getting this wrong breaks list indentation in ways that are hard to trace.

**Lazy continuation.** A paragraph inside a block quote or list item continues across lines that
lack the block marker, but only if the line would be paragraph continuation text. A line that
would start a new block does not continue lazily.

**List tightness.** A list is *loose* if any constituent item is separated by a blank line, or if
any item directly contains two block-level children with a blank line between them. Loose list
items wrap their paragraphs in `<p>`; tight ones don't. Tightness is a property of the whole list,
determined after the list is closed.

**Emphasis and flanking.** The delimiter run rules (left-flanking, right-flanking, the "rule of
three" for `*`/`_` multiples-of-three restriction, and `_` requiring word boundaries) are the
single most intricate part of CommonMark. Use the reference algorithm — process delimiters with a
stack and `openers_bottom` table — rather than regex. Section 6.2 upstream.

**Link precedence.** Code spans, autolinks, and raw HTML bind more tightly than emphasis, which
binds more tightly than links. But link *brackets* take precedence over emphasis when they conflict.

**Link label normalization.** For matching reference links: strip leading/trailing whitespace,
collapse internal whitespace runs to a single space, and Unicode case-fold. Labels longer than
999 characters are invalid.

**HTML blocks.** Seven types, each with its own start and end condition. Type 7 (any complete
open or closing tag alone on a line) cannot interrupt a paragraph; types 1–6 can. Type 1 covers
`<pre>`, `<script>`, `<style>`, `<textarea>`.

**Backslash escapes** work only before ASCII punctuation, and not inside code spans, autolinks,
or raw HTML. **Entity references** are recognized (named per the HTML5 entity list, plus decimal
and hex numeric) everywhere except code spans and code blocks.

**Code spans.** Delimited by matched backtick runs of equal length. One leading and one trailing
space is stripped, but only if both are present and the content isn't all spaces. Line endings
inside become spaces.

## The five GFM extensions

### 1. Tables

Header row, delimiter row, zero or more data rows. Pipes delimit cells; leading and trailing pipes
are optional. The delimiter row's cells consist of hyphens with an optional leading and/or trailing
colon setting alignment (left / right / center).

Rules worth encoding as tests:
- The delimiter row must have exactly the same cell count as the header row, or the whole
  construct is not a table.
- Rows with fewer cells than the header are padded with empty cells; rows with more are truncated.
- `\|` escapes a pipe inside cell content — including inside code spans, where escaping otherwise
  wouldn't apply. This is a deliberate deviation from base CommonMark inline rules.
- The table ends at the first blank line or a line that begins a new block.
- Cell content is parsed as inlines; block-level content is not permitted in cells.

### 2. Task list items

A list item whose paragraph begins with `[ ]`, `[x]`, or `[X]` followed by whitespace.
Renders as a disabled checkbox input. Only valid as the first thing in a list item's
first paragraph.

### 3. Strikethrough

One or two tildes: `~text~` or `~~text~~`. Follows the same delimiter-run flanking logic as
emphasis. Renders `<del>`. Three or more tildes do not create strikethrough.

### 4. Autolinks (extension)

Bare URLs and email addresses recognized without `<>` delimiters. Recognized after whitespace or
one of `*_~(`. Valid schemes: `http://`, `https://`, `ftp://`, plus `www.` (which gets `http://`
prepended).

Trailing-punctuation trimming rules — these matter and are frequently missed:
- Trailing `?!.,:*_~` are excluded from the link.
- A trailing `)` is excluded only if unbalanced against `(` within the link.
- If the link ends with `&` followed by alphanumerics and `;`, that entity-like suffix is excluded.
- The domain must contain at least one `.`, must not end in `-` or `_`, and the last two
  `.`-separated segments must not contain `_`.

Email autolinks: alphanumerics plus `.-_+` before `@`, alphanumerics plus `-_` after, at least
one `.`, and trailing `-` or `_` invalidates the match.

### 5. Disallowed raw HTML

A filter applied after parsing. These tags are escaped rather than passed through:
`<title>`, `<textarea>`, `<style>`, `<xmp>`, `<iframe>`, `<noembed>`, `<noframes>`, `<script>`,
`<plaintext>`. Comparison is case-insensitive; both opening and closing forms are filtered.
This is a security measure, not a syntax rule.

## github.com extras (not in the 0.29 spec)

Implement separately and behind flags if you want github.com parity:

| Feature | Syntax |
|---|---|
| Footnotes | `[^label]` reference, `[^label]: text` definition |
| Alerts | `> [!NOTE]` / `TIP` / `IMPORTANT` / `WARNING` / `CAUTION` as first line of a blockquote |
| Math | `$inline$`, `$$block$$`, or a `math` fenced code block |
| Mermaid | `mermaid` fenced code block |
| Auto heading anchors | Lowercase, strip non-alphanumerics except hyphens, spaces to hyphens, dedupe with `-1`, `-2` |
| Reference autolinks | `#123`, `user#123`, `owner/repo#123`, `@user`, bare SHAs |
| Front matter | Leading `---` YAML block, rendered as a table on github.com |

## Rust implementation notes

| Crate | Notes |
|---|---|
| `pulldown-cmark` | Pull-parser, event stream, zero-copy. Fastest. CommonMark + tables, strikethrough, footnotes, task lists. No AST — you build your own. Best fit if you're streaming into a layout engine. |
| `comrak` | Port of `cmark-gfm`. Full GFM extension set, AST-based, supports round-tripping back to Markdown. Slower but the most spec-faithful. |
| `markdown-rs` | CommonMark + GFM, produces an AST with source positions. Good for editor tooling where you need to map nodes back to byte offsets. |

For an editor, source position mapping is the deciding factor — you need node→byte-range to
implement incremental reparse, syntax highlighting, and cursor-aware formatting. `pulldown-cmark`
gives you ranges on events; `markdown-rs` gives positions on AST nodes; `comrak` has source
positions behind an option.

**Round-tripping is the hard part** and no spec covers it. Markdown→AST→Markdown is not
canonical: `*x*` and `_x_` both produce the same emphasis node. If Loki edits Markdown in place,
either preserve the original source spans for untouched regions, or commit to a normalized
output style and accept that opening a file rewrites it.

## Conformance testing

`spec.txt` is both prose and test suite. Examples are fenced blocks of the form
`markdown source` / `.` / `expected HTML`. Extract with:

```sh
python3 test/spec_tests.py --dump-tests   # from commonmark/commonmark-spec
```

This yields JSON with `markdown`, `html`, `section`, and `number` fields — drop it straight into
a Rust test harness. Normalize whitespace in HTML comparison the way the reference harness does,
or you'll get spurious failures.

## Further reading

- CommonMark spec: <https://spec.commonmark.org/0.31.2/>
- GFM spec: <https://github.github.com/gfm/>
- Babelmark (compare implementations on a given input): <https://babelmark.github.io/>
- CommonMark discussion forum: <https://talk.commonmark.org/>
