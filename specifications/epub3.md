# EPUB 3

## Identity

| | |
|---|---|
| Current spec | **EPUB 3.4** — W3C Candidate Recommendation Snapshot, 2026-07-21 |
| Previous stable | **EPUB 3.3** — W3C Recommendation, 2026-01-13 |
| Canonical URL (3.4) | <https://www.w3.org/TR/epub-34/> |
| Canonical URL (3.3) | <https://www.w3.org/TR/epub-33/> |
| Editors | Matt Garrish (DAISY Consortium), Ivan Herman (W3C) |
| Working group | W3C Publishing Maintenance Working Group |
| License | W3C Document License |
| Spec source | <https://github.com/w3c/epub-specs> |
| Test suite | <https://github.com/w3c/epub-tests> |
| Validator | EPUBCheck, <https://github.com/w3c/epubcheck> |

### Version numbering — the confusing part

"EPUB 3" is the *format*. The minor number (3.2, 3.3, 3.4) identifies a *revision of the
specification*, not a new format. A 3.4-conformant file is an EPUB 3 file. Reading systems do
not need to distinguish. The `version` attribute in the package document remains `"3.0"`.

As of September 2026, 3.4 is in Candidate Recommendation with implementations invited
(announced 2026-07-21). 3.3 is the current Recommendation. **Target 3.3 for production and
watch 3.4** — the delta is small and 3.4 will supersede it.

### The 3.4 document family

| Document | Scope |
|---|---|
| EPUB 3.4 | Authoring requirements — what a conformant publication looks like |
| EPUB Reading Systems 3.4 | Conformance requirements for renderers |
| EPUB Accessibility 1.2 | Accessibility conformance and discovery metadata |
| EPUB 3 Overview | Non-normative introduction (W3C Note) |
| EPUB Annotations 1.0 | First Public Working Draft, Feb 2026 — annotation model |

If you're writing Futhark (reader), **EPUB Reading Systems 3.4** is your primary normative
document, not EPUB 3.4 itself. If you're writing an editor/exporter, EPUB 3.4 is.

### Version-adaptive references

Since 3.2, EPUB references HTML, CSS, SVG, ECMAScript, and Unicode **undated**. The format
tracks the living web standards automatically. This means "what is valid EPUB" changes without
a spec revision — a design choice with real consequences for a reader implementation. Pin your
HTML/CSS support level explicitly and document it.

## Container format — OCF (Open Container Format)

An EPUB publication is a ZIP archive with strict constraints.

### ZIP requirements

- **The first entry must be `mimetype`.** It must be stored **uncompressed** (STORED, not
  DEFLATE), must have no extra field, and must contain exactly the ASCII string
  `application/epub+zip` with no trailing newline or BOM.
  This exists so that the file's type can be sniffed from bytes 30–50 of the archive.
- File names use UTF-8, forward slashes, and must not exceed 255 bytes; total path length
  limited to 65535 bytes.
- No encryption of the ZIP itself; ZIP64 permitted.

### Reserved directory: `META-INF/`

| File | Required | Purpose |
|---|---|---|
| `container.xml` | **Yes** | Points to the package document(s) |
| `encryption.xml` | No | Per-resource encryption info (incl. font obfuscation) |
| `signatures.xml` | No | XML digital signatures |
| `metadata.xml` | No | Container-level metadata |
| `rights.xml` | No | DRM information (content not specified) |
| `manifest.xml` | No | Reserved, no defined use |

`container.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/package.opf"
              media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>
```
Multiple rootfiles are permitted (rendition mapping / multiple renditions); the first is the
default. Everything outside `META-INF/` and `mimetype` is author-controlled layout — `OEBPS/`
is convention, not requirement.

## Package Document (`.opf`)

The manifest and spine. One per rendition. Root element `<package>` in namespace
`http://www.idpf.org/2007/opf`.

