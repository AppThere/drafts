# OOXML — WordprocessingML (DOCX)

## Identity

| | |
|---|---|
| Spec name | ECMA-376 — Office Open XML File Formats |
| Edition | **5th edition** |
| Part 1 | Fundamentals and Markup Language Reference — 5th ed., December 2016 |
| Part 2 | Open Packaging Conventions — 5th ed., December 2021 |
| Part 3 | Markup Compatibility and Extensibility — 5th ed., December 2015 |
| Part 4 | Transitional Migration Features — 5th ed., December 2016 |
| ISO equivalent | ISO/IEC 29500 (parts 1–4), 2016 revision |
| Canonical URL | <https://ecma-international.org/publications-and-standards/standards/ecma-376/> |
| Committee | Ecma TC45 |
| License | Free of charge from Ecma. ISO version is paid, technically identical. |
| Extension | `.docx` (no macros), `.docm` (macro-enabled), `.dotx` (template) |
| MIME type | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` |

Only Part 2 was revised in the 5th edition cycle; Parts 1, 3, and 4 carry earlier 5th-edition
dates. Part 1 is the big one — roughly 5,000 pages, most of it element reference.

**Download Part 1 and the schema ZIP.** The XSDs are the useful artifact; the prose is a
reference you grep, not read.

### Strict vs Transitional

ISO/IEC 29500 defines two conformance classes:

- **Transitional** — includes legacy compatibility features carried over from binary `.doc`:
  VML drawings, legacy date formats, deprecated attributes. This is what Word actually writes
  by default.
- **Strict** — the clean subset. No VML, DrawingML only, ISO 8601 dates.

Part 4 documents the Transitional-only features. In practice **you must read Transitional**,
because that's what exists in the wild. Write Transitional or Strict as you prefer; read both.

### Microsoft's implementation notes

The spec describes the format; Word's actual behaviour deviates in documented ways. These are
essential and often more useful than the spec itself for interop work:

- **[MS-OE376]** — Office implementation information for ECMA-376, revision 4.1 (2022)
- **[MS-DOCX]** — Word Extensions to the Office Open XML file format
- Both at <https://learn.microsoft.com/en-us/openspecs/office_standards/>

## Package format — OPC (Part 2)

A ZIP archive of *parts* connected by *relationships*. Unlike EPUB and ODF, **there is no
`mimetype` first-entry rule** — OPC uses a content-types stream instead.

### `[Content_Types].xml`

Mandatory, at package root. Maps extensions and specific parts to media types.

```xml
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Default Extension="png" ContentType="image/png"/>
  <Override PartName="/word/document.xml"
    ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml"
    ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>
```

`Default` by extension, `Override` per part. Every part must be typed by one or the other.

### Relationships

Relationships are stored in `_rels/` directories alongside the parts they describe.
The part `/word/document.xml` has its relationships in `/word/_rels/document.xml.rels`.
The package itself has `/_rels/.rels`.

`/_rels/.rels`:
```xml
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1"
    Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
    Target="word/document.xml"/>
  <Relationship Id="rId2"
    Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties"
    Target="docProps/core.xml"/>
