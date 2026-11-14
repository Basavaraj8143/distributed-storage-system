package com.byteharvest.master.placement;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Picks any candidate at random, which spreads chunks evenly over time. This is the default.
 *
 * <p>Selected by {@code storage.placement-strategy=random}, or when the property is not set. A new strategy
 * is added as another {@code @Component} with its own {@code havingValue}, and nothing else has to change.
 */
@Component
@ConditionalOnProperty(name = "storage.placement-strategy", havingValue = "random", matchIfMissing = true)
public class RandomPlacementStrategy implements PlacementStrategy {

    @Override
    public Optional<String> chooseNext(List<String> candidates, List<String> currentHolders) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())));
    }
}
