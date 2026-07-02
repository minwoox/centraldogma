# Central Dogma Site

Documentation site built with Sphinx.

## Building the documentation

Sphinx builds are only executed on Linux (CI). Apple Silicon (aarch64) Macs are automatically skipped.

```bash
# Linux / Intel Mac
./gradlew :site:sphinx

# Output
site/build/site/index.html
```

## Constraints

### Sphinx does not build on Apple Silicon

`sphinx-binary` does not provide an `osx-aarch_64` binary, so the Sphinx build fails on Apple Silicon Macs.
- Tracking issue: https://github.com/trustin/sphinx-binary/issues/10
- The `skipSphinx` condition in `build.gradle` automatically skips the task on Apple Silicon.
- If a binary was previously cached under `~/.gradle/caches/sphinx-binary/`, the build will appear to succeed.
