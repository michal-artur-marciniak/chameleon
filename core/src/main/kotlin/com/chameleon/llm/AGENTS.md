# LLM Context Conventions

## Model Reference Resolution

- `ModelRefResolver` lives in core and stays side-effect free; it returns a `ModelRefResolutionResult` with typed errors instead of throwing.
- Callers are responsible for deciding how to surface errors (log, exception, etc.).

## Provider Repository Port

- Infrastructure registries implement `LlmProviderRepositoryPort`.
- `list()` returns the configured provider IDs used for validation and default selection.

## Model Ref Format

- Preferred format is `provider/model`.
- If exactly one provider is configured, bare model IDs are allowed.
- Unknown providers return `ModelRefResolutionError.UnknownProvider` with the configured set.
