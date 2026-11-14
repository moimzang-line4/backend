package com.moimzang.domain.settlement.service;

import com.moimzang.global.exception.CommonErrorCode;
import com.moimzang.global.exception.CustomException;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

// 영수증 인식(API-036) 이미지 검증. 용량은 서블릿 multipart 상한(application.yml)에만 맡기고
// 여기서는 존재 여부·확장자만 본다
@Component
public class ReceiptImageValidator {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    // 검증을 통과한 확장자를 돌려준다 — 저장 키 생성(ReceiptImageKeyGenerator)이 같은 확장자를
    // 다시 뽑지 않도록 호출하는 쪽(Service)에 그대로 넘긴다
    public String validate(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new CustomException(CommonErrorCode.BAD_REQUEST);
        }

        String extension = extractExtension(image.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new CustomException(CommonErrorCode.BAD_REQUEST);
        }
        return extension;
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            return "";
        }
        return originalFilename.substring(dot + 1).toLowerCase();
    }
}