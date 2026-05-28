package com.poker.service;

import com.poker.domain.model.Card;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DeckService}.
 */
class DeckServiceTest {

    private final DeckService service = new DeckService();

    @Test
    void shuffledDeck_contains52UniqueCards() {
        List<Card> deck = service.shuffledDeck();

        assertThat(deck).hasSize(52);
        assertThat(deck).doesNotHaveDuplicates();
    }

    @Test
    void shuffledDeck_containsAllRanksAndSuits() {
        List<Card> deck = service.shuffledDeck();

        Set<String> notations = deck.stream()
            .map(Card::toString)
            .collect(Collectors.toSet());

        // Spot-check a few well-known cards
        assertThat(notations).contains("Ah", "Kd", "Qc", "Js", "Tc", "2h", "9s");
        assertThat(notations).hasSize(52);
    }

    @Test
    void shuffledDeck_returnsNewInstanceEachCall() {
        List<Card> deck1 = service.shuffledDeck();
        List<Card> deck2 = service.shuffledDeck();

        // Two decks are different objects (independent mutations possible)
        assertThat(deck1).isNotSameAs(deck2);
    }

    @Test
    void shuffledDeck_returnsShuffledOrder_notAlwaysSorted() {
        // Statistically, a random 52-card list is almost never in the same order
        // as another. We draw 5 decks and assert at least one differs from the first.
        List<Card> first = service.shuffledDeck();
        boolean anyDifferent = false;
        for (int i = 0; i < 5; i++) {
            if (!first.equals(service.shuffledDeck())) {
                anyDifferent = true;
                break;
            }
        }
        assertThat(anyDifferent)
            .as("At least one of 5 shuffles should produce a different order")
            .isTrue();
    }
}
