package com.byteharvest.master;

import com.byteharvest.master.placement.RandomPlacementStrategy;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomPlacementStrategyTest {

    private static final List<String> CANDIDATES = List.of("http://a:5001", "http://b:5002", "http://c:5003");

    private final RandomPlacementStrategy strategy = new RandomPlacementStrategy();

    @Test
    void returnsNothingWhenThereAreNoCandidates() {
        assertEquals(Optional.empty(), strategy.chooseNext(List.of(), List.of()));
    }

    @Test
    void alwaysReturnsOneOfTheCandidates() {
        for (int i = 0; i < 50; i++) {
            String chosen = strategy.chooseNext(CANDIDATES, List.of()).orElseThrow();
            assertTrue(CANDIDATES.contains(chosen));
        }
    }

    @Test
    void spreadsChoicesOverAllCandidates() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 300; i++) {
            seen.add(strategy.chooseNext(CANDIDATES, List.of()).orElseThrow());
        }

        assertEquals(Set.copyOf(CANDIDATES), seen, "every candidate should be picked at least once in 300 tries");
    }

    @Test
    void doesNotModifyTheListsItIsGiven() {
        List<String> holders = List.of("http://d:5004");

        strategy.chooseNext(CANDIDATES, holders);

        assertEquals(3, CANDIDATES.size());
        assertEquals(List.of("http://d:5004"), holders);
    }
}
