package com.moimzang.domain.file.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 자료실 활동사진 그리드에 쓸 썸네일을 만든다.
 *
 * JDK ImageIO만 쓴다 — 이미지 처리 라이브러리를 새로 들이지 않는 기조를 따른다.
 * 그래서 webp는 대상에서 빠진다(ImageIO에 디코더가 없다).
 *
 * 실패는 예외로 올리지 않고 빈 결과로 돌려준다. 썸네일은 그리드 표시를 위한 최적화일 뿐이라
 * 이것 때문에 업로드가 실패하면 안 되기 때문이다. 대신 사유는 전부 로그로 남긴다.
 */
@Slf4j
@Component
public class FileThumbnailGenerator {

    // 그리드 셀의 이미지 영역이 170x102이라 3배 DPR까지 덮는 크기로 잡았다
    static final int MAX_EDGE = 512;

    // 치수를 먼저 읽어 이 한도를 넘으면 디코딩 자체를 포기한다. 20MB 고압축 JPEG가
    // 8000x6000으로 펼쳐지면 픽셀 배열만 수백 MB라 업로드 요청이 메모리를 터뜨린다
    static final long MAX_PIXELS = 40L * 1000 * 1000;

    private static final Set<String> JPEG_SOURCES = Set.of("jpg", "jpeg");
    // gif는 첫 프레임만 쓴다. 투명도가 살아 있어야 해서 png로 내보낸다
    private static final Set<String> PNG_SOURCES = Set.of("png", "gif");

    public boolean supports(String extension) {
        String normalized = normalize(extension);
        return JPEG_SOURCES.contains(normalized) || PNG_SOURCES.contains(normalized);
    }

    public Optional<FileThumbnail> generate(InputStream source, String extension) {
        String normalized = normalize(extension);
        if (!supports(normalized)) {
            return Optional.empty();
        }

        boolean png = PNG_SOURCES.contains(normalized);
        try (ImageInputStream input = ImageIO.createImageInputStream(source)) {
            if (input == null) {
                log.warn("[썸네일 생성] 이미지 스트림을 열지 못했습니다. extension = {}", extension);
                return Optional.empty();
            }
            return readAndResize(input, png);
        } catch (IOException e) {
            log.warn("[썸네일 생성] 입출력 오류로 생성하지 못했습니다. extension = {}", extension, e);
            return Optional.empty();
        }
    }

    private Optional<FileThumbnail> readAndResize(ImageInputStream input, boolean png) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) {
            log.warn("[썸네일 생성] 디코딩할 수 있는 리더가 없습니다.");
            return Optional.empty();
        }

        ImageReader reader = readers.next();
        try {
            reader.setInput(input);

            // 전체를 메모리에 펼치기 전에 치수부터 본다 — 이미지 폭탄 방어
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            if (exceedsPixelLimit(width, height)) {
                log.warn("[썸네일 생성] 이미지가 너무 커서 건너뜁니다. {}x{}", width, height);
                return Optional.empty();
            }

            BufferedImage original = reader.read(0);
            if (original == null) {
                log.warn("[썸네일 생성] 이미지를 디코딩하지 못했습니다.");
                return Optional.empty();
            }
            return Optional.of(encode(resize(original, png), png));
        } finally {
            reader.dispose();
        }
    }

    // int 곱셈이 넘치지 않도록 long으로 올려서 비교한다
    static boolean exceedsPixelLimit(int width, int height) {
        return (long) width * height > MAX_PIXELS;
    }

    private BufferedImage resize(BufferedImage original, boolean png) {
        int width = original.getWidth();
        int height = original.getHeight();
        int longest = Math.max(width, height);

        // 원본이 이미 작으면 확대하지 않는다
        double scale = longest <= MAX_EDGE ? 1.0 : (double) MAX_EDGE / longest;
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        // 한 번에 크게 줄이면 계단 현상이 생긴다. 목표의 2배에 닿을 때까지 절반씩 내려간다
        BufferedImage current = original;
        int currentWidth = width;
        int currentHeight = height;
        while (currentWidth / 2 > targetWidth && currentHeight / 2 > targetHeight) {
            currentWidth /= 2;
            currentHeight /= 2;
            current = draw(current, currentWidth, currentHeight, png);
        }

        // 마지막 한 번은 크기가 같아도 반드시 거친다 — 출력 포맷에 맞는 이미지 타입으로
        // 바꿔야 하기 때문이다(JPEG로 내보낼 때 알파 채널을 흰 배경으로 지우는 것이 여기서 일어난다)
        return draw(current, targetWidth, targetHeight, png);
    }

    private BufferedImage draw(BufferedImage source, int width, int height, boolean png) {
        // JPEG에는 알파 채널이 없다. ARGB를 그대로 넘기면 투명한 부분이 검게 타므로
        // 흰 배경을 먼저 깔고 그 위에 그린다
        int type = png ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage target = new BufferedImage(width, height, type);

        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            if (!png) {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, width, height);
            }
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private FileThumbnail encode(BufferedImage image, boolean png) throws IOException {
        String format = png ? "png" : "jpg";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, format, out)) {
            throw new IOException("썸네일을 인코딩할 writer가 없다: " + format);
        }
        return new FileThumbnail(out.toByteArray(), format, png ? "image/png" : "image/jpeg");
    }

    private String normalize(String extension) {
        return extension == null ? "" : extension.toLowerCase();
    }
}