</Relationships>
```

**Relationship IDs are the indirection layer throughout the format.** An image in
`document.xml` is referenced as `r:embed="rId7"`, resolved through the relationships part
to a target path. Hyperlinks, headers, footers, footnotes, styles — all by rId. Build the
relationship resolver first; everything else depends on it.

`TargetMode="External"` marks a relationship pointing outside the package (external
hyperlinks, linked images).

### Typical part layout

```
[Content_Types].xml
_rels/.rels
docProps/core.xml          Dublin Core metadata
docProps/app.xml           Application metadata, statistics
docProps/custom.xml        User-defined properties
word/document.xml          ← the document body
word/_rels/document.xml.rels
word/styles.xml            Style definitions
word/numbering.xml         List/numbering definitions
word/settings.xml          Document-level settings
word/webSettings.xml
word/fontTable.xml         Font declarations
word/theme/theme1.xml      DrawingML theme (colors, fonts, effects)
word/footnotes.xml
word/endnotes.xml
word/comments.xml
word/commentsExtended.xml  (MS extension — threading)
word/header1.xml
word/footer1.xml
word/media/image1.png
word/glossary/             Building blocks / AutoText
customXml/                 Embedded custom XML data
```

## Namespaces

| Prefix | URI | Scope |
|---|---|---|
| `w` | `http://schemas.openxmlformats.org/wordprocessingml/2006/main` | WordprocessingML |
| `r` | `http://schemas.openxmlformats.org/officeDocument/2006/relationships` | Relationship refs |
| `a` | `http://schemas.openxmlformats.org/drawingml/2006/main` | DrawingML |
| `wp` | `…/drawingml/2006/wordprocessingDrawing` | Drawing anchoring in Word |
| `pic` | `…/drawingml/2006/picture` | Pictures |
| `m` | `…/officeDocument/2006/math` | OMML equations |
| `mc` | `http://schemas.openxmlformats.org/markup-compatibility/2006` | MCE |
| `v` | `urn:schemas-microsoft-com:vml` | VML (Transitional only) |
| `w14`, `w15`, `w16` | `http://schemas.microsoft.com/office/word/…` | Word extensions |

## The document body

```xml
<w:document xmlns:w="…">
  <w:body>
    <w:p>
      <w:pPr>
        <w:pStyle w:val="Heading1"/>
        <w:jc w:val="center"/>
      </w:pPr>
      <w:r>
        <w:rPr><w:b/><w:sz w:val="28"/></w:rPr>
        <w:t xml:space="preserve">Chapter One </w:t>
      </w:r>
    </w:p>
    <w:sectPr>…</w:sectPr>
  </w:body>
</w:document>
```

### The core hierarchy

```
w:body
 └─ w:p                    paragraph
     ├─ w:pPr              paragraph properties  (must be FIRST child)
     └─ w:r                run — a span of identically-formatted text
         ├─ w:rPr          run properties        (must be FIRST child)
         ├─ w:t            text
         ├─ w:br           break
         ├─ w:tab          tab
         ├─ w:drawing      DrawingML image
         └─ w:fldChar      field character
```

**The `pPr`-first and `rPr`-first rules are schema requirements**, not conventions. Word
rejects documents that violate them. This bites everyone once.

### `xml:space="preserve"`

Required on any `w:t` with leading or trailing whitespace. Without it, whitespace is stripped.
Safest policy: emit it unconditionally on every `w:t`.

### Runs

A run is a maximal span of uniform formatting. Word splits runs aggressively — spell-check
state, language, proofing marks, and revision tracking all cause splits. A single word may span
three runs. **Any text extraction or search must concatenate across runs**, and any formatting
operation must be prepared to split and merge them.

### Measurement units

This is a persistent source of bugs. There is no single unit.

| Unit | Value | Used for |
|---|---|---|
| **twip** (dxa) | 1/1440 inch = 1/20 point | Margins, indents, table widths, tab stops |
| **half-point** | 1/2 point | Font sizes (`w:sz w:val="24"` = 12pt) |
| **eighth-of-a-point** | 1/8 point | Border widths (`w:sz` on `w:bdr`) |
| **EMU** | 1/914400 inch | DrawingML extents (`wp:extent cx cy`) |
| **fiftieth of a percent** | 1/50 % | Some table width percentages (`pct`) |
| **1/100 mm** | | Some legacy attributes |

EMU is chosen to be an integer multiple of both inches and centimetres: 914400 EMU/inch,
360000 EMU/cm. Keep a units module; do not scatter conversions.

### Boolean attributes

`<w:b/>` means on. `<w:b w:val="0"/>` or `w:val="false"` means off. An absent element means
"inherit". **Absent ≠ off** — this three-state logic matters for style inheritance and is the
second-most-common source of rendering divergence after units.

