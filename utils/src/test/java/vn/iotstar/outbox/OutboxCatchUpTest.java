package vn.iotstar.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxCatchUpTest {

    @Mock private OutboxDao dao;
    @Mock private OutboxRelayService relayService;

    private OutboxRecord record() {
        return new OutboxRecord(UUID.randomUUID(), "t.v1", "k", "{}", 1,
                Instant.now().plusSeconds(60));
    }

    @Test
    void khongCoRowMoCoiThiKhongGuiGiCa() {
        when(dao.claimBatch(anyInt())).thenReturn(List.of());

        int nhatDuoc = new OutboxCatchUp(dao, relayService, new OutboxProperties()).run();

        assertThat(nhatDuoc).isZero();
        verifyNoInteractions(relayService);
    }

    @Test
    void quetTiepLoSauKhiLoDayVaDungKhiLoVoi() {
        OutboxProperties properties = new OutboxProperties();
        properties.setBatchSize(3);
        List<OutboxRecord> loDay = IntStream.range(0, 3).mapToObj(i -> record()).toList();
        // Lô thứ hai chỉ có 1 row, ít hơn batchSize, nên vòng lặp dừng ngay — đúng 2 lần gọi DB.
        // Vòng lặp dừng ngay khi thấy lô có size < batchSize, nên chỉ có 2 lần gọi claimBatch.
        when(dao.claimBatch(3)).thenReturn(loDay, List.of(record()));

        int nhatDuoc = new OutboxCatchUp(dao, relayService, properties).run();

        assertThat(nhatDuoc).isEqualTo(4);
        verify(dao, times(2)).claimBatch(3);
        verify(relayService, times(4)).send(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void goiThangSendChuKhongGoiRelayDeKhongNhanViecHaiLan() {
        OutboxRecord row = record();
        when(dao.claimBatch(anyInt())).thenReturn(List.of(row));

        new OutboxCatchUp(dao, relayService, new OutboxProperties()).run();

        verify(relayService).send(row);
        verify(relayService, org.mockito.Mockito.never())
                .relay(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void dungKhiThayLoCoSizeBangBatchSize() {
        OutboxProperties properties = new OutboxProperties();
        properties.setBatchSize(3);
        List<OutboxRecord> loDay = IntStream.range(0, 3).mapToObj(i -> record()).toList();
        // Lô đầu tiên đầy (size = batchSize), lô thứ hai rỗng, vòng lặp dừng ngay.
        when(dao.claimBatch(3)).thenReturn(loDay, List.of());

        int nhatDuoc = new OutboxCatchUp(dao, relayService, properties).run();

        assertThat(nhatDuoc).isEqualTo(3);
        verify(dao, times(2)).claimBatch(3);
        verify(relayService, times(3)).send(org.mockito.ArgumentMatchers.any());
    }
}
