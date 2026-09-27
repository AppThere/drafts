# Export Pipeline

How Markdown and Fountain source become XHTML/EPUB, ODT, and DOCX.

## Scope: ODT and DOCX are write-only

The editor reads and writes **Markdown and Fountain only**. ODT and DOCX are one-way export
targets — handoff artifacts for opening in LibreOffice or Word to do PDF export, print layout,
and formatting work beyond this app's remit. Nothing ever comes back.

This removes most of the difficulty. Not needed, at all:

- OPC/ODF package *reading*
- The DOCX style resolution cascade (docDefaults → table → numbering → paragraph → character →
  direct), which is the single largest correctness task in a DOCX reader
- MCE (`mc:AlternateContent`) preprocessing
- Strict vs Transitional *detection*; VML; legacy `.doc`-era compatibility markup
- Field *evaluation*, revision tracking, content controls, `w:sdt`
- Unit *parsing* (you only ever convert outward)
- Style-name recognition for re-import

What it changes about the design:

- **Style names should be the ones Word and LibreOffice already know**, not names of your
  own invention. See below — this is the difference between a document that behaves natively
  in the target app and one that merely looks right.
- **The templates are the deliverable**, more than the XML is. A `.dotx` and `.ott` carrying
  well-designed styles is what makes the handoff worth doing.
- **Unmapped constructs can degrade to something restyleable** rather than being dropped. The
  user is on their way into a more capable editor; leaving them a hook to grab is better than
  leaving them nothing.
- **Generated content can be emitted as unevaluated fields.** The receiving app computes it.

## The core decision: one IR, N backends

Do **not** write exporters against the parser's syntax tree. Two input syntaxes times four
output formats is eight pairings, each of which would independently re-solve list nesting,
whitespace encoding, and style resolution.

```
Markdown source ──► intellij-markdown ──► CST ──┐
                                                 ├──► Document IR ──┬──► XHTML / EPUB 3
Fountain source ──► hand-written parser ────────┘                   ├──► ODT (flat + packaged)
                                                                     ├──► DOCX
                                                                     └──► Markdown / Fountain
                                                                          (round-trip serialise)
```

The IR is the contract. Backends never see Markdown or Fountain concepts; parsers never see
output concepts.

## Can intellij-markdown do this?

Yes. `MarkdownParser(flavour).buildMarkdownTreeFromString(src)` returns an `ASTNode` tree and
that is the whole output of the library. `HtmlGenerator` is a separate class you simply don't
call.

Two things to know about the tree:

**It is a concrete syntax tree, not an abstract one.** Nodes span the full source range including
delimiters, so an `EMPH` element contains `EMPH` marker tokens for the `*` characters, plus
`TEXT`, `WHITE_SPACE`, and `EOL` tokens. This is exactly what you want for an editor — every byte
is accounted for, and source offsets are exact — but a backend walking it directly would have to
filter marker tokens at every node type. The IR lowering step is where that filtering happens,
once.

**`HtmlGenerator` is extensible but not a general-purpose backend.** It dispatches to a
`GeneratingProvider` per node type, which the flavour descriptor supplies via
`createHtmlGeneratingProviders`. You could substitute providers that emit ODF or
WordprocessingML, but the interface is string-append oriented and assumes an HTML-shaped output
model. Don't fight it. Lower to the IR and write real backends.

## Document IR

Enough shape to pin down the design; not a final API.

**Offsets are UTF-16 code units, not bytes, and the range is half-open.** Both were wrong in an
earlier draft of this document. UTF-16 is what intellij-markdown reports for AST node ranges and
what Compose reports for selection, so an offset crosses the parser/editor boundary without
conversion — on a path that runs on every keystroke, over documents that are not all ASCII.
`IntRange` is closed at both ends, which leaves two adjacent blocks both claiming the offset
between them; that offset is exactly where the caret sits when someone presses Home. `:core-model`
uses `SourceSpan(start, endExclusive)` with three distinct offset types, and this sketch now
follows it rather than the other way round. (Corrected 2026-09-27.)

