# Laravelers Plugin for PhpStorm

A developer toolkit for Laravel applications in PhpStorm and IntelliJ Platform IDEs.

The plugin provides dedicated tools designed specifically for Laravel developers, starting with **Intelligent Routes File Search** with parameter wildcard matching (`{}`).

---

## 1. Routes File Search

A fast, interactive tool window panel for searching and navigating Laravel routes across your project.

### Wildcard Parameter Search (`{}`)
When searching for routes, you often know the path structure but not the exact parameter name used in the code.
- Type `/go/to/{}/edit`:
  - Automatically matches `/go/to/{id}/edit`, `/go/to/{user}/edit`, `/go/to/{post:slug}/edit`, `/go/to/{uuid?}/edit`, etc.
  - Matches whether routes in code have or omit the leading slash (`'go/to/...'` vs `'/go/to/...'`).
  - Supports multiple wildcards, e.g. `/orgs/{}/teams/{}/members`.

### Additional Search Capabilities
- **Named Variable Search**: `/users/{id}` specifically matches parameters containing `id` (e.g. `{id}`, `{userId}`, `{id?}`).
- **HTTP Method Prefix**: Prefix your search with any HTTP verb like `POST /users` or `DELETE /go/to/{}`.
- **Route Name Search**: Type route names directly, e.g. `users.index`, `admin.dashboard`.
- **Controller / Action Search**: Type controller or method names, e.g. `UserController` or `UserController@edit`.
- **Wildcard `*`**: Matches any characters across or within segments, e.g. `/api/*/edit`.

### Tool Window Features
- **Right Sidebar Panel**: Dockable "Laravel Routes" tool window.
- **Color-Coded Method Badges**: Visual badges for `GET` (green), `POST` (blue), `PUT`/`PATCH` (orange), `DELETE` (red), `ANY` (purple).
- **Interactive Filtering**: Filter by HTTP method dropdown (GET, POST, PUT, DELETE, etc.) and search scope (`routes/**/*.php` vs entire project).
- **Code Preview & Details**: Selected route displays full URI, route name, action, and code snippet with line numbers.
- **Jump to Function Button**: One-click navigation directly to the controller PHP file and method definition (e.g. `UserController::edit()`), or directly to the closure declaration.
- **Instant Source Navigation**: Jump to the route definition line in the route file (`routes/web.php`).
- **Context Menu & Shortcuts**: Right-click context menu, `Enter` to jump to source, `Shift+Enter` or `Cmd+B` / `Ctrl+B` to jump directly to the controller method.
- **Live File Watcher**: Automatically refreshes when route files are edited or added.

---

## Keyboard Shortcuts & Navigation

| Action | Shortcut (macOS / Win / Linux) | Location / Context |
| :--- | :--- | :--- |
| **Search Laravel Routes** | `Shift + Alt + R` | Global: **Navigate** → **Laravel Route...** |
| **Open Routes Tool Window** | `Ctrl + Alt + R` | Global: **Tools** → **Laravelers** → **Search Laravel Routes...** |
| **Jump to Function / Method** | `Shift + Enter` or `Cmd + B` / `Ctrl + B` | Inside Routes Panel (or click "Jump to Function") |
| **Jump to Route Source** | `Enter` or Double-click | Inside Routes Panel (or click "Jump to Source") |

*(If text is selected in the editor when triggering the shortcut, it automatically pre-populates the search query!)*

---

## Project Structure & Architecture

```
laravelers/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── src/
│   ├── main/
│   │   ├── resources/
│   │   │   ├── META-INF/
│   │   │   │   ├── plugin.xml
│   │   │   │   ├── pluginIcon.svg
│   │   │   │   └── pluginIcon_dark.svg
│   │   │   └── icons/
│   │   │       ├── laravelers.svg
│   │   │       └── laravelers_dark.svg
│   │   └── kotlin/com/github/todd/laravelers/
│   │       └── routesearch/
│   │           ├── model/
│   │           │   └── RouteItem.kt          # Route data model
│   │           ├── matcher/
│   │           │   └── RoutePatternMatcher.kt # Regex compiler & wildcard matcher
│   │           ├── parser/
│   │           │   └── LaravelRouteParser.kt  # Route definitions & prefix groups parser
│   │           ├── service/
│   │           │   └── LaravelRouteIndexService.kt # Project index service & file listener
│   │           ├── ui/
│   │           │   ├── LaravelRoutesPanel.kt   # Tool window UI panel & search bar
│   │           │   ├── RouteCellRenderer.kt   # Color-coded badge list renderer
│   │           │   └── LaravelRoutesToolWindowFactory.kt # Tool window factory
│   │           └── SearchRoutesAction.kt       # Shortcut & menu action
│   └── test/
│       └── kotlin/com/github/todd/laravelers/routesearch/
│           ├── RoutePatternMatcherTest.kt     # Unit tests for {} wildcard regex matching
│           ├── LaravelRouteParserTest.kt      # Unit tests for route definition parsing
│           └── RouteRankingTest.kt            # Unit tests for relevance scoring
```

### Adding More Laravel Tools Over Time
The repository is modularly structured so that new Laravel-specific tools can be added under `com.github.todd.laravelers.<tool-name>`:
- `com.github.todd.laravelers.routesearch` (current)
- `com.github.todd.laravelers.artisan` (future Artisan runner / generator)
- `com.github.todd.laravelers.models` (future Eloquent relation helper)
- `com.github.todd.laravelers.views` (future Blade view navigation)

---

## Development & Building

### Run Tests
```bash
./gradlew test
```

### Launch PhpStorm with Plugin Installed
```bash
./gradlew runIde
```

### Build Plugin Distribution (`.zip`)
```bash
./gradlew buildPlugin
```
The installable ZIP is created at:
```
build/distributions/laravelers-1.0.0.zip
```
Install it in PhpStorm via:
**Settings** → **Plugins** → ⚙️ (Gear Icon) → **Install Plugin from Disk...**