```xml
<package xmlns="http://www.idpf.org/2007/opf"
         version="3.0"
         unique-identifier="pub-id"
         xml:lang="en">
  <metadata>…</metadata>
  <manifest>…</manifest>
  <spine>…</spine>
  <collection>…</collection>   <!-- optional, repeatable -->
</package>
```

### `<metadata>`

Uses Dublin Core Metadata Element Set (`http://purl.org/dc/elements/1.1/`) plus `<meta>`
elements for refinement.

**Required:**
- `dc:identifier` — at least one, with `id` matching `package/@unique-identifier`
- `dc:title` — at least one
- `dc:language` — at least one
- `<meta property="dcterms:modified">` — exactly one, format `CCYY-MM-DDThh:mm:ssZ`,
  UTC, no fractional seconds

**Optional DC elements:** `dc:contributor`, `dc:coverage`, `dc:creator`, `dc:date`,
`dc:description`, `dc:format`, `dc:publisher`, `dc:relation`, `dc:rights`, `dc:source`,
`dc:subject`, `dc:type`.

**Refinement model** — `<meta>` with `refines` pointing at another element's fragment ID:
```xml
<dc:creator id="creator">Haruki Murakami</dc:creator>
<meta refines="#creator" property="role" scheme="marc:relators">aut</meta>
<meta refines="#creator" property="file-as">Murakami, Haruki</meta>
<meta refines="#creator" property="display-seq">1</meta>
```

Vocabulary properties include: `alternate-script`, `belongs-to-collection`, `collection-type`,
`display-seq`, `file-as`, `group-position`, `identifier-type`, `role`, `source-of`,
`title-type`, `authority`, `term`.

**Rendition properties** (fixed layout) go here as `<meta>`:
`rendition:layout` (`reflowable`|`pre-paginated`), `rendition:orientation`
(`auto`|`landscape`|`portrait`), `rendition:spread`
(`auto`|`none`|`landscape`|`both`), `rendition:flow`
(`auto`|`paginated`|`scrolled-continuous`|`scrolled-doc`).

**Accessibility metadata** (from EPUB Accessibility 1.2, using schema.org):
`accessMode`, `accessModeSufficient`, `accessibilityFeature`, `accessibilityHazard`,
`accessibilitySummary`, plus `dcterms:conformsTo` pointing at the a11y conformance URL,
and `a11y:certifiedBy`.

### `<manifest>`

Every resource in the publication, exactly once.

```xml
<manifest>
  <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
  <item id="cover" href="cover.jpg" media-type="image/jpeg" properties="cover-image"/>
  <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
  <item id="css" href="style.css" media-type="text/css"/>
  <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
</manifest>
```

**`properties` values:** `cover-image`, `mathml`, `nav` (exactly one item must have it),
`remote-resources`, `scripted`, `svg`, `switch` (deprecated), `data-nav`.

`fallback` attribute chains non-core media types to a core-media-type alternative.
`media-overlay` points at a SMIL item.

### `<spine>`

Default reading order.

```xml
<spine toc="ncx">
  <itemref idref="cover" linear="no"/>
  <itemref idref="ch1" properties="page-spread-left"/>
  <itemref idref="ch2" properties="rendition:layout-pre-paginated"/>
</spine>
```

- `linear="no"` — auxiliary content, reachable by link but not in linear flow
- `properties` — `page-spread-left`, `page-spread-right`, `page-spread-center`, and
  per-spine-item overrides of the rendition properties
- `toc` attribute — legacy NCX reference, EPUB 2 compatibility only

### `<collection>`

Groups manifest items for defined roles. Used for index, preview, dictionary, and
manifest-of-manifests. Rarely encountered in trade ebooks.

### Legacy `<guide>`

EPUB 2 structural semantics. Deprecated in EPUB 3 in favour of the `landmarks` nav.
Some retailers still require it. Harmless to emit both.

## Core Media Types

Resources of these types need no fallback:

| Category | Types |
|---|---|
| Image | GIF, JPEG, PNG, SVG, WebP |
| Audio | MP3, MP4/AAC (`audio/mp4`), Opus in Ogg |
| Style | CSS |
| Font | WOFF, WOFF2, OpenType/SFNT (`font/ttf`, `font/otf`) |
| Other | XHTML (`application/xhtml+xml`), JavaScript, SMIL (media overlays), NCX (legacy), package document |

Anything else is a *foreign resource* and requires a manifest fallback — unless it is not in
the spine and not embedded in a content document.

## Content Documents

### XHTML Content Documents

Media type `application/xhtml+xml`. **XML serialization of HTML** — meaning well-formedness is
mandatory: every tag closed, attributes quoted, `&` escaped, void elements self-closed.
This is the single most common source of EPUBCheck errors.

The `epub` namespace (`http://www.idpf.org/2007/ops`) provides `epub:type` for structural
semantics:
```xml
<section epub:type="chapter" role="doc-chapter">
  <h1 epub:type="title">Chapter One</h1>
  <aside epub:type="footnote" id="fn1">…</aside>
</section>
```
The `epub:type` vocabulary (structural semantics) covers roughly 80 terms: `cover`, `frontmatter`,
`bodymatter`, `backmatter`, `chapter`, `part`, `toc`, `landmarks`, `footnote`, `noteref`,
`endnote`, `bibliography`, `glossary`, `index`, `pagebreak`, `titlepage`, `colophon`, and so on.
ARIA `role` from DPUB-ARIA is the accessibility-facing parallel and should be used alongside.

### SVG Content Documents

Permitted in the spine. Constrained profile of SVG 1.1+. Used mostly for fixed-layout comics
and picture books.

### CSS

EPUB references the current CSS snapshot. Reading system support is wildly variable in practice.
Notable EPUB-specific properties: `-epub-` prefixed forms of writing-mode, ruby, text-emphasis,
hyphens. `@page` is honoured only in pre-paginated contexts by most readers.

## Navigation Document

An XHTML content document containing `<nav>` elements. **Required** — the manifest item must
carry `properties="nav"`.

```xml
<nav epub:type="toc" id="toc">
  <h1>Contents</h1>
  <ol>
    <li><a href="ch1.xhtml">Chapter One</a>
      <ol><li><a href="ch1.xhtml#s1">Section 1</a></li></ol>
    </li>
  </ol>
</nav>

<nav epub:type="landmarks" hidden="">
  <ol>
    <li><a epub:type="bodymatter" href="ch1.xhtml">Start of Content</a></li>
    <li><a epub:type="toc" href="nav.xhtml#toc">Table of Contents</a></li>
  </ol>
</nav>

<nav epub:type="page-list" hidden="">…</nav>
```

- `toc` nav: **required**, exactly one
- `page-list`: optional, maps to print pagination via `epub:type="pagebreak"` markers in content
- `landmarks`: optional, key structural entry points
- `hidden` attribute suppresses display while keeping the nav machine-readable