## Sections

`w:sectPr` defines page setup. The final one is a direct child of `w:body`; earlier ones live
inside the `w:pPr` of the last paragraph of their section.

```xml
<w:sectPr>
  <w:headerReference w:type="default" r:id="rId5"/>
  <w:footerReference w:type="default" r:id="rId6"/>
  <w:pgSz w:w="12240" w:h="15840"/>              <!-- 8.5" x 11" in twips -->
  <w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"
           w:header="720" w:footer="720" w:gutter="0"/>
  <w:cols w:space="720"/>
  <w:titlePg/>
</w:sectPr>
```

Header/footer types: `default`, `first`, `even`.

## Styles (`styles.xml`)

```xml
<w:styles>
  <w:docDefaults>
    <w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri"/><w:sz w:val="22"/></w:rPr></w:rPrDefault>
    <w:pPrDefault><w:pPr><w:spacing w:after="160" w:line="259" w:lineRule="auto"/></w:pPr></w:pPrDefault>
  </w:docDefaults>
  <w:style w:type="paragraph" w:styleId="Heading1">
    <w:name w:val="heading 1"/>
    <w:basedOn w:val="Normal"/>
    <w:next w:val="Normal"/>
    <w:link w:val="Heading1Char"/>
    <w:uiPriority w:val="9"/>
    <w:qFormat/>
    <w:pPr>
      <w:keepNext/>
      <w:outlineLvl w:val="0"/>
      <w:spacing w:before="240"/>
    </w:pPr>
    <w:rPr><w:b/><w:sz w:val="32"/></w:rPr>
  </w:style>
</w:styles>
```

Style types: `paragraph`, `character`, `table`, `numbering`.

**Formatting resolution order** (each layer overrides the previous):
1. `docDefaults`
2. Table style (for content in tables), with conditional formatting bands
3. Numbering style properties
4. Paragraph style (following the `basedOn` chain to its root)
5. Character style
6. Direct paragraph formatting (`w:pPr`)
7. Direct run formatting (`w:rPr`)

Implementing this cascade correctly is the single largest correctness task in a DOCX reader.
Note `w:link` pairing a paragraph style with its character-style counterpart.

**Latent styles** (`w:latentStyles`) declare UI behaviour for built-in styles not explicitly
defined. Mostly ignorable for rendering.

## Numbering (`numbering.xml`)

Two-level indirection:

```xml
<w:numbering>
  <w:abstractNum w:abstractNumId="0">
    <w:multiLevelType w:val="hybridMultilevel"/>
    <w:lvl w:ilvl="0">
      <w:start w:val="1"/>
      <w:numFmt w:val="decimal"/>
      <w:lvlText w:val="%1."/>
      <w:lvlJc w:val="left"/>
      <w:pPr><w:ind w:left="720" w:hanging="360"/></w:pPr>
    </w:lvl>
  </w:abstractNum>
  <w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num>
</w:numbering>
```

A paragraph joins a list via `<w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr>`.
`w:num` can override individual levels of the abstract definition (`w:lvlOverride`), which is
how "restart numbering here" works.

`w:lvlText` uses `%1`–`%9` placeholders referring to level counters, enabling `1.2.3` formats.
`w:numFmt` values: `decimal`, `upperRoman`, `lowerRoman`, `upperLetter`, `lowerLetter`,
`bullet`, `ordinal`, `cardinalText`, `chicago`, plus East Asian formats.

**List numbering is computed, not stored.** The document contains no rendered numbers; the
consumer must walk the document maintaining counters per numId/ilvl. Restart semantics
(`w:start`, `w:lvlRestart`) are subtle.

## Tables

