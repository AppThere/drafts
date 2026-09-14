# OpenDocument Text (ODT)

## Identity

| | |
|---|---|
| Spec name | Open Document Format for Office Applications (OpenDocument) |
| Version | **1.4** — OASIS Standard, dated 2025-10-06, approved 2025-12-03 |
| Previous | 1.3 (OASIS Standard, 2021-04-27) |
| ISO equivalent | ISO/IEC 26300 (tracks ODF 1.2; 1.3 and 1.4 are OASIS-only so far) |
| Canonical URL | <https://docs.oasis-open.org/office/OpenDocument/v1.4/> |
| TC home | <https://www.oasis-open.org/tc-opendocument/> |
| License | OASIS IPR — RF on Limited Terms |
| Editors | Francis Cave, Patrick Durusau, Svante Schubert, Michael Stahl |
| Text document extension | `.odt` (package), `.fodt` (flat XML) |
| MIME type | `application/vnd.oasis.opendocument.text` |

ODF 1.4 maintains **full backward compatibility** with 1.3 and earlier. It adds accessibility
improvements, professional formatting features, and revised developer documentation. A 1.2 or
1.3 reader will open a 1.4 file with graceful degradation of unknown elements.

## Document structure — four parts

| Part | Title | Approx. size | What it's for |
|---|---|---|---|
| Part 1 | Introduction | small | Overview, conformance, part relationships |
| Part 2 | Packages | moderate | The ZIP container, manifest, encryption, digital signatures |
| Part 3 | OpenDocument Schema | ~800 pages | **The element and attribute reference.** This is the one you'll live in. |
| Part 4 | Recalculated Formula (OpenFormula) | large | Spreadsheet formula language — irrelevant for text documents |

For a word processor, **Part 3 is the spec** and Part 2 is the packaging chapter. Part 4 can be
ignored entirely unless you support embedded spreadsheet objects with live formulas.

Part 3 ships with **RELAX NG schemas**. These are the highest-value artifact in the whole
distribution — machine-readable, and you can drive Rust type generation from them.

## Package format

A ZIP archive, with the same `mimetype`-first constraint as EPUB.

### ZIP requirements

- First entry must be `mimetype`, **stored uncompressed**, containing the MIME type string
  with no trailing newline and no extra field.
- Paths use `/`, UTF-8 names.

### Standard entries

| Path | Required | Contents |
|---|---|---|
| `mimetype` | Yes | `application/vnd.oasis.opendocument.text` |
| `META-INF/manifest.xml` | Yes | Lists every file in the package with media type |
| `content.xml` | Yes | Document body + automatic styles used by it |
| `styles.xml` | Common | Named styles, master pages, page layouts |
| `meta.xml` | Common | Document metadata |
| `settings.xml` | No | Application-specific view settings (cursor position, zoom) |
| `Thumbnails/thumbnail.png` | No | Preview image |
| `Pictures/` | No | Embedded images |
| `ObjectReplacements/` | No | Rendered previews of embedded objects |
| `Configurations2/` | No | Application config (LibreOffice-specific) |

`META-INF/manifest.xml`:
```xml
<manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0"
                   manifest:version="1.4">
  <manifest:file-entry manifest:full-path="/"
                       manifest:media-type="application/vnd.oasis.opendocument.text"
                       manifest:version="1.4"/>
  <manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/>
  <manifest:file-entry manifest:full-path="styles.xml" manifest:media-type="text/xml"/>
  <manifest:file-entry manifest:full-path="meta.xml" manifest:media-type="text/xml"/>
  <manifest:file-entry manifest:full-path="Pictures/img1.png" manifest:media-type="image/png"/>
</manifest:manifest>
```
Encryption info, when present, is declared per-entry here (`manifest:encryption-data`,
with Blowfish CFB or AES-256 and PBKDF2/Argon2 key derivation).

### Flat ODF (`.fodt`)

A single XML file with root `<office:document>` containing all four documents' content inline.
No ZIP, no embedded binaries (images are base64-inlined). Excellent for version control and
diffing — worth supporting as an export target for exactly that reason.

## Namespaces

The prefixes are conventional but universally used:

| Prefix | URI |
|---|---|
| `office` | `urn:oasis:names:tc:opendocument:xmlns:office:1.0` |
| `text` | `urn:oasis:names:tc:opendocument:xmlns:text:1.0` |
| `style` | `urn:oasis:names:tc:opendocument:xmlns:style:1.0` |
| `table` | `urn:oasis:names:tc:opendocument:xmlns:table:1.0` |
| `draw` | `urn:oasis:names:tc:opendocument:xmlns:drawing:1.0` |
| `fo` | `urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0` |
| `svg` | `urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0` |
| `meta` | `urn:oasis:names:tc:opendocument:xmlns:meta:1.0` |
| `number` | `urn:oasis:names:tc:opendocument:xmlns:datastyle:1.0` |
| `dc` | `http://purl.org/dc/elements/1.1/` |
| `xlink` | `http://www.w3.org/1999/xlink` |
| `script` | `urn:oasis:names:tc:opendocument:xmlns:script:1.0` |
| `form` | `urn:oasis:names:tc:opendocument:xmlns:form:1.0` |
| `manifest` | `urn:oasis:names:tc:opendocument:xmlns:manifest:1.0` |

Note ODF **reuses XSL-FO and SVG attribute names** wherever possible (`fo:font-size`,
`fo:margin-left`, `svg:width`). This is a deliberate design decision and a genuine ergonomic
advantage over OOXML's invented vocabulary — if you know CSS/XSL-FO, you can read ODF styles.

## `content.xml` structure

```xml
<office:document-content office:version="1.4" xmlns:...>
  <office:scripts/>
  <office:font-face-decls>
    <style:font-face style:name="Liberation Serif"
                     svg:font-family="'Liberation Serif'"
                     style:font-family-generic="roman"
                     style:font-pitch="variable"/>
  </office:font-face-decls>
  <office:automatic-styles>
    <style:style style:name="P1" style:family="paragraph" style:parent-style-name="Standard">
      <style:paragraph-properties fo:text-align="center"/>
    </style:style>
  </office:automatic-styles>
  <office:body>
    <office:text>
      <text:sequence-decls>…</text:sequence-decls>
      <text:h text:style-name="Heading_20_1" text:outline-level="1">Chapter One</text:h>
      <text:p text:style-name="P1">Body text with <text:span text:style-name="T1">emphasis</text:span>.</text:p>
    </office:text>
  </office:body>
</office:document-content>
```

## Text content elements

