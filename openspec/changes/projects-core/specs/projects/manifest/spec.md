# Spec Delta

## ADDED Requirements

### Requirement: A project from a later version is read-only
A `project.toml` whose `schema` is greater than the one this version writes SHALL be read for what
it can be and SHALL NOT be written, even if every key in it is known. Actions that would write it
SHALL be refused, and the writer SHALL be told the project was saved by a newer version of the app.

Rationale: an audit finding recorded for this change; a later schema may change what known keys mean.

#### Scenario: Schema 2 with only known keys
- **WHEN** a project whose `project.toml` says `schema = 2` and holds only keys this version knows is opened, and the writer reorders a folder
- **THEN** `project.toml` is unchanged and the writer is told the project was saved by a newer version
