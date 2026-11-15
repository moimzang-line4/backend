package com.moimzang.domain.file.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

// 실제 픽셀 변환이라 mock으로는 검증할 수 없어 바이트를 직접 만들어 넣고 결과를 다시 디코딩해 본다
class FileThumbnailGeneratorTest {

    private final FileThumbnailGenerator generator = new FileThumbnailGenerator();

    @Test
    void supports_이미지_확장자만_허용하고_webp는_제외한다() {
        assertThat(generator.supports("jpg")).isTrue();
        assertThat(generator.supports("jpeg")).isTrue();
        assertThat(generator.supports("png")).isTrue();
        assertThat(generator.supports("gif")).isTrue();
        // ImageIO에 디코더가 없어 대상에서 뺐다
        assertThat(generator.supports("webp")).isFalse();
        assertThat(generator.supports("pdf")).isFalse();
        assertThat(generator.supports("")).isFalse();
        assertThat(generator.supports(null)).isFalse();
    }

    @Test
    void supports_확장자_대문자도_인식한다() {
        assertThat(generator.supports("JPG")).isTrue();
        assertThat(generator.supports("PNG")).isTrue();
    }

    @Test
    void generate_긴_변을_512로_줄이고_비율을_유지한다() throws IOException {
        byte[] source = image(1600, 1200, "jpg", false);

        FileThumbnail thumbnail = generate(source, "jpg");

        BufferedImage decoded = decode(thumbnail);
        assertThat(decoded.getWidth()).isEqualTo(512);
        // 1600:1200 = 4:3 이므로 512의 3/4인 384가 된다
        assertThat(decoded.getHeight()).isEqualTo(384);
    }

    @Test
    void generate_세로가_더_길면_세로를_512로_맞춘다() throws IOException {
        byte[] source = image(600, 1800, "png", false);

        FileThumbnail thumbnail = generate(source, "png");

        BufferedImage decoded = decode(thumbnail);
        assertThat(decoded.getHeight()).isEqualTo(512);
        assertThat(decoded.getWidth()).isEqualTo(171);
    }

    @Test
    void generate_원본이_512보다_작으면_확대하지_않는다() throws IOException {
        byte[] source = image(200, 100, "jpg", false);

        FileThumbnail thumbnail = generate(source, "jpg");

        BufferedImage decoded = decode(thumbnail);
        assertThat(decoded.getWidth()).isEqualTo(200);
        assertThat(decoded.getHeight()).isEqualTo(100);
    }

    @Test
    void generate_jpg는_jpeg로_내보낸다() throws IOException {
        FileThumbnail thumbnail = generate(image(800, 800, "jpg", false), "jpg");

        assertThat(thumbnail.extension()).isEqualTo("jpg");
        assertThat(thumbnail.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void generate_png는_png로_내보내고_알파_채널을_유지한다() throws IOException {
        // 왼쪽 절반이 완전히 투명한 이미지
        byte[] source = image(800, 800, "png", true);

        FileThumbnail thumbnail = generate(source, "png");

        assertThat(thumbnail.extension()).isEqualTo("png");
        assertThat(thumbnail.contentType()).isEqualTo("image/png");
        BufferedImage decoded = decode(thumbnail);
        assertThat(decoded.getColorModel().hasAlpha()).isTrue();
        int topLeftAlpha = (decoded.getRGB(2, 2) >>> 24);
        assertThat(topLeftAlpha).isZero();
    }

    @Test
    void generate_gif는_png로_내보낸다() throws IOException {
        FileThumbnail thumbnail = generate(image(800, 600, "gif", false), "gif");

        assertThat(thumbnail.extension()).isEqualTo("png");
        assertThat(thumbnail.contentType()).isEqualTo("image/png");
    }

    @Test
    void generate_투명한_png를_줄여도_불투명_영역의_색이_검게_타지_않는다() throws IOException {
        BufferedImage decoded = decode(generate(image(600, 600, "png", true), "png"));

        // 불투명한 오른쪽 절반은 원래 색(빨강)이 남아 있어야 한다
        int right = decoded.getRGB(decoded.getWidth() - 3, 3);
        assertThat((right >>> 24)).isEqualTo(255);
        assertThat(right & 0x00FFFFFF).isEqualTo(Color.RED.getRGB() & 0x00FFFFFF);
    }

    @Test
    void generate_썸네일이_원본보다_작다() throws IOException {
        byte[] source = image(2000, 2000, "png", false);

        FileThumbnail thumbnail = generate(source, "png");

        assertThat(thumbnail.content().length).isLessThan(source.length);
    }

    @Test
    void generate_지원하지_않는_확장자면_빈_결과를_준다() throws IOException {
        byte[] source = image(800, 600, "png", false);

        assertThat(generator.generate(new ByteArrayInputStream(source), "webp")).isEmpty();
        assertThat(generator.generate(new ByteArrayInputStream(source), "pdf")).isEmpty();
    }

    @Test
    void generate_디코딩할_수_없는_바이트면_빈_결과를_준다() {
        byte[] broken = "이건 이미지가 아니다".getBytes();

        Optional<FileThumbnail> result = generator.generate(new ByteArrayInputStream(broken), "png");

        assertThat(result).isEmpty();
    }

    @Test
    void generate_빈_바이트면_빈_결과를_준다() {
        assertThat(generator.generate(new ByteArrayInputStream(new byte[0]), "jpg")).isEmpty();
    }

    @Test
    void exceedsPixelLimit_상한을_넘는_치수만_걸러낸다() {
        // 실제로 그 크기의 이미지를 만들면 테스트가 수백 MB를 쓰므로 판정만 따로 검증한다
        assertThat(FileThumbnailGenerator.exceedsPixelLimit(8000, 6000)).isTrue();
        assertThat(FileThumbnailGenerator.exceedsPixelLimit(4000, 3000)).isFalse();
        // int 곱셈이었다면 넘쳐서 음수가 돼 통과했을 조합
        assertThat(FileThumbnailGenerator.exceedsPixelLimit(70000, 70000)).isTrue();
    }

    private FileThumbnail generate(byte[] source, String extension) {
        return generator.generate(new ByteArrayInputStream(source), extension).orElseThrow();
    }

    private BufferedImage decode(FileThumbnail thumbnail) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(thumbnail.content()));
    }

    // withAlpha면 왼쪽 절반을 완전히 투명하게 비우고 오른쪽 절반만 빨갛게 칠한다
    private byte[] image(int width, int height, String format, boolean withAlpha) throws IOException {
        int type = withAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.RED);
        if (withAlpha) {
            graphics.fillRect(width / 2, 0, width - width / 2, height);
        } else {
            graphics.fillRect(0, 0, width, height);
        }
        graphics.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }
}