| Element | Purpose |
|---|---|
| `text:p` | Paragraph |
| `text:h` | Heading; `text:outline-level` 1–10 |
| `text:span` | Inline styled run |
| `text:a` | Hyperlink (`xlink:href`) |
| `text:s` | Multiple consecutive spaces (`text:c` = count) |
| `text:tab` | Tab character |
| `text:line-break` | Line break within a paragraph |
| `text:soft-page-break` | Rendered pagination hint |
| `text:list` / `text:list-item` / `text:list-header` | Lists |
| `text:section` | Named, optionally protected/linked region |
| `text:table-of-content` | TOC with source and generated `text:index-body` |
| `text:alphabetical-index`, `text:illustration-index`, `text:object-index`, `text:bibliography` | Other generated indexes |
| `text:bookmark`, `text:bookmark-start`, `text:bookmark-end` | Bookmarks (point vs range) |
| `text:reference-mark`, `text:reference-mark-start/end` | Cross-reference targets |
| `text:note` | Footnote/endnote container (`text:note-citation`, `text:note-body`) |
| `text:number` | Explicit list/heading number (for consumers that don't compute) |
| `text:change`, `text:change-start`, `text:change-end` | Tracked changes |
| `text:tracked-changes` | Change log at start of `office:text` |
| `office:annotation` | Comments |
| `draw:frame` | Container for images, text boxes, OLE objects |
| `table:table` | Tables (same vocabulary as spreadsheets) |

### Whitespace handling

**Critical and frequently mishandled.** ODF collapses whitespace in text content. Therefore:
- A single space is a literal space character.
- **Two or more consecutive spaces** must be encoded as one space plus `<text:s text:c="n"/>`.
- Tabs must be `<text:tab/>`.
- Line breaks must be `<text:line-break/>`.
- Leading/trailing whitespace in a paragraph is not preserved unless encoded.

A serializer that emits raw multiple spaces produces documents that render wrong in every
conformant consumer. This is the ODF equivalent of forgetting `xml:space="preserve"` in OOXML.

### Fields

Fields are elements, not escape codes: `text:page-number`, `text:page-count`, `text:date`,
`text:time`, `text:title`, `text:chapter`, `text:file-name`, `text:author-name`,
`text:sequence` (for figure/table numbering), `text:variable-set`/`text:variable-get`,
`text:user-defined`, `text:bookmark-ref`, `text:sequence-ref`, `text:note-ref`.

Each carries both the field definition *and* a cached display value as element text — so a
naive text extractor gets sensible output without evaluating fields.

## The style model

ODF's style system is its strongest design feature and the thing to understand first.

### Style categories

| Category | Location | Meaning |
|---|---|---|
| **Common (named) styles** | `styles.xml` → `<office:styles>` | User-visible, reusable: "Heading 1", "Quotation" |
| **Automatic styles** | `content.xml` or `styles.xml` → `<office:automatic-styles>` | Generated for direct formatting; named `P1`, `T1`, `fr1`. Not user-visible. |
| **Master styles** | `styles.xml` → `<office:master-styles>` | Page masters: headers, footers, page layout binding |
| **Default styles** | `<style:default-style style:family="…">` | Family-level fallback |

**This maps cleanly onto a style-first word processor.** Direct formatting in ODF isn't a
separate mechanism from styles — it *is* a style, just an anonymous one. If Loki's model is
style-first, ODF is the more natural of the two office formats to target, and a Loki document
model probably round-trips to ODF with less lossiness than to DOCX.

### Style families

`paragraph`, `text`, `section`, `table`, `table-column`, `table-row`, `table-cell`,
`table-page`, `chart`, `graphic`, `presentation`, `drawing-page`, `ruby`.

### Properties elements

Each style contains family-appropriate property elements:
```xml
<style:style style:name="Quotation" style:family="paragraph"
             style:parent-style-name="Standard"
             style:next-style-name="Standard">
  <style:paragraph-properties fo:margin-left="1cm" fo:margin-right="1cm"
                              fo:text-indent="0cm" fo:margin-top="0.2cm"/>
  <style:text-properties fo:font-style="italic"/>
</style:style>
```

- `style:text-properties` — `fo:font-size`, `fo:font-weight`, `fo:font-style`, `fo:color`,
  `style:font-name`, `style:text-underline-style`, `fo:language`, `fo:country`,
  `style:text-position` (sub/superscript), `fo:letter-spacing`
- `style:paragraph-properties` — `fo:margin-*`, `fo:text-indent`, `fo:text-align`,
  `fo:line-height`, `style:line-spacing`, `fo:break-before`/`after`,
  `fo:keep-together`, `fo:orphans`, `fo:widows`, `fo:background-color`, tab stops via
  `style:tab-stops`
- `style:section-properties`, `style:table-properties`, `style:graphic-properties`, etc.

Inheritance via `style:parent-style-name`. `style:next-style-name` controls what style follows
on Enter — a UI affordance encoded in the format.

### Page layout

```xml
<style:page-layout style:name="pm1">
  <style:page-layout-properties fo:page-width="21cm" fo:page-height="29.7cm"
                                fo:margin-top="2cm" fo:margin-bottom="2cm"
                                fo:margin-left="2cm" fo:margin-right="2cm"
                                style:print-orientation="portrait"/>
  <style:header-style>…</style:header-style>
  <style:footer-style>…</style:footer-style>
</style:page-layout>

<style:master-page style:name="Standard" style:page-layout-name="pm1">
  <style:header><text:p>…</text:p></style:header>
  <style:footer><text:p text:style-name="Footer">
    <text:page-number text:select-page="current">1</text:page-number>
  </text:p></style:footer>
</style:master-page>
```

Page breaks with a style change are triggered by `fo:break-before="page"` plus
`style:master-page-name` on a paragraph style.

### List styles

`text:list-style` containing `text:list-level-style-number`,
`text:list-level-style-bullet`, or `text:list-level-style-image`, one per level 1–10.
Numbering format via `style:num-format` (`1`, `a`, `A`, `i`, `I`), `style:num-prefix`,
`style:num-suffix`, `text:display-levels` for `1.2.3` style.

## Metadata (`meta.xml`)

```xml
<office:document-meta office:version="1.4">
  <office:meta>
    <meta:generator>Loki/1.0</meta:generator>
    <dc:title>Document Title</dc:title>
    <dc:creator>Author Name</dc:creator>
    <dc:date>2026-09-13T10:00:00</dc:date>
    <meta:creation-date>2026-09-01T09:00:00</meta:creation-date>
    <meta:editing-cycles>12</meta:editing-cycles>
    <meta:editing-duration>PT2H30M</meta:editing-duration>
    <meta:document-statistic meta:page-count="14" meta:word-count="3200"
                             meta:character-count="18400" meta:paragraph-count="98"/>
    <meta:user-defined meta:name="Project">AppThere</meta:user-defined>
  </office:meta>
</office:document-meta>
```

ODF 1.2+ also supports RDF metadata in the package (`META-INF/manifest.rdf` plus
`text:meta` / `text:meta-field` inline annotation) for semantic markup. Rarely used;
skip unless you have a specific need.

## Tracked changes

```xml
<text:tracked-changes>
  <text:changed-region xml:id="ct1">
    <text:insertion>
      <office:change-info>
        <dc:creator>Kevin</dc:creator>
        <dc:date>2026-09-13T10:00:00</dc:date>
      </office:change-info>
    </text:insertion>
  </text:changed-region>
</text:tracked-changes>
```
Referenced inline by `<text:change-start text:change-id="ct1"/>` … `<text:change-end
text:change-id="ct1"/>` for ranges, or `<text:change text:change-id="ct1"/>` for deletions
(where the deleted content lives inside the `text:deletion` element in the changed-region).

Note this is structurally *different* from OOXML, where revisions are inline `w:ins`/`w:del`
wrappers. ODF keeps a change log and references into it. Mapping between the two is non-trivial
and a known fidelity loss point in every converter.

## Conformance

ODF 1.4 defines conformance classes. A **conforming OpenDocument document** validates against
the RELAX NG schema. **Extended conformance** permits foreign elements and attributes in
other namespaces, which conformant consumers must preserve or ignore rather than reject.

Practical consequence: you can round-trip Loki-specific data through an ODF file using a
custom namespace, and LibreOffice will preserve it. This is the supported extension path.

## Implementation guidance

1. **Generate from the RELAX NG schema.** Part 3's schemas can drive codegen; hand-writing
   800 pages of element types is not a good use of time. Convert RNG → XSD if your tooling
   prefers it (`trang`).
2. **Start with a subset.** Paragraphs, headings, spans, styles (paragraph + text families),
   lists, tables, images, page layout. That covers the overwhelming majority of real documents.
3. **Get whitespace encoding right from day one** — `text:s`, `text:tab`, `text:line-break`.
4. **Automatic vs common styles is the core abstraction.** Decide early whether Loki's
   direct formatting maps to automatic styles (recommended) or gets flattened into named ones.
5. **Validate against LibreOffice.** There is no ODF equivalent of EPUBCheck with the same
   authority; LibreOffice is the de facto reference implementation, and "opens correctly in
   LibreOffice" is the practical conformance bar. `odfvalidator` from the ODF Toolkit exists
   and is worth wiring into CI, though it's Java.
6. **Flat ODF is a cheap win.** Same serializer, no ZIP, and it makes your test fixtures
   diffable.

**Rust crates:** essentially nothing mature. `quick-xml` + `zip` and your own model is the
path. Given that DOCX will need the same infrastructure, build a shared XML-document-package
layer and specialize per format.

## Further reading

- ODF 1.4 downloads: <https://docs.oasis-open.org/office/OpenDocument/v1.4/>
- TC page: <https://www.oasis-open.org/tc-opendocument/>
- ODF Toolkit (Java, includes validator): <https://odftoolkit.org/>
- LibreOffice source (`sw/source/filter/xml/`) as a reference implementation
