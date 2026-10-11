# Spec Delta

## Purpose

Source: projects.md §2, §8 (`.gitignore`), §13.1–13.2. A new project can start from a suggested
structure that the writer is free to change at once; nothing in the app depends on it afterwards.

## ADDED Requirements

### Requirement: A new project starts from a template
Creating a new project SHALL ask for its name, where to put it, and a template: *Novel*,
*Screenplay*, *Screenplay + Treatment*, *Research paper*, or *Empty*. The project SHALL be created as
a new folder with that name, holding the template's folders, `.drafts/project.toml`, and a
`.gitignore`, and SHALL open in a project window.

#### Scenario: Novel
- **WHEN** the writer creates a project named *The Salt Road* from the *Novel* template
- **THEN** a folder *The Salt Road* exists holding `Manuscript/`, `Notes/`, `Characters/`, `Places/`, `.drafts/project.toml` and `.gitignore`, and it opens

### Requirement: Template folders are in the template's order
A template's `project.toml` SHALL order the project's top-level folders as the template lists them,
so they are not marked *Unsorted*.

#### Scenario: Screenplay order
- **WHEN** a project is created from *Screenplay*
- **THEN** the binder shows `Screenplay`, `Notes`, `Characters`, `Beats` in that order, none *Unsorted*

### Requirement: Templates ship a .gitignore
Every template SHALL write a `.gitignore` that ignores `.drafts/cache/` and `.drafts/trash/`.

#### Scenario: Empty template
- **WHEN** a project is created from *Empty*
- **THEN** it holds `.drafts/project.toml` and a `.gitignore` ignoring `.drafts/cache/` and `.drafts/trash/`, and nothing else

### Requirement: Creating a project never replaces or nests
Creating a project SHALL NOT replace or write into an existing file or folder, and SHALL NOT create a
project inside an existing project. In either case nothing SHALL be created and the writer SHALL be
told why.

Rationale: projects.md §13.2, nesting is unsupported.

#### Scenario: Name taken
- **WHEN** the writer creates a project named *Notes* where a folder *Notes* already exists
- **THEN** the existing folder is unchanged and the writer is asked for another name

#### Scenario: Inside an existing project
- **WHEN** the writer chooses a location inside a folder that has a `.drafts/` above it
- **THEN** no project is created and the writer is told the location is already inside a project

### Requirement: Nothing depends on a template after creation
After creation, the app SHALL NOT treat a project differently for having come from a template, nor
treat any of its folders as special by name.

#### Scenario: Template folder renamed
- **WHEN** the writer renames `Manuscript/` to `Draft/` in a *Novel* project
- **THEN** the project behaves exactly as before, with `Draft/` in `Manuscript/`'s place
