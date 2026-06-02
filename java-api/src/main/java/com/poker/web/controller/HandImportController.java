package com.poker.web.controller;

import com.poker.domain.model.HandSource;
import com.poker.exception.ResourceNotFoundException;
import com.poker.domain.repository.HandImportRepository;
import com.poker.service.HandImportService;
import com.poker.web.dto.HandImportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Hand history import endpoints.
 *
 * <ul>
 *   <li>{@code POST /import/hands}            — upload a hand-history file</li>
 *   <li>{@code GET  /import/hands}            — list the caller's import jobs</li>
 *   <li>{@code GET  /import/hands/{importId}} — poll a specific import job</li>
 * </ul>
 *
 * <p>All endpoints require a valid JWT ({@code ROLE_PLAYER}).  The uploaded
 * username in the hand-history file must match the authenticated player's
 * username exactly (case-sensitive) for hands to be attributed correctly.
 */
@Tag(name = "Import", description = "Hand history import — upload PokerStars or GGPoker .txt files to seed long-term stats")
@RestController
@RequestMapping("/import/hands")
public class HandImportController {

    private final HandImportService    importService;
    private final HandImportRepository importRepo;

    public HandImportController(HandImportService    importService,
                                HandImportRepository importRepo) {
        this.importService = importService;
        this.importRepo    = importRepo;
    }

    @Operation(summary = "Import a hand-history file",
               description = """
                   Uploads and synchronously parses a PokerStars or GGPoker `.txt` hand-history file.
                   Hands whose hero username matches the authenticated player's username are persisted
                   and included in future stats computation. **Requires JWT.**
                   """)
    @ApiResponse(responseCode = "201", description = "File parsed — import summary returned")
    @ApiResponse(responseCode = "400", description = "File is empty or source is unrecognised")
    @ApiResponse(responseCode = "422", description = "Parse error (unrecognised hand-history format)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public HandImportResponse importHands(
            @Parameter(description = "Hand-history .txt file") @RequestParam("file") MultipartFile file,
            @Parameter(description = "Source site: POKERSTARS or GGPOKER")  @RequestParam("source") HandSource source,
            @AuthenticationPrincipal UUID playerId) {
        return importService.importHands(playerId, source, file);
    }

    @Operation(summary = "List import jobs",
               description = "Returns all hand-history import jobs submitted by the authenticated player, most recent first. **Requires JWT.**")
    @ApiResponse(responseCode = "200", description = "Import job list (may be empty)")
    @GetMapping
    public List<HandImportResponse> listImports(@AuthenticationPrincipal UUID playerId) {
        return importRepo.findByPlayerIdOrderByImportedAtDesc(playerId)
            .stream()
            .map(HandImportResponse::from)
            .toList();
    }

    @Operation(summary = "Get import job",
               description = "Polls a specific import job by ID. **Requires JWT.**")
    @ApiResponse(responseCode = "200", description = "Import job found")
    @ApiResponse(responseCode = "404", description = "Import job not found")
    @GetMapping("/{importId}")
    public HandImportResponse getImport(
            @Parameter(description = "Import job UUID") @PathVariable UUID importId) {
        return importRepo.findById(importId)
            .map(HandImportResponse::from)
            .orElseThrow(() -> new ResourceNotFoundException("HandImport", importId));
    }
}
