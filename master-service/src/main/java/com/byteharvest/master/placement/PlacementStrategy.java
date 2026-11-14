package com.byteharvest.master.placement;

import java.util.List;
import java.util.Optional;

/**
 * Decides which storage node should receive the next copy of a chunk.
 *
 * <p>Callers ask one node at a time. If the chosen node then fails, the caller removes it from the candidates
 * and asks again, so a strategy never has to deal with retries.
 *
 * <p>Both upload (a new chunk, no holders yet) and repair (a chunk that lost copies) use it, so a placement
 * rule is written once and applies to both.
 *
 * <p>The arguments carry what a smarter strategy needs. For example, a rack-aware strategy would prefer a
 * candidate in a rack that none of {@code currentHolders} is in. Information a strategy needs beyond the node
 * URLs (such as each node's rack) is that strategy's own concern, for example through its own configuration.
 */
public interface PlacementStrategy {

    /**
     * @param candidates     nodes that may receive the copy: active, not already holding this chunk, and not
     *                       already tried and failed. Never contains anything from {@code currentHolders}.
     * @param currentHolders nodes that already hold, or have just been given, a copy of this chunk.
     *                       Empty for the first copy of a new chunk. Read-only.
     * @return the chosen node, which is always one of {@code candidates}, or empty if there are no candidates
     */
    Optional<String> chooseNext(List<String> candidates, List<String> currentHolders);
}
