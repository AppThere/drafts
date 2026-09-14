# Hugo Markdown

## Identity

| | |
|---|---|
| Formal specification | **None.** Behaviour is implementation-defined. |
| Normative implementation | Goldmark, <https://github.com/yuin/goldmark> (MIT) |
| Base standard | CommonMark (Goldmark is CommonMark-compliant) |
| Config reference | <https://gohugo.io/configuration/markup/> |
| Hugo extensions repo | <https://github.com/gohugoio/hugo-goldmark-extensions> |
| Docs license | CC BY-NC-SA 4.0 |

There is no "Hugo Markdown specification" to fetch. Hugo's content format is CommonMark as
implemented by Goldmark, plus a configurable extension set, plus three Hugo-specific layers that
sit *outside* the Markdown grammar entirely: front matter, shortcodes, and render hooks.

Goldmark replaced Blackfriday as the default in Hugo v0.60.0 (2019). Blackfriday was removed
entirely in later versions. Anything describing Hugo Markdown as Blackfriday-based is obsolete.

## The three layers

Anyone writing a parser for Hugo content needs to handle these as distinct passes:

```
┌─────────────────────────────────────────┐
│ 1. Front matter       (pre-Markdown)    │  TOML / YAML / JSON, stripped before parsing
├─────────────────────────────────────────┤
│ 2. Shortcodes         (pre-Markdown)    │  {{< >}} extracted and placeholder-substituted
├─────────────────────────────────────────┤
│ 3. Goldmark           (CommonMark+ext)  │  the actual Markdown parse
├─────────────────────────────────────────┤
│ 4. Render hooks       (post-AST)        │  template overrides per node type
└─────────────────────────────────────────┘
```

Hugo extracts shortcodes *before* handing text to Goldmark, replacing them with placeholder
tokens, then substitutes rendered output back afterwards. This is why shortcode content doesn't
interact with Markdown block structure the way inline HTML does. If you're building an editor,
mirror this: treat shortcodes as opaque atomic spans.

## Layer 1 — Front matter

Delimiters determine the format. Must be the very first thing in the file.

| Format | Opening | Closing |
|---|---|---|
| TOML | `+++` | `+++` |
| YAML | `---` | `---` |
| JSON | `{` | `}` (braces are the delimiters; no fence) |

The YAML form collides with CommonMark's thematic break and setext heading underline. A parser
that doesn't strip front matter first will read `---` as a thematic break and the following
key-value lines as a paragraph.

Common keys: `title`, `date`, `lastmod`, `publishDate`, `expiryDate`, `draft`, `weight`,
`slug`, `url`, `aliases`, `tags`, `categories`, `series`, `layout`, `type`, `summary`,
`description`, `keywords`, `cascade`, `outputs`, `resources`, `params`, `build`,
`markup`, `headless`, `translationKey`, `linkTitle`, `menus`.

## Layer 2 — Shortcodes

Two forms, and the distinction matters:

| Form | Behaviour |
|---|---|
| `{{< name >}}` | Output inserted **without** Markdown processing |
| `{{% name %}}` | Inner content **is** processed as Markdown |

Both support paired form with a closing tag: `{{< name >}}...{{< /name >}}`.

Arguments are positional or named:
```
{{< figure src="/img/x.jpg" title="Caption" >}}
{{< youtube w7Ft2ymGmfc >}}
```

Built-in shortcodes: `figure`, `highlight`, `instagram`, `param`, `ref`, `relref`, `vimeo`,
`youtube`, `gist` (removed in newer versions), `qr`, `comment`, `details`.

`{{< ref "page.md" >}}` and `{{< relref >}}` resolve to absolute/relative permalinks and fail
the build on a broken link — this is Hugo's link integrity mechanism and has no Markdown analogue.

Whitespace-trimming variants `{{<-` and `->}}` exist for controlling surrounding whitespace.

## Layer 3 — Goldmark configuration

Default configuration (`markup.goldmark`), from Hugo's `goldmark_config` package:

```yaml
markup:
  goldmark:
    extensions:
      typographer:
        disable: false
        leftSingleQuote: "‘"
        rightSingleQuote: "’"
        leftDoubleQuote: "“"
        rightDoubleQuote: "”"
        enDash: "–"
        emDash: "—"
        ellipsis: "…"
        leftAngleQuote: "«"
        rightAngleQuote: "»"
        apostrophe: "’"
      footnote: true
      definitionList: true
      table: true            # GitHub-flavored
      strikethrough: true    # GitHub-flavored
      linkify: true          # GitHub-flavored (bare URL autolinking)
      linkifyProtocol: https
      taskList: true         # GitHub-flavored
      cjk:
        enable: false
        eastAsianLineBreaks: false
        eastAsianLineBreaksStyle: simple
        escapedSpace: false
      passthrough:
        enable: false
        delimiters:
          inline: []
          block: []
    parser:
      autoHeadingID: true
      autoHeadingIDType: github      # github | github-ascii | blackfriday
      wrapStandAloneImageWithinParagraph: true
      attribute:
        title: true
        block: false
    renderer:
      unsafe: false
```

### Extension notes

**`typographer`** — smart quotes and dashes. Note this is a *rendering* transform, not a parse
transform; the source characters are unchanged. If you're round-tripping, don't apply it.
Configurable per-character, which matters for non-English locales.

