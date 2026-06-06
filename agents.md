# Project Guidance

## Project Goal

Build a Java game application that can run on both PC and Android.

The codebase should be structured so that game logic is shared across platforms, while platform-specific concerns such as input, display, audio, storage, packaging, and lifecycle handling are isolated behind clear interfaces.

Use Gradle as the build system. Prefer a multi-module Gradle project with shared game code in a core module and separate launcher/platform modules for desktop and Android.

## Java Coding Practices

- Prefer clear, small classes with a single responsibility.
- Keep game logic deterministic where practical, especially for simulation, rules, collision, scoring, and state transitions.
- Separate core game logic from rendering, input, audio, persistence, and platform APIs.
- Favor composition over inheritance unless inheritance is a natural fit for the domain.
- Use immutable value objects where possible, especially for positions, dimensions, configuration, and events.
- Make invalid states difficult to represent through constructors, factory methods, validation, and strong types.
- Use descriptive names for classes, methods, variables, and packages.
- Keep methods short and focused. Extract helper methods when a method begins mixing multiple concepts.
- Avoid global mutable state. Pass dependencies explicitly or use dependency injection where appropriate.
- Prefer interfaces for platform boundaries and external services.
- Treat warnings seriously and keep the project clean under the configured compiler and static-analysis tools.
- Write tests for core game rules, state transitions, serialization, and other logic that should behave identically on PC and Android.
- Keep platform-specific code thin and easy to replace.
- Use standard Java APIs and well-maintained libraries before creating custom infrastructure.

## Error Handling and Result Values

Use a Result-style monad, or the closest practical Java equivalent, as much as possible for operations that can fail without requiring immediate exception-based control flow.

Examples of operations that should usually return a `Result<T, E>`-style value:

- Loading assets or configuration.
- Parsing saved game data.
- Validating player input or level definitions.
- Calling platform services.
- Performing persistence operations.
- Initializing subsystems that may be unavailable on one platform.

Guidelines:

- Prefer explicit success/failure return values over returning `null`.
- Avoid using exceptions for ordinary, expected failure paths.
- Reserve exceptions for truly exceptional conditions, programmer errors, or integration points that require them.
- Convert exceptions from external APIs into Result values near the boundary of the system.
- Keep error types meaningful. Prefer domain-specific error records, enums, or sealed interfaces over generic strings when practical.
- Chain Result operations with `map`, `flatMap`, `recover`, or equivalent helpers to keep fallible workflows readable.
- Do not force Result usage where it makes code harder to understand, such as simple pure calculations that cannot fail.

If no Result library has been chosen yet, prefer a small, well-tested local abstraction or a mature Java library that supports typed success and failure values.

## Cross-Platform Structure

- Keep shared game code independent from Android framework classes.
- Place Android-specific implementation details in Android modules or packages.
- Place desktop-specific implementation details in desktop modules or packages.
- Use Gradle modules to separate shared code from platform code.
- Define shared interfaces for platform services such as file access, input, audio, display settings, and logging.
- Keep rendering abstractions compatible with the selected game framework.
- Ensure build scripts support producing both a desktop runnable artifact and an Android application package.

## Development Priorities

- Correctness of core game behavior comes before visual polish.
- Shared code should be easy to test without launching Android or a desktop window.
- Platform code should delegate to shared game systems wherever possible.
- Favor maintainable, readable code over clever shortcuts.
