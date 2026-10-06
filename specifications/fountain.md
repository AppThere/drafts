# Fountain

## Identity

| | |
|---|---|
| Format | Fountain — plain text screenplay markup |
| Version | 1.1 |
| Canonical URL | <https://fountain.io/syntax/> |
| Authors | Stu Maschwitz, John August, Nima Yousefi |
| Origin | Formerly "Screenplay Markdown" (SPMD) |
| Reference implementations | <https://github.com/nyousefi/Fountain> (Objective-C, MIT) |
| File extension | `.fountain` |
| MIME type | `text/plain` (no registered type) |

Fountain is deliberately small. The design principle is that a Fountain file should read as a
screenplay in plain text, with syntax markers used only where inference would be ambiguous.
The complete grammar fits on a page.

Fountain covers the creative phase of writing. It deliberately omits production features:
MOREs, CONTINUEDs, revision marks, locked pages, colored pages. Scene numbers are supported
because they matter for archival.

## Core parsing model

**Fountain is line-oriented and blank-line delimited.** Almost every rule is of the form
"an element is a line or block of lines, preceded and/or followed by a blank line, that matches
some shape." Case matters: uppercase is semantically significant for scene headings, characters,
and transitions.

Every element type has a **forcing character** that removes ambiguity. When implementing, check
forcing characters first — they short-circuit all inference.

| Element | Forcing char | Position |
|---|---|---|
| Scene Heading | `.` | Line start (not followed by another `.`) |
| Action | `!` | Line start |
| Character | `@` | Line start |
| Transition | `>` | Line start (not ending in `<`) |
| Lyrics | `~` | Line start |

## Element reference

### Title Page

Optional. Must be the first thing in the file. Key-value pairs, colon-separated, terminated by
a blank line. Keys are case-insensitive.

```
Title:
    _**THE LAST BIRTHDAY CARD**_
Credit: Written by
Author: Stu Maschwitz
Source: Story by KTM
Draft date: 1/20/2012
Contact:
    Next Level Productions
    1588 Mission Dr.
    Solvang, CA 93463
```

Values may be inline after the colon, or indented on following lines (indentation is any
leading whitespace). Multi-line values preserve their line breaks.

Recognized keys: `Title`, `Credit`, `Author`/`Authors`, `Source`, `Notes`, `Draft date`,
`Date`, `Contact`, `Copyright`. Unknown keys are permitted and typically ignored by renderers.

Title page values may contain emphasis markup.

### Scene Heading (Slugline)

A line preceded by a blank line, followed by a blank line, that begins with one of:
`INT`, `EXT`, `EST`, `INT./EXT`, `INT/EXT`, `I/E` — case-insensitive per the spec, though
convention is uppercase. Drafts also accepts `EXT./INT` and `EXT/INT`, which writers use for the
same thing, and a screenplay may have its own list (`appthere-drafts.md` §11.3).

```
EXT. BRICK'S POOL - DAY
```

**Forced:** prefix with a single period. The period is not rendered.
```
.SNIPER SCOPE POV
```
A line beginning with two or more periods is *not* a forced scene heading — that reserves `..`
for action text starting with an ellipsis.

**Scene numbers:** appended in `#...#` at end of line. Content may be alphanumeric with
hyphens and periods.
```
INT. HOUSE - DAY #1#
EXT. BRICK'S PATIO - DAY #A1.2#
```

### Action

Any paragraph that doesn't match another element. Blank-line separated. Leading whitespace is
preserved (this is the mechanism for hand-positioned text).

**Forced:** prefix with `!`. Necessary when a line would otherwise be read as a character name
(all uppercase) or a scene heading.

Consecutive non-blank lines form a single action block, with line breaks preserved.

### Character

A line in **all uppercase**, preceded by a blank line, **not** followed by a blank line.
May contain numbers, and may end with a character extension in parentheses.

```
STEEL
MOM (V.O.)
HANS (on the radio)
```

The uppercase requirement applies to letters only — numbers, spaces, and punctuation are
permitted. A character line may not consist solely of uppercase if it ends in `TO:` (that's
a transition).

**Forced:** prefix with `@`. Required for lowercase names or names containing characters that
would break inference.
```
@McCLANE
```

**Dual dialogue:** append `^` to the second character's line. The two dialogue blocks render
side by side.
```
BRICK
Screw retirement.

STEEL ^
Screw retirement.
```
Whitespace before `^` is permitted and ignored.

### Dialogue

Any text on the line immediately following a Character line or a Parenthetical. Continues until
a blank line.

To include a blank line *within* dialogue, the blank line must contain at least one space —
this is the standard workaround and parsers must honor it.

### Parenthetical

A line wrapped in parentheses, immediately following a Character line or a Dialogue line
(no blank line between).

```
STEEL
(starting the engine)
So much for retirement!
```

### Lyrics

Lines prefixed with `~`. Each line is its own lyric element; the `~` is not rendered.

```
~Willy Wonka! Willy Wonka! The amazing chocolatier!
```

Lyrics may appear in dialogue or as standalone blocks.

### Transition

A line in all uppercase ending in `TO:`, preceded and followed by blank lines.

```
CUT TO:
```

**Forced:** prefix with `>`, when the line doesn't end in `TO:` or isn't uppercase.
```
> Burn to White.
```
The `>` and any following whitespace are stripped. A line beginning with `>` and ending with `<`
is a centered text element, not a transition.

Adding a space after the colon (`CUT TO: `) makes the line parse as Action — a documented escape.