```kotlin
sealed interface Block {
    val role: BlockRole          // open set — see below
    val attrs: Attributes        // id, classes, key-values
    val source: SourceSpan?     // half-open UTF-16 code-unit range, null if synthesised
}

data class Paragraph(val inlines: List<Inline>, ...) : Block
data class Heading(val level: Int, val inlines: List<Inline>, ...) : Block
data class ListBlock(val ordered: Boolean, val start: Int, val tight: Boolean,
                     val items: List<List<Block>>, ...) : Block
data class DefinitionList(val entries: List<DefEntry>, ...) : Block
data class CodeBlock(val language: String?, val text: String, ...) : Block
data class BlockQuote(val children: List<Block>, ...) : Block
data class Table(val alignments: List<Align>, val rows: List<List<List<Inline>>>, ...) : Block
data class ThematicBreak(...) : Block
data class Figure(val image: Image, val caption: List<Inline>?, ...) : Block
data class RawPassthrough(val text: String, val origin: Origin, ...) : Block   // shortcodes

sealed interface Inline
data class Text(val value: String) : Inline
data class Emphasis(val strong: Boolean, val children: List<Inline>) : Inline
data class Strikethrough(val children: List<Inline>) : Inline
data class CodeSpan(val text: String) : Inline
data class Link(val href: String, val title: String?, val children: List<Inline>) : Inline
data class Image(val src: String, val alt: String, val title: String?, val attrs: Attributes) : Inline
data class FootnoteRef(val label: String) : Inline
data class LineBreak(val hard: Boolean) : Inline

data class Document(
    val blocks: List<Block>,
    val footnotes: Map<String, List<Block>>,
    val frontMatter: FrontMatter?,   // preserved verbatim, format-tagged
    val metadata: DocMetadata,       // title, author, language — for EPUB/ODT/DOCX headers
)
```

### `BlockRole` is the join between Markdown and Fountain

```kotlin
enum class BlockRole {
    // shared
    BODY, HEADING, QUOTE, CODE, LIST_ITEM, DEFINITION_TERM, DEFINITION_BODY,
    // Fountain
    SCENE_HEADING, ACTION, CHARACTER, DIALOGUE, PARENTHETICAL, DUAL_DIALOGUE_LEFT,
    DUAL_DIALOGUE_RIGHT, TRANSITION, CENTERED, LYRIC, PAGE_BREAK,
    // Fountain, non-rendering
    SECTION, SYNOPSIS, NOTE,
}
```

This is the piece that makes one IR serve both modes. A Fountain document is, structurally,
a flat sequence of paragraphs each carrying a role. Backends map role → named style.

**Consequence worth noticing: Fountain → ODT/DOCX is easier than Markdown → ODT/DOCX.**
Screenplay formatting is *entirely* paragraph styles and indentation — no lists, no tables, no
nesting, no inline structure beyond four emphasis types. Once you have a working DOCX backend for
Markdown, the Fountain path is a style table and little else.

## Feature → format mapping

This is where the extension set from `markdown-dialect.md` meets reality.

| Feature | XHTML / EPUB 3 | ODT | DOCX |
|---|---|---|---|
| Paragraph | `<p>` | `text:p` | `w:p` |
| Heading | `<h1>`–`<h6>` | `text:h` + `text:outline-level` | `w:p` + `Heading N` style + `w:outlineLvl` |
| Emphasis / strong | `<em>` / `<strong>` | `text:span` → `fo:font-style` / `fo:font-weight` | `w:rPr` → `w:i` / `w:b` |
| Strikethrough | `<s>` | `style:text-line-through-style` | `w:strike` |
| Code span / block | `<code>`, `<pre><code>` | `text:span` + `text:p` w/ mono styles | runs + paragraph style |
| Link | `<a href>` | `text:a xlink:href` | `w:hyperlink` + relationship |
| Image | `<img/>` (self-closed) | `draw:frame` + `draw:image` | `w:drawing` + `wp:inline` (EMU) |
| List | `<ul>` / `<ol>` | `text:list` + list style | `w:numPr` + `numbering.xml` |
| Table | `<table>` | `table:table` | `w:tbl` |
| **Footnotes** | `<aside epub:type="footnote">` + `epub:type="noteref"` | `text:note` | `footnotes.xml` + `w:footnoteReference` |
| **Definition lists** | `<dl>`/`<dt>`/`<dd>` — native | **no native construct** | **no native construct** |
| **Typographer** | Unicode chars — free | free | free |
| **`{#id}`** | `id` attribute | `text:bookmark` | `w:bookmarkStart`/`End` |
| **`{.class}`** | `class` attribute | **needs class→style map** | **needs class→style map** |