**`footnote`** — `[^1]` reference / `[^1]: text` definition. Same syntax as GFM's footnotes.

**`definitionList`** — PHP Markdown Extra style:
```
Term
: Definition
```
Not part of CommonMark or GFM. A Hugo-specific expectation.

**`linkify`** — bare URL autolinking. `linkifyProtocol` sets the scheme prepended to bare
`www.`-style hosts. Defaults to `https` (GFM defaults to `http`).

**`passthrough`** — preserves raw content within delimiter pairs, bypassing Markdown inline
parsing. Built for LaTeX. Without it, `$a^*=x-b^*$` has its asterisks consumed as emphasis.
Default delimiter pairs when enabled: `$...$` and `\(...\)` inline, `$$...$$` and `\[...\]`
block. Configurable to avoid dollar-sign collisions with currency.

**`cjk`** — East Asian line break handling. `eastAsianLineBreaks` suppresses the space that
would otherwise be inserted at a soft break between CJK characters. Styles: `simple` and
`css3draft`.

**`renderer.unsafe`** — when `false` (the default), raw inline HTML in content is **stripped**,
replaced with an HTML comment. This is Hugo's single most common source of "my HTML disappeared"
reports. CommonMark says pass it through; Hugo says no unless you opt in.

### Parser notes

**`autoHeadingID`** — generates anchor IDs from heading text. Three algorithms:
- `github` — GitHub's rule: lowercase, strip punctuation, spaces to hyphens, Unicode preserved
- `github-ascii` — same but transliterates/strips non-ASCII
- `blackfriday` — legacy compatibility

**`attribute`** — Markdown attribute syntax, a Goldmark extension not in CommonMark:
```markdown
## Heading {#custom-id .class key="value"}

![Alt](/img.png "Title")
{.rounded .shadow}
```
`title: true` enables attributes on headings/images; `block: true` enables them on block
elements generally (default off).

**`wrapStandAloneImageWithinParagraph`** — when `false`, an image alone in a paragraph is
emitted without the wrapping `<p>`. Useful for figure styling.

## Layer 4 — Render hooks

Templates that override rendering of specific AST node types. Placed in
`layouts/_markup/` (older: `layouts/_default/_markup/`):

| Hook file | Overrides |
|---|---|
| `render-link.html` | Inline links |
| `render-image.html` | Images |
| `render-heading.html` | Headings |
| `render-codeblock.html` | Fenced code blocks (can dispatch by language) |
| `render-blockquote.html` | Blockquotes (this is how GitHub-style alerts are implemented) |
| `render-table.html` | Tables |
| `render-passthrough.html` | Passthrough spans (e.g. render LaTeX server-side with KaTeX) |

Codeblock hooks can be language-specific: `render-codeblock-mermaid.html` handles only
```` ```mermaid ```` blocks. This is how diagram support is added without a parser change.

Blockquote hooks receive an `AlertType` when the blockquote opens with `> [!NOTE]` and similar,
giving GitHub-alert parity through templates rather than syntax.

## Code blocks and highlighting

Hugo uses Chroma for syntax highlighting. Fenced code block info strings accept options:

````markdown
```go {linenos=table, hl_lines=[2,"4-5"], linenostart=10}
````

Options: `linenos` (`false`/`true`/`table`/`inline`), `hl_lines`, `linenostart`, `anchorlinenos`,
`lineanchors`, `hl_inline`, `style`. Configured globally under `markup.highlight`.

## Additional Markdown formats Hugo accepts

Hugo dispatches on file extension: `.md`/`.markdown` → Goldmark, `.html` → passthrough,
`.org` → Org-mode, `.adoc` → Asciidoctor (external binary), `.rst` → reStructuredText (external),
`.pandoc`/`.pdc` → Pandoc (external). Only Goldmark is built in.

## Implementation guidance

If Loki or the writing app is going to read and write Hugo content:

1. **Strip and preserve front matter separately.** Keep the original serialization format; don't
   normalize TOML to YAML on save.
2. **Tokenize shortcodes before Markdown parsing.** Regex for `{{<`/`{{%` with balanced
   delimiter matching, replace with sentinel tokens, restore on serialize. Don't try to parse
   their contents.
3. **Target CommonMark + `table`, `strikethrough`, `linkify`, `taskList`, `footnote`,
   `definitionList`** as the baseline — that's Hugo's default-on set.
4. **Treat attribute blocks `{...}` as trailing metadata** on the preceding node, not content.
5. **Don't apply typographer on read.** It's output-only; applying it corrupts round-trips.
6. **Respect `unsafe: false` semantics only on render**, never on storage — the source file keeps
   its raw HTML regardless.

For Rust, no crate implements Goldmark's exact extension set. Closest path: `pulldown-cmark` or
`markdown-rs` for the CommonMark core, plus custom handling for definition lists, attributes,
and passthrough. Definition list support is the main gap — neither crate has it natively.

## Further reading

- Markup configuration: <https://gohugo.io/configuration/markup/>
- Content formats: <https://gohugo.io/content-management/formats/>
- Shortcodes: <https://gohugo.io/content-management/shortcodes/>
- Render hooks: <https://gohugo.io/render-hooks/>
- Goldmark source: <https://github.com/yuin/goldmark>
- Hugo's Goldmark extensions: <https://github.com/gohugoio/hugo-goldmark-extensions>
