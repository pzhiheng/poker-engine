package com.poker.domain.model;

/**
 * Origin of a {@link com.poker.domain.entity.Hand}.
 *
 * <ul>
 *   <li>{@link #SYSTEM}     — played live on this platform</li>
 *   <li>{@link #POKERSTARS} — imported from a PokerStars hand-history file</li>
 *   <li>{@link #GGPOKER}    — imported from a GGPoker hand-history file</li>
 * </ul>
 */
public enum HandSource {
    SYSTEM,
    POKERSTARS,
    GGPOKER
}