### The two awkward cases

Both are handled with named styles. Because export is one-way, the style names don't have to be
machine-recoverable — they have to be *useful to a person in Word or LibreOffice*.

**Definition lists.** Neither ODF nor WordprocessingML has a `<dl>` equivalent; both office
suites fake it with paragraph styles. Use the names each application already ships, so the styles
appear in the user's style gallery rather than as strangers:

| IR role | ODT style | DOCX style |
|---|---|---|
| `DEFINITION_TERM` | `Definition_20_Term` (displays as "Definition Term") | `DefinitionTerm` |
| `DEFINITION_BODY` | `Definition_20_List` (displays as "Definition List") | `Definition` |

LibreOffice ships both ODT styles natively. Word has no built-in equivalent, so define them in
the `.dotx` with sensible indentation and a `w:next` chain (Term → Definition).

**`{.class}` attributes.** A CSS class has no meaning in an office format. Provide an explicit
mapping table from class name to a named character or paragraph style:

```toml
[export.styles]
"highlight"  = { docx = "Highlight",  odt = "Highlight" }
"lead"       = { docx = "Lead",       odt = "Lead_20_Paragraph" }
"small-caps" = { docx = "SmallCaps",  odt = "Small_20_Caps" }
```

For **unmapped** classes, don't drop the content — emit a character style named after the class
itself, defined with no formatting. The text survives, the semantic grouping survives, and the
user can select the style in Word and give it an appearance in one action. That is exactly the
kind of task they opened Word to do. Log it rather than raising it in the UI; a silent-but-
recoverable degradation is the right severity for a one-way export.

## Backend: XHTML for EPUB 3

**Do not use `HtmlGenerator` for this.** It emits HTML fragments — unclosed void elements,
HTML-style escaping — and EPUB content documents must be well-formed XML. One unclosed `<img>`
fails EPUBCheck and the file won't load in strict readers.

Requirements, per `epub3.md`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"
      xmlns:epub="http://www.idpf.org/2007/ops"
      xml:lang="en" lang="en">
```

- Every void element self-closed: `<br/>`, `<img/>`, `<hr/>`.
- XML escaping for `&`, `<`, `>` — including inside attribute values.
- Serialise through a real XML writer. Never string-template.

**Footnotes get the EPUB treatment**, not the HTML one — this is what gives you popup footnotes
in real readers:

```xml
<a epub:type="noteref" href="#fn1" id="fnref1" role="doc-noteref">1</a>
...
<aside epub:type="footnote" id="fn1" role="doc-footnote">
  <p>Footnote body. <a href="#fnref1" role="doc-backlink">↩</a></p>