```xml
<w:tbl>
  <w:tblPr>
    <w:tblStyle w:val="TableGrid"/>
    <w:tblW w:w="0" w:type="auto"/>
    <w:tblBorders>…</w:tblBorders>
  </w:tblPr>
  <w:tblGrid>
    <w:gridCol w:w="4675"/>
    <w:gridCol w:w="4675"/>
  </w:tblGrid>
  <w:tr>
    <w:trPr><w:trHeight w:val="300"/></w:trPr>
    <w:tc>
      <w:tcPr>
        <w:tcW w:w="4675" w:type="dxa"/>
        <w:gridSpan w:val="2"/>
        <w:vMerge w:val="restart"/>
      </w:tcPr>
      <w:p>…</w:p>
    </w:tc>
  </w:tr>
</w:tbl>
```

- `w:gridGrid` establishes column widths; cell widths should agree but often don't.
- Horizontal merge: `w:gridSpan`.
- Vertical merge: `w:vMerge w:val="restart"` on the first cell, bare `w:vMerge` on continuations.
- **Every `w:tc` must contain at least one `w:p`.** Empty cells still need an empty paragraph.
- Table styles support conditional formatting (`w:tblLook`, first row/column, banding) —
  a common fidelity gap.

## Fields

Two forms.

**Simple:**
```xml
<w:fldSimple w:instr=" PAGE ">
  <w:r><w:t>1</w:t></w:r>
</w:fldSimple>
```

**Complex** — a state machine across runs:
```xml
<w:r><w:fldChar w:fldCharType="begin"/></w:r>
<w:r><w:instrText xml:space="preserve"> TOC \o "1-3" \h </w:instrText></w:r>
<w:r><w:fldChar w:fldCharType="separate"/></w:r>
<w:r><w:t>Cached result text</w:t></w:r>
<w:r><w:fldChar w:fldCharType="end"/></w:r>
```

The content between `separate` and `end` is the cached last-computed result. A reader that
doesn't evaluate fields can render the cache and be mostly right. Field codes can nest.

Common instructions: `PAGE`, `NUMPAGES`, `DATE`, `TIME`, `TOC`, `REF`, `PAGEREF`, `SEQ`,
`STYLEREF`, `HYPERLINK`, `INCLUDEPICTURE`, `MERGEFIELD`, `IF`, `DOCPROPERTY`.

Hyperlinks are more commonly the dedicated element: `<w:hyperlink r:id="rId8">…</w:hyperlink>`.

## Revision tracking

Inline wrappers, unlike ODF's change log:

```xml
<w:p>
  <w:r><w:t xml:space="preserve">Kept text </w:t></w:r>
  <w:ins w:id="1" w:author="Kevin" w:date="2026-09-13T10:00:00Z">
    <w:r><w:t>inserted text</w:t></w:r>
  </w:ins>
  <w:del w:id="2" w:author="Kevin" w:date="2026-09-13T10:01:00Z">
    <w:r><w:delText>deleted text</w:delText></w:r>
  </w:del>
</w:p>
```

Note `w:delText` replaces `w:t` inside deletions. Formatting changes use `w:rPrChange` /
`w:pPrChange` carrying the *previous* properties. Moves use `w:moveFrom`/`w:moveTo`.
Paragraph-mark insertions/deletions are recorded via `w:rPr/w:ins` inside `w:pPr`.

## Content controls (structured document tags)

```xml
<w:sdt>
  <w:sdtPr>
    <w:alias w:val="Title"/>
    <w:tag w:val="doc-title"/>
    <w:id w:val="12345"/>
    <w:dataBinding w:xpath="/root/title" w:storeItemID="{GUID}"/>
    <w:text/>
  </w:sdtPr>
  <w:sdtContent>
    <w:p><w:r><w:t>Bound value</w:t></w:r></w:p>
  </w:sdtContent>
</w:sdt>
```

Types: `w:text`, `w:richText`, `w:picture`, `w:comboBox`, `w:dropDownList`, `w:date`,
`w:checkbox` (w14), `w:repeatingSection` (w15). `w:dataBinding` links to custom XML parts —
this is the mechanism behind document automation and templating pipelines.

## Images

