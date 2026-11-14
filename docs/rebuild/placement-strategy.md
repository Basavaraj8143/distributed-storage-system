# Placement Strategy

Which storage node receives a new copy of a chunk is decided by a `PlacementStrategy`, not by code inside the upload or repair services. Only random placement exists today. The seam exists so that other rules, such as rack-aware placement, can be added without editing the services.

Source: `master-service/src/main/java/com/byteharvest/master/placement/`

## 1. The interface

```java
public interface PlacementStrategy {
    Optional<String> chooseNext(List<String> candidates, List<String> currentHolders);
}
```

| Argument | Meaning |
| --- | --- |
| `candidates` | Nodes that may receive the copy: active, not already holding the chunk, not already tried and failed |
| `currentHolders` | Nodes that already hold (or were just given) a copy. Empty for the first copy of a new chunk |
| Return | One of `candidates`, or empty if none are left |

The strategy is asked for one node at a time. If a send to that node fails, the caller removes it from `candidates` and asks again. Strategies therefore never handle retries.

## 2. Who uses it

| Caller | When |
| --- | --- |
| `UploadService` | Each copy of each new chunk |
| `ChunkRepairService.repairUnderReplicatedChunks` | Each copy added to a chunk that lost replicas |

Both go through the same strategy, so a placement rule applies to uploads and to repair.

## 3. Current implementation

`RandomPlacementStrategy` picks any candidate at random. It is the default.

Two small behaviour changes compared with before:

- **Upload:** same result as before. Nodes were shuffled and taken in order, which is equivalent to picking randomly each time.
- **Repair:** previously took the first free node in the set's order, which tended to fill the same nodes. It now picks randomly, which spreads repaired copies more evenly.

## 4. Selecting a strategy

`storage.placement-strategy` (optional, default `random`). Each implementation is a Spring `@Component` guarded by `@ConditionalOnProperty` with its own value, so exactly one is active.

## 5. Adding a strategy later (for example rack-aware)

1. Write `RackAwarePlacementStrategy implements PlacementStrategy`.
2. Annotate it with `@Component` and `@ConditionalOnProperty(name = "storage.placement-strategy", havingValue = "rack-aware")`.
3. Give it its own rack configuration (for example a node-to-rack map in its own properties class).
4. Set `storage.placement-strategy=rack-aware`.

The strategy's rule would be: among `candidates`, prefer a node whose rack differs from every rack in `currentHolders`; if none exists, fall back to any candidate.

No change to `UploadService`, `ChunkRepairService` or the interface is needed. That is the point of passing `currentHolders` now, even though random placement ignores it.

See `docs/planning/rack-aware-placement-plan.md` for the full plan.

## 6. Not included on purpose

- No rack configuration, no node metadata, no extra parameters. They would be guesses until a second strategy exists.
- No change to node selection for reads or for reconcile.

## 7. Tests

`RandomPlacementStrategyTest`: returns empty with no candidates, always returns a candidate, uses every candidate over many tries, and does not modify its inputs. `ChunkIntegrityTest` and `NodeReconcileTest` build the services with a `RandomPlacementStrategy`.
