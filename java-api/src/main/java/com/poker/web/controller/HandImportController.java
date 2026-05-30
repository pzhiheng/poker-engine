package com.poker.web.controller;

import com.poker.domain.model.HandSource;
import com.poker.exception.ResourceNotFoundException;
import com.poker.domain.repository.HandImportRepository;
import com.poker.service.HandImportService;
import com.poker.web.dto.HandImportResponse;
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

    /**
     * Uploads and synchronously processes a hand-history file.
     *
     * @param file      the hand-history {@code .txt} file (multipart)
     * @param source    {@code POKERSTARS} or {@code GGPOKER}
     * @param playerId  injected from JWT — identifies the uploading player
     * @return 201 with import summary
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public HandImportResponse importHands(
            @RequestParam("file")   MultipartFile file,
            @RequestParam("source") HandSource    source,
            @AuthenticationPrincipal UUID         playerId) {

        return importService.importHands(playerId, source, file);
    }

    /**
     * Lists all import jobs submitted by the authenticated player, most
     * recent first.
     *
     * @param playerId  injected from JWT
     * @return 200 with list (may be empty)
     */
    @GetMapping
    public List<HandImportResponse> listImports(@AuthenticationPrincipal UUID playerId) {
        return importRepo.findByPlayerIdOrderByImportedAtDesc(playerId)
            .stream()
            .map(HandImportResponse::from)
            .toList();
    }

    /**
     * Returns a single import job by ID.
     *
     * @param importId  UUID of the import job
     * @return 200 with the job; 404 if not found
     */
    @GetMapping("/{importId}")
    public HandImportResponse getImport(@PathVariable UUID importId) {
        return importRepo.findById(importId)
            .map(HandImportResponse::from)
            .orElseThrow(() -> new ResourceNotFoundException("HandImport", importId));
    }
}