</aside>
```

**Structural semantics.** Map headings and document divisions to `epub:type` plus DPUB-ARIA
`role`: `chapter`, `part`, `titlepage`, `frontmatter`, `bodymatter`, `backmatter`.

**Raw HTML in Markdown source is a hazard.** CommonMark passes it through; EPUB requires
well-formedness. On export, either parse-and-reserialise it as XML, or escape it as literal text.
Silently passing it through will produce invalid EPUBs from valid Markdown.

**Fountain → EPUB** follows the constraints already settled for this project: percentage-based
indentation rather than fixed measurements, a US Letter body-width ceiling, and no pagination.
That means the screenplay CSS is a set of `margin-left`/`width` percentages keyed off
`BlockRole`, with a `max-width` on the body container — reflowable, but never wider than a
printed page. Dual dialogue is the one layout that needs real care; a two-column flex or table
layout degrades acceptably on narrow screens if you let it stack.

## Backend: ODT

**Generate flat ODF (`.fodt`) first, then package.** One XML document, no ZIP, and your test
fixtures become diffable. Packaging to `.odt` afterwards is mechanical: split into
`content.xml` / `styles.xml` / `meta.xml`, write `META-INF/manifest.xml`, and ZIP with
`mimetype` stored first and uncompressed.

Critical rules from `odf-text.md`:

- **Whitespace must be encoded.** Two or more spaces become one space plus
  `<text:s text:c="n"/>`. Tabs are `<text:tab/>`. Line breaks are `<text:line-break/>`.
  Emitting raw runs of spaces produces documents that render wrong everywhere.
- **Automatic vs common styles.** Named styles (`Heading 1`, `Quotation`) go in `styles.xml`.
  Anything generated per-instance goes in `<office:automatic-styles>` as `P1`, `T1`, etc.
  Your IR's `attrs` → automatic styles; your `BlockRole` → common styles.
- Screenplay roles become common paragraph styles with `fo:margin-left` and `fo:margin-right`,
  a `style:next-style-name` chain (Character → Dialogue → Action), and `fo:keep-with-next` on
  Character and Scene Heading.

## Backend: DOCX

Build the OPC *writer* first — `[Content_Types].xml`, `_rels/.rels`, and relationship emission.
Writing relationships is bookkeeping; the resolver a reader would need doesn't exist here.

From `ooxml-docx.md`, the writing rules that will bite:

- **`xml:space="preserve"` on every `w:t`.** Unconditionally. Cheaper than reasoning about it.
- **`w:pPr` must be the first child of `w:p`; `w:rPr` first child of `w:r`.** Schema requirement,
  not convention. Word rejects violations.
- **Units.** Twips for indents and margins, half-points for font sizes, EMU for image extents.
  One conversion module, tested in isolation. Outward conversion only.
- **Every `w:tc` needs at least one `w:p`**, even when empty.
- **List numbering is computed, not stored.** Emit `numbering.xml` definitions and reference them
  with `w:numPr`; don't write literal numbers into the text.
- Write **Transitional**. Strict has patchier support in older tools and buys you nothing here.

### Use built-in style identifiers

This is the most consequential decision in the DOCX backend, and it only matters because the
target is handoff.

Word treats certain `w:styleId` values specially: `Heading1` through `Heading9`, `Title`,
`Subtitle`, `Quote`, `IntenseQuote`, `ListParagraph`, `Caption`, `FootnoteText`. Use them, paired
with `<w:outlineLvl>` on the paragraph properties, and the user gets — for free — a working
Navigation pane, outline view, automatic table of contents, and **PDF bookmarks on export**.

Invent your own names instead and all of that silently stops working. The document will look
correct and behave inertly, which is the worst failure mode for a handoff format: the user won't
know what they're missing.

The same logic applies to ODT: `Heading_20_1` with `text:outline-level`, not a bespoke name.

### Emit fields, don't evaluate them

You have no pagination and don't need any. The receiving app does. So emit generated content as
*unevaluated* fields with an empty cached result, and let Word compute it:

```xml
<w:p>
  <w:r><w:fldChar w:fldCharType="begin"/></w:r>
  <w:r><w:instrText xml:space="preserve"> TOC \o "1-3" \h \z \u </w:instrText></w:r>
  <w:r><w:fldChar w:fldCharType="separate"/></w:r>
  <w:r><w:t>Right-click and choose Update Field.</w:t></w:r>
  <w:r><w:fldChar w:fldCharType="end"/></w:r>
