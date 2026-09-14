# Project Markdown Dialect

The normative definition of the Markdown flavour this project reads and writes.

## Decision

**Base:** CommonMark 0.31.2 (<https://spec.commonmark.org/0.31.2/>)

**Enabled extensions:**

| # | Extension | Syntax | Source of the rule |
|---|---|---|---|
| 1 | Tables | pipe tables with alignment row | GFM 0.29 §4.10 |
| 2 | Strikethrough | `~~text~~` (and `~text~`) | GFM 0.29 §6.5 |
| 3 | Linkify | bare URLs and `www.` hosts autolinked | GFM 0.29 §6.9 |
| 4 | Footnotes | `[^label]` ref, `[^label]: …` def | PHP Markdown Extra / GFM site behaviour |
| 5 | Definition lists | `Term` / `: Definition` | PHP Markdown Extra |
| 6 | Typographer | smart quotes, en/em dashes, ellipsis | Goldmark / SmartyPants |
| 7 | Attribute syntax | `{#id .class key="value"}` | Goldmark / kramdown / Pandoc |

**Explicitly NOT enabled:** task lists, math/passthrough, CJK line-break handling, wiki links,
highlight (`==`), admonitions, emoji shortcodes, front matter as a *Markdown* construct.

Front matter is handled outside the Markdown parser (see below), not as an extension.

## Rationale

This is a superset of GFM minus task lists, plus the three Goldmark defaults that GFM lacks
(footnotes as-specified, definition lists, typographer) plus attribute syntax.

The practical effect is **Hugo compatibility**. Hugo's default-on Goldmark extension set is
table, strikethrough, linkify, footnote, definitionList, typographer, plus `parser.attribute.title`.
A document authored in this dialect renders correctly in Hugo without configuration changes,
which was the original reason for targeting Hugo-style class modifiers.

Task lists are dropped because this is a prose and screenplay editor, not a note-taking app;
checkboxes are outside the scope and their absence removes a whole class of list-parsing
edge cases.

## Per-extension specification

### 1. Tables

Follow GFM exactly, including:
- Delimiter row cell count must equal header row cell count or the construct is not a table.
- Short rows pad with empty cells; long rows truncate.
- `\|` escapes a pipe **even inside code spans** — a deliberate deviation from CommonMark
  inline rules.
- Cells contain inline content only; no block content.

### 2. Strikethrough

Single and double tilde both produce strikethrough. Three or more tildes do not.
Delimiter runs follow the same left/right-flanking logic as emphasis.

**Conflict note:** if subscript (`~text~`) is ever added, single-tilde strikethrough must be
dropped. Pick one. This dialect picks strikethrough.

### 3. Linkify

- Recognised schemes: `http://`, `https://`, plus bare `www.` hosts.
- **Prepend `https` to bare `www.` hosts** (Goldmark's `linkifyProtocol` default), not `http`
  as GFM does. This is the one place the dialect deliberately diverges from GFM.
- `ftp://` and email autolinking follow GFM.
- Trailing-punctuation trimming rules per GFM §6.9 (trailing `?!.,:*_~` excluded; unbalanced
  `)` excluded; entity-like `&…;` suffix excluded).

### 4. Footnotes

```markdown
Text with a reference.[^1]

[^1]: The footnote body. May contain
    multiple paragraphs when indented.
```

- Labels are case-insensitive and normalised the same way link labels are.
- Definitions may appear anywhere in the document; they are collected in a first pass.
- Definition bodies may contain block content when continuation lines are indented.
- Ordering in output follows order of *first reference*, not order of definition.
- An unreferenced definition is not an error; it is simply not rendered.
- A reference with no definition renders as literal text.

Inline footnotes (`^[text]`) are **not** supported.

### 5. Definition lists

```markdown
Term
: Definition text

Another term
: First definition
: Second definition
```

- The term is a single line of inline content.
- A definition line starts with `:` followed by whitespace.
- One or more definitions may follow a term.
- A blank line between term and definition makes the list *loose* (definitions wrapped in `<p>`).
- Definitions may contain block content when continuation lines are indented to align.
- A `:` line with no preceding term line is a paragraph, not a definition.

This is the least standardised of the seven — PHP Markdown Extra is the reference, and
Goldmark's implementation is the behavioural target where Extra is ambiguous.

### 6. Typographer

Transforms applied to text content only. **Never inside** code spans, code blocks, autolinks,
raw HTML, or link destinations.

| Input | Output | Char |
|---|---|---|
| `'` opening | ‘ | U+2018 |
| `'` closing / apostrophe | ’ | U+2019 |
| `"` opening | “ | U+201C |
| `"` closing | ” | U+201D |
| `--` | – | U+2013 |
| `---` | — | U+2014 |
| `...` | … | U+2026 |
| `<<` | « | U+00AB |
| `>>` | » | U+00BB |

**Critical rule for this project: typographer is an output transform, not a storage transform.**

The source file keeps straight quotes and double hyphens unless the user typed the Unicode
character directly. Applying typographer on read and re-serialising would silently rewrite the
user's file on every save. In a WYSIWYG editor this matters more than in a static site generator —
see "WYSIWYG implications" below.

Quote direction is determined by adjacency: a quote preceded by whitespace or an opening
bracket opens; otherwise it closes. Apostrophes in contractions (`don't`, `'90s`) resolve to
the right single quote.

### 7. Attribute syntax

```markdown
## Heading text {#custom-id .highlight}

![Alt](/img.png "Title")
{.rounded .shadow width=400}
```

- Attribute blocks apply to **headings and images** (Goldmark's `parser.attribute.title: true`).
- Generic block attributes (`parser.attribute.block: true`) are **off** — an attribute block on
  an arbitrary paragraph is literal text.
- Syntax: `{` followed by space-separated items, `}` at end of line.
  - `#id` sets the element id
  - `.class` appends a class
  - `key=value` or `key="quoted value"` sets an arbitrary attribute
- The block must be the last thing on the heading line, or on the line immediately following
  a standalone image.
- Malformed attribute blocks are literal text, never a parse error.

Auto-generated heading IDs are on, using **GitHub's algorithm** (lowercase, strip punctuation
except hyphens, spaces to hyphens, Unicode preserved, duplicates suffixed `-1`, `-2`). An
explicit `{#id}` overrides the generated one.

## Handled outside the Markdown parser

| Layer | Handling |
|---|---|
| **Front matter** | TOML (`+++`), YAML (`---`), JSON (`{`). Stripped before parsing, preserved in its original format on save. Never normalised between formats. |
| **Hugo shortcodes** | `{{< … >}}` and `{{% … %}}` tokenised into opaque atomic spans before parsing, restored verbatim on serialise. Never parsed, never reformatted. |

The YAML front matter delimiter collides with CommonMark's thematic break. Stripping must
happen first or `---` parses as `<hr>`.

## Round-trip contract

Because this is an editor, not a renderer, serialisation fidelity is a first-class requirement:

1. **Untouched regions are byte-preserved.** Retain source spans for every node; re-emit the
   original bytes for any subtree the user did not edit.
2. **Typographer never round-trips.** Straight quotes in, straight quotes out.
3. **Emphasis delimiter choice is preserved.** `*x*` stays `*x*`; `_x_` stays `_x_`.
4. **Shortcodes and front matter are preserved verbatim**, including whitespace and key order.
5. **Unknown constructs pass through.** Anything the parser doesn't recognise is retained as
   literal text rather than dropped.

Only edited subtrees are re-serialised from the AST, using a documented canonical style
(ATX headings, `-` bullets, fenced code with backticks, reference-style links preserved as
found).

## WYSIWYG implications

The editor renders formatted text while storing Markdown. Three consequences:

- **Typographer as an input method, not a parse step.** Smart quotes should be applied as the
  user types (like a word processor's autocorrect), writing the Unicode character into the
  buffer. That way the file contains what's displayed and no transform is needed on save.
  Offer it as a toggle; some users want straight quotes in source.
- **Attribute blocks and footnote definitions need a visible affordance.** They are metadata,
  not prose. Consider rendering them as chips or a side panel rather than as literal text.
- **Incremental reparse is mandatory.** Full-document reparse on every keystroke will not hold
  up on a long manuscript. The parser must expose source offsets and support reparsing a dirty
  range.

## Implementation — Kotlin Multiplatform

**No available library covers all seven extensions.** The gaps are consistent: definition lists,
typographer, and attribute syntax are missing nearly everywhere. Options as of September 2026:

### Candidates

| Library | Artifact | Targets | Covers | Gaps |
|---|---|---|---|---|
| **JetBrains/markdown** (intellij-markdown) | `org.jetbrains:markdown` | JVM, Native (incl. iOS), JS, Wasm | CommonMark, tables, strikethrough, linkify | footnotes, definition lists, typographer, attributes |
| **KMP Markdown** (huarangmeng) | `io.github.huarangmeng:markdown-parser` | Android, iOS, JVM, JS, Wasm | CommonMark 0.31.2 (652/652 claimed), tables, strikethrough, linkify, footnotes, definition lists, block attributes | typographer |
| **commonmark-kotlin** (darriousliu) | `io.github.darriousliu:commonmark` | Android, iOS, JVM, Native, JS, Wasm | CommonMark, tables, strikethrough, autolink, footnotes, image attributes | definition lists, typographer, general attributes |

Ruled out as JVM-only: **flexmark-java** (which does support all seven, and is the reason this
extension set looks familiar), **commonmark-java**, **txtmark**.

Renderers rather than parsers, and therefore not load-bearing for an editor:
**mikepenz/multiplatform-markdown-renderer** (wraps JetBrains/markdown), **Orca**,
**compose-markdown**.

### Assessment

**JetBrains/markdown is the low-risk choice.** Pure Kotlin, all required targets, maintained by
JetBrains, and it is the engine behind IntelliJ's own Markdown editor — which means it is built
for exactly this use case: `ASTNode` carries `startOffset`/`endOffset`, and the flavour system
(`MarkdownFlavourDescriptor`, `MarkerBlockProvider`, sequential inline parsers) exists
specifically to be extended. Third parties have already extended it successfully; the Obsidian
flavour parser adds footnotes, highlights, and callouts on top of it.

Cost: four extensions to write. Footnotes and definition lists are block-level marker providers.
Attribute syntax is a line-suffix parser on headings plus a lookahead on standalone images.
Typographer is not a parser feature at all under this dialect — it's an input method.
Estimate: a few hundred lines each, plus conformance tests.

**KMP Markdown covers six of seven out of the box** and is the only library that natively does
definition lists and block attributes. It also advertises incremental parsing, source maps, and
streaming — all relevant. The caveat is maturity: ~50 stars, one maintainer, first released
recently, and the feature claims are self-reported with no independent conformance run. For an
app intended for public release, that's a real supply-chain consideration. Worth benchmarking
against the CommonMark suite yourself before committing; if it holds up, it saves months.

**commonmark-kotlin is the weakest option.** A single-maintainer machine-assisted transliteration
of commonmark-java (7 stars, 31 commits) that still leaves two of the seven extensions unbuilt.
The upstream it ports doesn't have definition lists or typographer either, so the gap won't close
by tracking upstream.

### Recommendation

Build on **JetBrains/markdown**, with the four missing pieces as your own extensions in a
`:markdown-dialect` module. Reasons specific to this project:

1. It's the only option with institutional backing and a multi-year track record.
2. Editor-grade source offsets are already there, which is the hard part.
3. Writing the extensions yourself means the definition-list and attribute semantics match this
   spec exactly, rather than matching some other project's interpretation.
4. You need a hand-written Fountain parser regardless — one that shares the AST and
   source-mapping conventions. Two parsers with one architecture is simpler than two parsers with
   two architectures plus a third-party black box.

Evaluate KMP Markdown in parallel: run the CommonMark 0.31.2 suite and your own definition-list
and attribute fixtures against it. If it passes cleanly it's a legitimate shortcut, and the
extensions you'd have written for JetBrains/markdown become a fallback rather than wasted work.

### Conformance testing

```sh
curl -o spec-0.31.2.txt https://spec.commonmark.org/0.31.2/spec.txt
# extract to JSON with commonmark-spec's test/spec_tests.py --dump-tests
```

Drive the 652 CommonMark examples from `commonTest` so every target is verified, not just JVM.
Write your own fixture set for the four extensions this dialect adds beyond GFM — there is no
published conformance suite for definition lists or attribute syntax.

## Further reading

- CommonMark 0.31.2: <https://spec.commonmark.org/0.31.2/>
- GFM (tables, strikethrough, linkify): <https://github.github.com/gfm/>
- PHP Markdown Extra (footnotes, definition lists): <https://michelf.ca/projects/php-markdown/extra/>
- Goldmark (typographer, attributes — behavioural reference): <https://github.com/yuin/goldmark>
- JetBrains/markdown: <https://github.com/JetBrains/markdown>
- KMP Markdown: <https://github.com/huarangmeng/Markdown>
