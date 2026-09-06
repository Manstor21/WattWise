# WattWise — Architecture Documentation

Technical architecture documentation for the WattWise electricity price optimizer.

---

## Documents

| Document | Description | Status |
|---|---|---|
| [architecture.md](./architecture.md) | System architecture: high-level diagram, module overview, data flows, architectural decisions, and NFRs | ✅ Block 1 |
| [modules.md](./modules.md) | Detailed module specification: purpose, stack, folder structure, endpoints/classes, connections, and tests | ✅ Block 1 |
| [data-model.md](./data-model.md) | Entity-relationship model: entities, fields, relationships, storage matrix, and migration strategy | ✅ Block 1 |
| [traffic-light-methodology.md](./traffic-light-methodology.md) | Hybrid price classification algorithm: percentiles, deviation, absolute thresholds, fusion logic, and configuration | ✅ Block 1 |

---

## How to Use These Documents

- **New to the project?** Start with [architecture.md](./architecture.md) for the executive summary and high-level diagram.
- **Implementing a module?** See [modules.md](./modules.md) for your module's folder structure and API contracts.
- **Designing database changes?** Reference [data-model.md](./data-model.md) for existing entities and relationships.
- **Working on the recommendation engine?** The classification logic is fully specified in [traffic-light-methodology.md](./traffic-light-methodology.md).

---

## Diagram Sources

All Mermaid diagrams in these documents are designed to render in:
- GitHub Markdown (native Mermaid support)
- VS Code with Mermaid extension
- Any Mermaid Live Editor ([mermaid.live](https://mermaid.live))

For the highest-quality rendering, open the `.md` files in a Mermaid-compatible viewer or export via the Mermaid CLI.

---

## Maintenance

These documents are living artifacts. When making significant architectural changes:

1. Update the relevant document(s) in this directory
2. If adding/changing an entity, update [data-model.md](./data-model.md)
3. If changing a module's structure or API, update [modules.md](./modules.md)
4. If the high-level architecture changes, update [architecture.md](./architecture.md)
5. Record the decision in `docs/adr/` as a new ADR