</w:p>
```

Same for `PAGE` and `NUMPAGES` in headers and footers. Set
`<w:updateFields w:val="true"/>` in `settings.xml` and Word offers to refresh on open.

The ODT equivalent is `text:table-of-content` with a `text:index-body` placeholder, plus
`text:page-number` / `text:page-count` fields.

### Ship a template

Generate `document.xml` and `numbering.xml`; take `styles.xml`, `theme1.xml`, and page setup from
a `.dotx` you author by hand in Word. It produces cleaner output than synthesising style
definitions, gives you a place to tune the definition-list and screenplay styles visually, and
lets users swap in their own template. For a handoff format this is the highest-value part of the
work — more than the XML generation itself.

## Round-trip serialisers

The only formats with a *read* path are Markdown and Fountain, and they're the reason the IR
carries `source: SourceSpan?`. ODT and DOCX have no serialiser counterpart here.

Only re-serialise subtrees the user actually edited; emit the original text for everything else. This
is what preserves emphasis delimiter choice, shortcode formatting, and front matter key order,
per the round-trip contract in `markdown-dialect.md`. Fountain is easier here — it's its own
canonical serialisation, so byte-preservation gives you perfect fidelity almost for free.

## Module layout

These are the names in `appthere-drafts.md` §3, which is the one list the build follows. This
document used to give the same modules different names — `:document-ir`, `:markdown-parser-dialect`,
`:export-xhtml` and so on — and the disagreement was silent, because nothing reads a module name out
of a specification. §3 wins for a concrete reason: `engineering-conventions.md` §2 already cites
`:core-export-ooxml/**/StyleMap.kt` by path in its file-length exemption list, so §3's naming is
load-bearing in a third document. (Corrected 2026-09-27.)

```
:core-parse-markdown         intellij-markdown + the four custom extensions
:core-parse-fountain         hand-written, ~500 lines
:core-model                  the IR, plus lowering from both parsers
:core-export-container       ZIP + XML writing, shared by EPUB / ODT / DOCX  (write-only)
:core-export-xhtml           XHTML + EPUB 3 packaging
:core-export-odf             FODT + ODT
:core-export-ooxml           DOCX
:core-serialise              IR → Markdown / Fountain, source-preserving
```

Two notes on that list.

`:core-export-container` was `:export-package` here and `:core-export-package` in the build until
the Android resource compiler refused it: `package` is a Java keyword, so the namespace the module
name implies is not a legal package and the generated R file will not compile. *Container* is also
the better word — EPUB calls this an OCF container and ODF and OOXML call it OPC, the Open
Packaging *Conventions*. Renamed 2026-09-27.

`:core-serialise` is **one** module here and was two — `:serialise-markdown` and
`:serialise-fountain`. Whether to split it is genuinely open and is deliberately not being settled
now. The question is whether the two serialisers share the round-trip fidelity machinery the IR
carries (`Emphasis.delimiter`, `CodeBlock.fence`, `Heading.style`, `ListBlock.marker`): if they do,
one module is right; if they turn out to share nothing, splitting later is cheap. Revisit at
Phase 10, when both serialisers exist and the answer is a fact rather than a guess.

All pure Kotlin in `commonMain` — no platform-specific code is needed for any of it. ZIP is the
only dependency worth care; `korlibs-compression` or `kotlinx-io` based approaches work across
targets, and you need stored-not-deflated support for the `mimetype` entry in both EPUB and ODT.

Templates (`.dotx`, `.ott`) ship as resources in `:core-export-ooxml` and `:core-export-odf`.

## Validation

EPUB has EPUBCheck. ODT and DOCX have no equivalent authority, and export-only means a subtly
malformed file can't be caught by reading it back — nothing reads it back. Test the way users
will actually consume the output:

```sh
soffice --headless --convert-to pdf out.docx
soffice --headless --convert-to pdf out.odt
```

Run this in CI over a fixture corpus. It catches structural invalidity cheaply, and it exercises
precisely the path users take. Supplement with manual checks that don't automate well:

- Do the styles appear in Word's and LibreOffice's style gallery under the expected names?
- Does Insert → Table of Contents populate?
- Does PDF export produce a navigable bookmark tree from the headings?
- Do screenplay paragraphs hold their indentation and keep-with-next behaviour across a page break?

## Build order

1. IR + Markdown lowering + Markdown serialiser. Round-trip tests pass before anything exports.
2. XHTML backend. Smallest, and it's the substrate for EPUB.
3. EPUB packaging. Validate with EPUBCheck in CI.
4. Fountain parser + lowering. Reuses everything above.
5. FODT. Single-file XML, easy to eyeball, and LibreOffice opens it directly — so this is the
   first point at which the handoff story actually works end to end.
6. ODT packaging.
7. DOCX. Largest of the three, and benefits from every lesson learned in 5–6.

Steps 5–7 are now substantially smaller than they were when import was in scope. DOCX in
particular drops from a long-tail fidelity programme to a bounded generation task.
