# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot 3.4 application using Java 21, PostgreSQL, and OAuth2 authentication. The project is called "mine" and is organized using a domain-driven architecture with separate domain and global packages.

## Build & Development Commands

### Building and Running
```bash
# Build the project (excluding tests)
./gradlew build -x test

# Build with tests
./gradlew build

# Run the application
./gradlew bootRun

# Clean build
./gradlew clean build
```

### Testing
```bash
# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests ClassName

# Run tests with specific pattern
./gradlew test --tests '*PatternName*'
```

### Code Quality & Formatting

The project uses multiple code quality tools that are enforced in CI:

```bash
# Run all static analysis checks (in order)
./gradlew spotbugsMain  # Bug pattern detection (null risks, infinite loops)
./gradlew pmdMain       # Code habit analysis (duplicate code, unused variables)
./gradlew checkstyleMain # Code style checking (indentation, naming, spacing)

# Format code automatically with Spotless
./gradlew spotlessApply

# Check formatting without applying
./gradlew spotlessCheck
```

**Important**: Always run `./gradlew spotlessApply` before committing to ensure code follows Google Java Format style.

## Architecture

### Package Structure

The codebase follows a domain-driven structure:

- `com.imyme.mine.domain.*` - Domain-specific business logic modules:
  - `auth` - Authentication and authorization
  - `user` - User management
  - `card` - Card entities and logic
  - `category` - Category management
  - `keyword` - Keyword handling
  - `storage` - Storage functionality
  - `device` - Device management
  - `attempt` - Attempt tracking

- `com.imyme.mine.global.*` - Cross-cutting concerns and shared utilities:
  - `config` - Spring configuration classes
  - `error` - Global error handling
  - `common` - Common utilities and base classes
  - `util` - Utility classes

### Technology Stack

- **Framework**: Spring Boot 3.4.12
- **Java Version**: 21 (using toolchain)
- **Database**: PostgreSQL (JPA with Hibernate)
- **Security**: Spring Security with OAuth2 Client
- **Build Tool**: Gradle 8.x
- **Code Quality**: Spotless, SpotBugs, PMD, Checkstyle

### Configuration

Environment variables required for database connection:
- `DB_URL` - PostgreSQL database URL
- `DB_USERNAME` - Database username
- `DB_PASSWORD` - Database password

JPA is configured with:
- DDL auto: update
- SQL logging: enabled
- Format SQL: enabled
- Open-in-view: disabled

## CI/CD Pipeline

The project uses GitHub Actions for CI on `main`, `develop`, and `infra/cicd` branches.

**CI Pipeline order**:
1. SpotBugs analysis
2. PMD analysis
3. Checkstyle checks
4. Build (without tests)
5. Run tests
6. Upload artifacts (main branch only)
7. Discord notification on failure

## Pull Request Guidelines

PRs should include:
- Description of purpose and changes
- Reference to related issues (Resolves #XXX)
- List of changes made
- Screenshots/video for UI changes
- Testing details and environment
- Checklist confirmation:
  - Code compiles successfully
  - All tests pass
  - Documentation updated
  - Code style guidelines followed
  - Code reviewer assigned

## Coding Standards

- **Formatting**: Google Java Format (enforced by Spotless)
- **Import Order**: java, javax, blank line, static imports
- **Indentation**: 4 spaces
- **Line Endings**: LF with newline at end of file
- **Lombok**: Used for reducing boilerplate (configured with annotation processor)

## Common Development Workflow

1. Create feature branch from `main`
2. Make code changes
3. Run `./gradlew spotlessApply` to format code
4. Run static analysis: `./gradlew spotbugsMain pmdMain checkstyleMain`
5. Run tests: `./gradlew test`
6. Commit changes (code will be checked by CI)
7. Create PR with proper description and checklist
