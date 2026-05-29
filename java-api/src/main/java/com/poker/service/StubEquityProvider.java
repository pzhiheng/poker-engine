package com.poker.service;

import com.poker.domain.model.Card;
import com.poker.domain.model.Rank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Heuristic equity estimator used until the Go gRPC odds service is available.
 *
 * <p>This is intentionally simple — it looks at hole-card rank strength to produce
 * a rough estimate rather than doing full combinatorial analysis.  It will be
 * superseded by {@code GrpcEquityProvider} on Day 19.
 *
 * <p>Rule of thumb used:
 * <ul>
 *   <li>Pocket pair        → base 0.65 (premium pair) down to 0.52 (low pair)</li>
 *   <li>Broadway (T-A)     → 0.58 if suited, 0.54 otherwise</li>
 *   <li>One high card (A/K)→ 0.50</li>
 *   <li>Everything else    → 0.40</li>
 *   <li>Each extra opponent shaves ~8 % off the base estimate</li>
 * </ul>
 *
 * <p>The {@code @ConditionalOnMissingBean} means this bean is only registered if no
 * other {@link EquityProvider} (e.g. the real gRPC one) has been registered first.
 */
@Component
@ConditionalOnMissingBean(value = EquityProvider.class, ignored = StubEquityProvider.class)
public class StubEquityProvider implements EquityProvider {

    @Override
    public double calculateEquity(List<String> holeCards,
                                  List<String> boardCards,
                                  int          numOpponents) {
        if (holeCards == null || holeCards.size() < 2) return 0.5;

        Card c1 = Card.parse(holeCards.get(0));
        Card c2 = Card.parse(holeCards.get(1));

        double base;

        if (c1.rank() == c2.rank()) {
            // Pocket pair — scale with rank value (2=2, A=14)
            int rankVal = c1.rank().value();
            base = 0.52 + (rankVal - 2) * (0.15 / 12.0); // 0.52 (2s) … 0.67 (AA)
        } else {
            boolean suited   = c1.suit() == c2.suit();
            int     topRank  = Math.max(c1.rank().value(), c2.rank().value());
            boolean broadway = topRank >= Rank.TEN.value()
                            && Math.min(c1.rank().value(), c2.rank().value()) >= Rank.TEN.value();

            if (broadway) {
                base = suited ? 0.58 : 0.54;
            } else if (topRank >= Rank.ACE.value() || topRank >= Rank.KING.value()) {
                base = suited ? 0.52 : 0.48;
            } else {
                base = suited ? 0.44 : 0.40;
            }
        }

        // Simplified: equity against N opponents ≈ base ^ (1 + 0.4*(N-1))
        double multiway = Math.pow(base, 1.0 + 0.4 * (numOpponents - 1));

        return Math.min(0.98, Math.max(0.02, multiway));
    }
}
