package com.poker.web.controller;

import com.poker.domain.model.Challenge;
import com.poker.domain.model.ChallengeSet;
import com.poker.service.ChallengeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for poker training challenges.
 *
 * <p>All endpoints are public (no JWT required).
 */
@Tag(name = "Challenges", description = "Poker training scenarios with GTO explanations")
@RestController
@RequestMapping("/challenges")
public class ChallengeController {

    private final ChallengeService challengeService;

    public ChallengeController(ChallengeService challengeService) {
        this.challengeService = challengeService;
    }

    // ── GET /challenges ───────────────────────────────────────────────────────

    @Operation(summary = "List all challenges",
               description = "Returns all training scenarios without answers or explanations.")
    @GetMapping
    public List<ChallengeListItem> listChallenges() {
        return challengeService.listAll().stream()
            .map(ChallengeListItem::from)
            .toList();
    }

    // ── GET /challenges/{id} ──────────────────────────────────────────────────

    @Operation(summary = "Get a challenge",
               description = "Returns full scenario details (hole cards, board, options) but NOT the answer.")
    @GetMapping("/{id}")
    public ChallengeDetail getChallenge(@PathVariable String id) {
        return ChallengeDetail.from(challengeService.findById(id));
    }

    // ── POST /challenges/{id}/answer ──────────────────────────────────────────

    @Operation(summary = "Submit an answer",
               description = "Checks the chosen answer and returns the correct answer with full GTO explanation.")
    @PostMapping("/{id}/answer")
    public AnswerResponse submitAnswer(@PathVariable String id,
                                       @RequestBody AnswerRequest req) {
        Challenge c = challengeService.findById(id);
        boolean correct = req.chosenIndex() == c.correctIndex();
        return new AnswerResponse(correct, c.correctIndex(), c.options().get(c.correctIndex()),
                                  c.explanation(), c.keyPoints());
    }

    // ── GET /challenges/sets ──────────────────────────────────────────────────

    @Operation(summary = "List challenge sets",
               description = "Returns all themed quiz sets with their challenge count.")
    @GetMapping("/sets")
    public List<SetListItem> listSets() {
        return challengeService.listSets().stream()
            .map(s -> new SetListItem(s.id(), s.title(), s.description(), s.icon(),
                                      s.difficulty(), s.challengeIds().size()))
            .toList();
    }

    // ── GET /challenges/sets/{id} ─────────────────────────────────────────────

    @Operation(summary = "Get a challenge set with all its challenges",
               description = "Returns set metadata plus the full ordered list of challenges (no answers).")
    @GetMapping("/sets/{id}")
    public SetDetail getSet(@PathVariable String id) {
        ChallengeSet set = challengeService.findSetById(id);
        List<ChallengeDetail> challenges = set.challengeIds().stream()
            .map(cid -> ChallengeDetail.from(challengeService.findById(cid)))
            .toList();
        return new SetDetail(set.id(), set.title(), set.description(), set.icon(),
                             set.difficulty(), challenges);
    }

    // ── Nested DTOs ───────────────────────────────────────────────────────────

    /** Lightweight list item — no answer or explanation. */
    public record ChallengeListItem(
            String id, String title, String description,
            String position, String street, String difficulty) {
        static ChallengeListItem from(Challenge c) {
            return new ChallengeListItem(c.id(), c.title(), c.description(),
                                         c.position(), c.street(), c.difficulty());
        }
    }

    /** Full scenario detail — no answer. */
    public record ChallengeDetail(
            String id, String title, String description,
            String position, java.util.List<String> holeCards, java.util.List<String> board,
            String street, int potChips, int callAmount, int effectiveStack,
            java.util.List<String> options, String difficulty) {
        static ChallengeDetail from(Challenge c) {
            return new ChallengeDetail(c.id(), c.title(), c.description(), c.position(),
                                       c.holeCards(), c.board(), c.street(),
                                       c.potChips(), c.callAmount(), c.effectiveStack(),
                                       c.options(), c.difficulty());
        }
    }

    public record AnswerRequest(int chosenIndex) {}

    public record AnswerResponse(
            boolean correct, int correctIndex, String correctOption,
            String explanation, java.util.List<String> keyPoints) {}

    public record SetListItem(
            String id, String title, String description,
            String icon, String difficulty, int count) {}

    public record SetDetail(
            String id, String title, String description,
            String icon, String difficulty,
            java.util.List<ChallengeDetail> challenges) {}
}
