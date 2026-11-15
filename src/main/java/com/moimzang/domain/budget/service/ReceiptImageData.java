package com.moimzang.domain.budget.service;

// 영수증 이미지 1장을 엑셀에 심기 위해 읽어온 결과. bytes가 null이면 로딩에 실패했다는 뜻이고
// (스토리지에 파일이 없음, POI가 지원하지 않는 포맷 등) 호출하는 쪽은 그림 대신 안내 문구를 넣는다
record ReceiptImageData(byte[] bytes, int pictureType, int widthPx, int heightPx) {

    static ReceiptImageData failed() {
        return new ReceiptImageData(null, -1, 0, 0);
    }

    boolean isFailed() {
        return bytes == null;
    }
}
