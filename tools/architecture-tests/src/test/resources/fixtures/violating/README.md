Deliberately-violating Kotlin, used to prove the architecture assertions actually fire.

These files are under `resources/`, so they are never compiled and never ship. Konsist parses
Kotlin as text, which is what makes this possible: the fixtures can be as wrong as they need to be.

They are excluded from detekt by the `**/resources/**` exclusion in the `drafts.quality`
convention plugin -- without it, detekt would analyse them and fail the build on violations that
are the entire point of the files.