The legacy `toc.ncx` is optional in EPUB 3 but still expected by older reading systems and some
retailers (Amazon's pipeline historically required it). Cheap to emit; emit it.

## Media Overlays

Synchronized text-audio narration. A subset of SMIL 3.0.

```xml
<smil xmlns="http://www.w3.org/ns/SMIL" version="3.0">
  <body>
    <seq id="s1" epub:textref="ch1.xhtml#sec1" epub:type="chapter">
      <par id="p1">
        <text src="ch1.xhtml#para1"/>
        <audio src="ch1.mp3" clipBegin="0:00:01.000" clipEnd="0:00:05.250"/>
      </par>
    </seq>
  </body>
</smil>
```

Elements: `smil`, `head`, `body`, `seq`, `par`, `text`, `audio`. Clock values in full or
partial clock syntax. Package-level metadata `media:duration` (total and per-overlay) and
`media:active-class` / `media:playback-active-class` for highlight styling.

Text-to-speech is permitted as an alternative to pre-recorded audio, with pronunciation
control via PLS lexicons and SSML attributes (`ssml:ph`).

## Font obfuscation

EPUB defines an obfuscation algorithm (not encryption — it's a deterrent, keyed on the
publication's unique identifier). Obfuscated fonts are declared in `META-INF/encryption.xml`
with algorithm `http://www.idpf.org/2008/embedding`.

Algorithm: SHA-1 the unique identifier (whitespace-stripped), XOR the first 1040 bytes of the
font file against the 20-byte key, repeating. Fully reversible; a reader must implement it or
embedded fonts in many commercial EPUBs will fail to load.

Adobe's older, incompatible obfuscation (`http://ns.adobe.com/pdf/enc#RC`) uses the first 1024
bytes and a different key derivation. You'll encounter both in the wild.

## Fixed layout

Set `rendition:layout` to `pre-paginated` at package level or per spine item. Each content
document then declares its dimensions:

```html
<meta name="viewport" content="width=1200, height=1600"/>
```

For SVG content documents, the `viewBox` serves this role. Pre-paginated documents are rendered
as a unit and scaled to fit; reflow is disabled.

## Reading System requirements (for Futhark)

From EPUB Reading Systems 3.4, the load-bearing ones:

- **Unique origin per publication.** Each EPUB should get its own security origin so scripts
  in one book can't reach another's storage. This is the main scripting security requirement.
- Must support all core media types.
- Must process the package document, spine order, and navigation document.
- Must resolve manifest fallbacks.
- Must support `epub:type` for at least footnote handling (popup footnotes).
- Should support Media Overlays if claiming audio conformance.
- Scripting support is optional but if supported, must be sandboxed.

## Changes 3.3 → 3.4

3.4 is an incremental revision. Broadly: clarifications to the authoring requirements, continued
alignment with evolving web platform specs, refinements to accessibility conformance, and
tightening of areas where 3.3 was ambiguous. Nothing in 3.4 invalidates a well-formed 3.3
publication. Check the changes appendix upstream before committing to a 3.4-specific feature,
since CR content can still shift before Recommendation.

## Implementation guidance

**For a reader (Futhark):**
1. ZIP reading with correct handling of the stored `mimetype` entry.
2. Parse `container.xml` → package document → manifest + spine.
3. Build a resource resolver mapping manifest hrefs (relative to the package document) to ZIP
   entries. Percent-decoding matters; so does case sensitivity.
4. Render XHTML through your existing engine (Blitz/Vello gets you most of the way). The gap
   is pagination, not rendering.
5. Implement both font obfuscation algorithms up front — it's 30 lines and saves confusing bugs.
6. CFI (Canonical Fragment Identifiers) for bookmarks/annotations — not covered here, see the
   separate EPUB CFI spec. This is what you need for position persistence across reflows.

**For an exporter (Loki):**
1. Generate well-formed XHTML. Run it through an XML serializer, never string templating.
2. `dcterms:modified` is mandatory and must be exactly formatted.
3. Emit both `nav.xhtml` and `toc.ncx`.
4. Validate with EPUBCheck in CI. It's the de facto arbiter and retailers run it.

**Rust crates:** the ecosystem is thin. `epub` and `epub-builder` exist but are basic.
`zip` + `quick-xml` + your own model is the realistic path, which is fine — the format is
XML and ZIP, both of which you already need.

## Further reading

- EPUB 3.4: <https://www.w3.org/TR/epub-34/>
- EPUB Reading Systems 3.4: <https://www.w3.org/TR/epub-rs-34/>
- EPUB Accessibility 1.2: <https://www.w3.org/TR/epub-a11y-12/>
- EPUB 3 Overview: <https://www.w3.org/TR/epub-overview-34/>
- Spec sources and issues: <https://github.com/w3c/epub-specs>
- Test suite: <https://w3c.github.io/epub-tests/>
- EPUBCheck: <https://github.com/w3c/epubcheck>
