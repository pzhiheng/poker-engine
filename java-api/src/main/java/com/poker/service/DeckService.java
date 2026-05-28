package com.poker.service;

import com.poker.domain.model.Card;
import com.poker.domain.model.Rank;
import com.poker.domain.model.Suit;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds and shuffles a standard 52-card deck.
 *
 * <p>Each call to {@link #shuffledDeck()} returns a freshly created, independently
 * shuffled {@code List<Card>} — the order is randomised using
 * {@link Collections#shuffle} (cryptographically adequate for a practice engine).
 */
@Service
public class DeckService {

    /**
     * Returns a new 52-card list in a random order.
     * The returned list is mutable so callers can {@code remove()} cards as they deal.
     */
    public List<Card> shuffledDeck() {
        List<Card> deck = new ArrayList<>(52);
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                deck.add(new Card(rank, suit));
            }
        }
        Collections.shuffle(deck);
        return deck;
    }
}
