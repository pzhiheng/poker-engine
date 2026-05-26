package com.poker.service;

import com.poker.domain.entity.Player;
import com.poker.domain.entity.PokerTable;
import com.poker.domain.entity.TableSeat;
import com.poker.domain.model.TableStatus;
import com.poker.domain.repository.PlayerRepository;
import com.poker.domain.repository.PokerTableRepository;
import com.poker.domain.repository.TableSeatRepository;
import com.poker.exception.BusinessRuleException;
import com.poker.exception.ResourceNotFoundException;
import com.poker.web.dto.CreateTableRequest;
import com.poker.web.dto.JoinSeatRequest;
import com.poker.web.dto.SeatResponse;
import com.poker.web.dto.TableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TableService} — all dependencies are mocked, so these
 * run without a Spring context or database.
 */
@ExtendWith(MockitoExtension.class)
class TableServiceTest {

    @Mock PokerTableRepository tableRepo;
    @Mock TableSeatRepository  seatRepo;
    @Mock PlayerRepository     playerRepo;

    @InjectMocks TableService service;

    // ── Test fixtures ─────────────────────────────────────────────────────────

    private PokerTable savedTable;
    private Player     player;
    private UUID       tableId;
    private UUID       playerId;

    @BeforeEach
    void setUp() throws Exception {
        tableId  = UUID.randomUUID();
        playerId = UUID.randomUUID();

        savedTable = new PokerTable("Main Table", 5, 10);
        // Inject the generated ID via reflection (JPA assigns it on persist)
        var idField = PokerTable.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(savedTable, tableId);

        player = new Player("alice", "hash", 1_000);
        var pIdField = Player.class.getDeclaredField("id");
        pIdField.setAccessible(true);
        pIdField.set(player, playerId);
    }

    // ── createTable ───────────────────────────────────────────────────────────

    @Test
    void createTable_savesAndReturnsResponse() {
        when(tableRepo.existsByName("Main Table")).thenReturn(false);
        when(tableRepo.save(any(PokerTable.class))).thenReturn(savedTable);

        TableResponse resp = service.createTable(
            new CreateTableRequest("Main Table", 5, 10));

        assertThat(resp.name()).isEqualTo("Main Table");
        assertThat(resp.smallBlind()).isEqualTo(5);
        assertThat(resp.bigBlind()).isEqualTo(10);
        assertThat(resp.status()).isEqualTo(TableStatus.WAITING);
        verify(tableRepo).save(any(PokerTable.class));
    }

    @Test
    void createTable_rejectsInvalidBlinds() {
        // bigBlind != 2 * smallBlind → entity constructor throws
        assertThatIllegalArgumentException()
            .isThrownBy(() -> service.createTable(
                new CreateTableRequest("Bad Table", 5, 11)));
    }

    @Test
    void createTable_rejectsDuplicateName() {
        when(tableRepo.existsByName("Main Table")).thenReturn(true);

        assertThatExceptionOfType(BusinessRuleException.class)
            .isThrownBy(() -> service.createTable(
                new CreateTableRequest("Main Table", 5, 10)))
            .withMessageContaining("Main Table");
    }

    // ── joinSeat ──────────────────────────────────────────────────────────────

    @Test
    void joinSeat_happyPath_deductsBankrollAndCreatesSeat() throws Exception {
        when(tableRepo.findById(tableId)).thenReturn(Optional.of(savedTable));
        when(seatRepo.findByTableIdAndSeatNo(tableId, 1)).thenReturn(Optional.empty());
        when(playerRepo.findById(playerId)).thenReturn(Optional.of(player));
        when(seatRepo.findByTableIdAndPlayerId(tableId, playerId)).thenReturn(Optional.empty());

        // Capture the seat saved to repo and inject an id before returning it
        ArgumentCaptor<TableSeat> seatCaptor = ArgumentCaptor.forClass(TableSeat.class);
        when(seatRepo.save(seatCaptor.capture())).thenAnswer(inv -> {
            TableSeat s = seatCaptor.getValue();
            var field = TableSeat.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(s, UUID.randomUUID());
            return s;
        });
        when(playerRepo.save(any(Player.class))).thenReturn(player);

        SeatResponse resp = service.joinSeat(tableId, new JoinSeatRequest(playerId, 1, 300));

        assertThat(resp.seatNo()).isEqualTo(1);
        assertThat(resp.stackChips()).isEqualTo(300);
        assertThat(resp.playerId()).isEqualTo(playerId);
        assertThat(player.getBankrollChips()).isEqualTo(700); // 1000 - 300
    }