### Centered Text

Bracketed by `>` and `<`. Whitespace inside is trimmed.
```
> THE END <
```

### Page Break

A line containing three or more consecutive `=` characters and nothing else.
```
===
```

### Sections and Synopses

Structural/outlining elements. **Not rendered in output** — they exist for the writer's
navigation and are typically shown in an outline sidebar.

**Section:** one or more `#` at line start. Depth = number of `#`.
```
# Act I
## Sequence A
### Scene 1
```

**Synopsis:** a line beginning with `=` followed by a space (or immediately by text), where
it isn't a page break.
```
= Steel arrives at the pool, and Brick is already there.
```

The `=` synopsis marker conflicts with the `===` page break — disambiguate by requiring three
or more `=` with no other content for a page break.

### Notes

Inline, delimited by double square brackets. May span multiple lines. Not rendered.
```
[[Add an additional beat here]]
```
A note containing a blank line is still a single note. Notes can appear inside any element.

### Boneyard

Block comments, `/* ... */`. Everything between is omitted from output entirely, including
across element boundaries.

```
/*
INT. OLD SCENE - DAY

This whole scene is cut for now.
*/
```

### Emphasis

Markdown-derived, applied to inline text:

| Markup | Result |
|---|---|
| `*text*` | Italic |
| `**text**` | Bold |
| `***text***` | Bold italic |
| `_text_` | Underline |

Combinable and nestable: `_an *italicized* word within an underlined phrase_`.

Escape with backslash: `\*`. Asterisks or underscores surrounded by spaces on both sides are
treated as literal characters, not emphasis markers — `a * b` is literal.

Emphasis does not apply inside Boneyard or Notes.

### Indenting and whitespace

Leading whitespace in Action is preserved. Elsewhere it is generally trimmed. Tabs are treated
as equivalent to spaces for the purposes of element detection.

## Recommended parse order

Ambiguity resolution has a natural precedence. A practical implementation:

1. Strip Boneyards (`/* */`) — they can span everything else.
2. Extract and strip Notes (`[[ ]]`) if you want them out of the token stream, or tokenize
   them in place.
3. Extract the Title Page if the file begins with one of the recognised keys above. Any `Key:`
   line would read a script that opens `FADE IN:` as a title page.
4. Split the body into blocks on blank lines, retaining "blank lines with whitespace" as
   non-separators inside dialogue.
5. For each block, in order:
   - Page Break (`===`+)
   - Forced elements by leading character: `.` `!` `@` `>` `~` `=` `#`
   - Centered (`>` … `<`)
   - Scene Heading by prefix match
   - Transition (uppercase, ends `TO:`)
   - Character (uppercase, block has more than one line)
   - Parenthetical / Dialogue (position-dependent, only after Character)
   - Action (fallback)
6. Apply inline emphasis within each element's text.

The Character rule depends on the *next* line being non-blank, so single-pass streaming needs
one line of lookahead — or block-splitting first, as above.

## Pagination

Not part of the Fountain syntax spec. Renderers implement standard US screenplay layout:
12pt Courier, 1" margins (1.5" left), ~55 lines per page, with element-specific indentation
(dialogue ~2.5" from left, character ~3.7", parenthetical ~3.1", transitions right-aligned).
One page ≈ one minute of screen time. Drafts does not paginate (`appthere-drafts.md` §5.4); an
export that does should replicate this layout metric, and `fountain-mode`'s pagination logic is a
good reference for edge cases (widow/orphan handling, dialogue splitting with MORE/CONT'D).

## Ecosystem

| Project | Language | Notes |
|---|---|---|
| `Fountain` (nyousefi) | Objective-C | Original reference implementation, MIT |
| `fountain-mode` (rnkn) | Emacs Lisp | Full 1.1 support, accurate pagination, PDF export via troff |
| `fountain` (docs.rs) | Rust | Parser + formatter, nom-based |
| `afterwriting` | JavaScript | Browser-based renderer/PDF export |
| `screenplain` | Python | Fountain → PDF/HTML/FDX |
| `Fountain-PHP` | PHP | Port of the Objective-C library |
| Highland 2, Slugline, Fade In, Beat | Commercial editors | Various extensions beyond 1.1 |

Note that several editors have added non-standard extensions (Beat's `[[note types]]`,
Highland's `{{...}}` templating, revision marks). None are part of 1.1. If you're targeting
interop, stick to 1.1 and treat unknown constructs as Action.

## Implementation guidance

- The whole parser is a few hundred lines. Don't reach for a parser generator; hand-written
  line matching is clearer and faster.
- Retain source byte ranges per element for editor cursor mapping and incremental reparse.
- Represent the document as a list of elements, each carrying type, text, source range, and
  type-specific metadata (scene number, dual-dialogue flag, section depth).
- Blank-line-with-whitespace inside dialogue is the one rule that breaks naive `split("\n\n")`.
- Round-tripping is exact: Fountain is its own canonical serialization. Preserve the source
  verbatim for untouched regions and you get perfect fidelity for free — a genuine advantage
  over Markdown here.
- For syntax highlighting, colorize by element type rather than by marker character; writers
  expect the *whole* character name line highlighted, not just the `@`.

## Further reading

- Syntax: <https://fountain.io/syntax/>
- FAQ: <https://fountain.io/faq/>
- Apps and tools: <https://fountain.io/apps/>
- Reference implementation: <https://github.com/nyousefi/Fountain>
