-- 자료실 활동사진 그리드에 쓸 썸네일 객체 키. 이미지가 아니거나 생성에 실패한 자료는 NULL로 남는다
ALTER TABLE file ADD COLUMN thumbnail_key VARCHAR(500) NULL AFTER storage_key;