```xml
<w:drawing>
  <wp:inline distT="0" distB="0" distL="0" distR="0">
    <wp:extent cx="5486400" cy="3200400"/>      <!-- EMU -->
    <wp:docPr id="1" name="Picture 1" descr="Alt text"/>
    <a:graphic>
      <a:graphicData uri="…/picture">
        <pic:pic>
          <pic:blipFill><a:blip r:embed="rId7"/></pic:blipFill>
          <pic:spPr><a:xfrm><a:ext cx="5486400" cy="3200400"/></a:xfrm></pic:spPr>
        </pic:pic>
      </a:graphicData>
    </a:graphic>
  </wp:inline>
</w:drawing>
```

`wp:inline` for inline images, `wp:anchor` for floating (with wrap mode, positioning relative
to page/margin/column). In Transitional documents you'll also encounter VML
(`<w:pict><v:shape>`) for older content and for text boxes.

## Markup Compatibility and Extensibility (Part 3)

MCE lets a producer emit newer markup with a fallback for older consumers:

```xml
<mc:AlternateContent>
  <mc:Choice Requires="wps">
    <!-- modern DrawingML text box -->
  </mc:Choice>
  <mc:Fallback>
    <!-- VML equivalent -->
  </mc:Fallback>
</mc:AlternateContent>
```

Also `mc:Ignorable="w14 w15 wp14"` on the root element, declaring namespaces a consumer may
skip, and `mc:ProcessContent` for elements whose children should still be processed.

**A conformant reader must implement MCE preprocessing** before parsing WordprocessingML,
or it will choke on Word's output. This is a discrete, testable component — build it separately.

## Implementation guidance

**Order of work for a DOCX reader/writer:**

1. OPC layer: ZIP + `[Content_Types].xml` + relationship graph. Shared with XLSX/PPTX if you
   ever want them.
2. MCE preprocessing pass.
3. Units module. Twips, half-points, EMU, eighths. Test it in isolation.
4. Body traversal: `w:p` / `w:r` / `w:t`, plus breaks and tabs. This alone gets you plain-text
   extraction.
5. Style resolution cascade. The big one.
6. Sections and page setup.
7. Numbering computation.
8. Tables, including merges.
9. Images.
10. Fields (render cached values first; evaluate later).
11. Revisions, comments, content controls.

**Generate types from the XSDs.** ECMA-376 ships schemas; hand-writing the element model is not
viable at this scale. `xsd-parser-rs` or a custom codegen pass over the XSDs will save months.

**Rust crates:** `docx-rs` (write-focused, usable), `docx-rust` (read/write, incomplete),
`ooxmlsdk` (codegen'd from the schemas — closest to a full model). None is production-complete
for high-fidelity round-tripping. Expect to build on `quick-xml` + `zip` with your own model,
reusing the OPC and units layers across DOCX and ODT.

**Fidelity reality check.** Full DOCX fidelity is not achievable and no one has it — LibreOffice
has had hundreds of person-years on it. Decide early what fidelity tier Loki targets:
(a) faithful *rendering* of received documents, (b) lossless round-trip of Loki-authored
documents, (c) lossless round-trip of Word-authored documents. (a) and (b) are tractable;
(c) is an ongoing programme, not a feature.

**Preserving the unknown.** For round-trip safety, retain unrecognised elements verbatim in
your model and re-emit them in place. This turns "we don't support feature X" from data loss
into pass-through, and it's much cheaper than implementing X.

## Further reading

- ECMA-376 (all parts, free): <https://ecma-international.org/publications-and-standards/standards/ecma-376/>
- Microsoft Open Specifications: <https://learn.microsoft.com/en-us/openspecs/office_standards/>
- [MS-OE376] implementation notes: <https://learn.microsoft.com/en-us/openspecs/office_standards/ms-oe376/>
- Open XML SDK docs (concepts transfer even if the API doesn't): <https://learn.microsoft.com/en-us/office/open-xml/>
- `officeDissector` / `unzip -l` — inspecting real documents is the fastest way to learn the format
