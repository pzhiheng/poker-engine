package com.poker.service;

import com.poker.domain.entity.Player;
import com.poker.domain.repository.PlayerRepository;
import com.poker.security.JwtService;
import jakarta.annotation.PostConstruct;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Ensures a single bot player ({@code bot-easy}) exists in the database and
 * holds a valid JWT for it in memory so {@link BotActionService} can act on
 * its behalf without re-authenticating on every hand.
 *
 * <p>The bot is a normal {@link Player} row — no schema change required.
 * It is identified by the reserved username {@code bot-easy} and is seeded
 * idempotently on every application startup.
 */
@Service
public class BotPlayerService {

    static final String BOT_USERNAME = "bot-easy";
    private static final String BOT_PASSWORD = "bot-internal-password-not-used-externally";

    private final PlayerRepository playerRepo;
    private final PasswordEncoder  passwordEncoder;
    private final JwtService       jwtService;

    private UUID   botPlayerId;
    private String botToken;

    public BotPlayerService(PlayerRepository playerRepo,
                            PasswordEncoder  passwordEncoder,
                            JwtService       jwtService) {
        this.playerRepo      = playerRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService      = jwtService;
    }

    @PostConstruct
    @Transactional
    public void init() {
        Player bot = playerRepo.findByUsername(BOT_USERNAME).orElseGet(() -> {
            Player p = new Player(BOT_USERNAME, passwordEncoder.encode(BOT_PASSWORD), 100_000);
            return playerRepo.save(p);
        });
        botPlayerId = bot.getId();
        botToken    = jwtService.generateToken(botPlayerId, BOT_USERNAME);
    }

    public UUID   getBotPlayerId() { return botPlayerId; }
    public String getBotToken()    { return botToken; }
    public String getBotUsername() { return BOT_USERNAME; }
}
