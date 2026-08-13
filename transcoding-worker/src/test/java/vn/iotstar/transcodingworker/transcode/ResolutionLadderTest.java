package vn.iotstar.transcodingworker.transcode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResolutionLadderTest {

    @Test
    @DisplayName("nguồn 1080p giữ đủ 4 nấc, không upscale")
    void fullHdSourceKeepsAllFourRungs() {
        List<Rung> ladder = ResolutionLadder.buildFor(1920, 1080);

        assertThat(ladder).extracting(Rung::label).containsExactly("1080p", "720p", "480p", "360p");
        assertThat(ladder).allSatisfy(r -> assertThat(r.height()).isLessThanOrEqualTo(1080));
    }

    @Test
    @DisplayName("nguồn 720p bỏ nấc 1080p — không upscale")
    void hdSourceDropsHigherRungs() {
        List<Rung> ladder = ResolutionLadder.buildFor(1280, 720);

        assertThat(ladder).extracting(Rung::label).containsExactly("720p", "480p", "360p");
    }

    @Test
    @DisplayName("nguồn thấp hơn 360p vẫn ra đúng một nấc bằng kích thước nguồn, không bỏ transcode")
    void belowLowestRungStillProducesOneRung() {
        List<Rung> ladder = ResolutionLadder.buildFor(426, 240);

        assertThat(ladder).hasSize(1);
        assertThat(ladder.get(0).width()).isEqualTo(426);
        assertThat(ladder.get(0).height()).isEqualTo(240);
    }

    @Test
    @DisplayName("kích thước nguồn lẻ được làm tròn xuống số chẵn (yêu cầu của H.264)")
    void oddSourceDimensionsRoundedDownToEven() {
        List<Rung> ladder = ResolutionLadder.buildFor(321, 241);

        assertThat(ladder.get(0).width()).isEqualTo(320);
        assertThat(ladder.get(0).height()).isEqualTo(240);
    }
}