    @Test
    void joinSeat_tableNotFound_throws404() {
        when(tableRepo.findById(tableId)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ResourceNotFoundException.class)
            .isThrownBy(() -> service.joinSeat(
                tableId, new JoinSeatRequest(playerId, 1, 300)));
    }

    @Test
    void joinSeat_tableNotWaiting_throws422() {
        savedTable.setStatus(TableStatus.IN_HAND);
        when(tableRepo.findById(tableId)).thenReturn(Optional.of(savedTable));

        assertThatExceptionOfType(BusinessRuleException.class)
            .isThrownBy(() -> service.joinSeat(
                tableId, new JoinSeatRequest(playerId, 1, 300)))
            .extracting(BusinessRuleException::getCode)
            .isEqualTo("TABLE_NOT_OPEN");
    }

    @Test
    void joinSeat_seatAlreadyOccupied_throws422() throws Exception {
        TableSeat occupied = new TableSeat(savedTable, player, 1, 500);
        when(tableRepo.findById(tableId)).thenReturn(Optional.of(savedTable));
        when(seatRepo.findByTableIdAndSeatNo(tableId, 1)).thenReturn(Optional.of(occupied));

        assertThatExceptionOfType(BusinessRuleException.class)
            .isThrownBy(() -> service.joinSeat(
                tableId, new JoinSeatRequest(playerId, 1, 300)))
            .extracting(BusinessRuleException::getCode)
            .isEqualTo("SEAT_OCCUPIED");
    }

    @Test
    void joinSeat_playerNotFound_throws404() {
        when(tableRepo.findById(tableId)).thenReturn(Optional.of(savedTable));
        when(seatRepo.findByTableIdAndSeatNo(tableId, 1)).thenReturn(Optional.empty());
        when(playerRepo.findById(playerId)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ResourceNotFoundException.class)
            .isThrownBy(() -> service.joinSeat(
                tableId, new JoinSeatRequest(playerId, 1, 300)));
    }

    @Test
    void joinSeat_playerAlreadySeated_throws422() throws Exception {
        TableSeat existingSeat = new TableSeat(savedTable, player, 2, 400);
        when(tableRepo.findById(tableId)).thenReturn(Optional.of(savedTable));
        when(seatRepo.findByTableIdAndSeatNo(tableId, 1)).thenReturn(Optional.empty());
        when(playerRepo.findById(playerId)).thenReturn(Optional.of(player));
        when(seatRepo.findByTableIdAndPlayerId(tableId, playerId))
            .thenReturn(Optional.of(existingSeat));

        assertThatExceptionOfType(BusinessRuleException.class)
            .isThrownBy(() -> service.joinSeat(
                tableId, new JoinSeatRequest(playerId, 1, 300)))
            .extracting(BusinessRuleException::getCode)
            .isEqualTo("ALREADY_SEATED");
    }

    @Test
    void joinSeat_insufficientBankroll_throws422() {
        player.setBankrollChips(200);
        when(tableRepo.findById(tableId)).thenReturn(Optional.of(savedTable));
        when(seatRepo.findByTableIdAndSeatNo(tableId, 1)).thenReturn(Optional.empty());
        when(playerRepo.findById(playerId)).thenReturn(Optional.of(player));
        when(seatRepo.findByTableIdAndPlayerId(tableId, playerId)).thenReturn(Optional.empty());

        assertThatExceptionOfType(BusinessRuleException.class)
            .isThrownBy(() -> service.joinSeat(
                tableId, new JoinSeatRequest(playerId, 1, 300)))
            .extracting(BusinessRuleException::getCode)
            .isEqualTo("INSUFFICIENT_BANKROLL");
    }
}
