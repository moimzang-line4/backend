# 배포

`develop`에 머지하면 자동 배포된다. 서버에 접속해서 `git pull`이나 빌드를 할 일은 없다.

## 전체 흐름

```
 로컬
   │ git push → PR → develop 머지
   ▼
 GitHub Actions
   │  test-and-build 잡: 테스트 → bootJar → jar를 아티팩트로 업로드
   │  deploy 잡: jar를 내려받아 이미지에 COPY (Gradle을 돌리지 않아 수 초)
   │
   ├─[AWS 액세스 키]──▶ ECR            이미지 push (태그: 커밋 SHA)
   │
   └─[SSH]────────────▶ EC2            ① docker-compose.yml·nginx/nginx.conf scp
                                       ② .env / .env.app 생성
                                       ③ docker compose pull && up -d
                                       ④ /health 응답 확인 (최대 60초)

 EC2
   ├─[인스턴스 역할]──▶ ECR            이미지 pull
   ├─[인스턴스 역할]──▶ S3             파일 업로드·다운로드·삭제
   └─[VPC 사설망]─────▶ RDS            퍼블릭 액세스 off

 브라우저 ─▶ api.chungs.store ─[DNS]─▶ EC2 ─▶ nginx:443 ─▶ app:8080
```

## 이미지는 jar만 담는다

`Dockerfile`은 단일 스테이지이고 **미리 빌드된 `build/libs/*-SNAPSHOT.jar`를 `COPY`만 한다.**
이미지 안에서 Gradle을 돌리지 않는다.

GitHub 러너는 매 실행마다 새로 뜨므로 Docker 레이어 캐시가 없다. 이미지 안에서 빌드하면
배포마다 Gradle 배포판(~130MB)과 전체 의존성을 다시 받고 처음부터 컴파일해서 `docker build`
하나가 10분 가까이 걸린다. 그래서 빌드는 `test-and-build` 잡이 하고(테스트와 같은 잡이라
의존성 캐시를 공유한다) jar만 아티팩트로 `deploy` 잡에 넘긴다.

**로컬에서 이미지를 만들려면 jar가 먼저 있어야 한다:**

```bash
./gradlew bootJar
docker build -t moimzang-be:local .
```

`./gradlew bootJar` 없이 `docker build`만 하면 `COPY` 대상이 없어 실패한다.

## 서버에 소스가 없다

이미지 빌드를 Actions에서 하기 때문에 EC2에는 소스도, JDK도, Gradle도 없다. 인스턴스가
2GB라 거기서 Gradle을 돌리면 애너테이션 프로세서(MapStruct·QueryDSL·Lombok) 때문에 메모리가
빠듯하고, 돌아가는 애플리케이션과 메모리를 다투게 된다.

EC2에 있는 파일은 네 개뿐이고 전부 워크플로가 만든다.

| 파일 | 내용 |
|---|---|
| `~/app/docker-compose.yml` | 저장소에서 scp |
| `~/app/nginx/nginx.conf` | 저장소에서 scp |
| `~/app/.env` | `IMAGE_URI` (어떤 이미지를 띄울지) |
| `~/app/.env.app` | DB·S3·OpenAI 등 애플리케이션 환경변수 |

`.env.app`은 매 배포마다 GitHub Secrets에서 새로 쓰인다. 서버에서 직접 고쳐도 다음 배포에
덮인다 — 설정을 바꾸려면 Secret을 바꿔야 한다.

## 자격 증명이 두 종류인 이유

| 구간 | 방식 | 이유 |
|---|---|---|
| Actions → ECR | 정적 액세스 키 (IAM 사용자) | GitHub 러너는 AWS 외부라 인스턴스 역할을 받을 수 없다 |
| EC2 → ECR·S3 | 인스턴스 역할 | AWS 내부라 역할로 받는다. **서버에 키를 두지 않는다** |
| Actions → EC2 | SSH 키 | AWS 자격 증명과 무관한 별개 경로 |

`S3ClientConfig`가 `DefaultCredentialsProvider`를 쓰기 때문에, 인스턴스 역할에 S3 권한을
붙이면 `.env.app`에 AWS 키를 넣지 않아도 된다.

## 컨테이너 타임존을 명시하는 이유 (IMPORTANT)

`.env.app`의 `TZ=Asia/Seoul`을 빼면 안 된다.

EC2 호스트와 컨테이너 기본 타임존은 **UTC**다. 그런데 `BaseCreatedEntity`/`BaseEntity`의
감사 필드(`created_at`/`updated_at`)와 코드 곳곳의 `LocalDateTime.now()`는 **JVM 기본
타임존**을 따른다. 즉 `TZ` 없이 띄우면 저장되는 모든 시각이 9시간 어긋난다.

```
TZ=Asia/Seoul  →  JVM zone = Asia/Seoul,  now = 15:27
TZ 없음        →  JVM zone = Etc/UTC,     now = 06:27
```

`MemberList`처럼 `ZoneId.of("Asia/Seoul")`을 코드에 명시한 곳은 `TZ`와 무관하게 맞게
동작한다. 하지만 그렇지 않은 곳이 훨씬 많아서 `TZ` 주입이 여전히 필요하다. 새 코드를 쓸 때
시각 계산이 중요하다면 존을 명시하는 쪽이 안전하다.

RDS 파라미터 그룹의 `time_zone`도 같은 값으로 맞춰 둔다.

## nginx 설정에서 눈여겨볼 점

- `client_max_body_size 25M` — `application.yml`의 요청 상한(22MB)보다 커야 한다
- `proxy_buffering off` — 파일·ZIP 다운로드가 `StreamingResponseBody`라서, 버퍼링하면
  전송이 끝날 때까지 응답이 지연된다
- `proxy_read_timeout 120s` — AI 응답은 외부 API 호출을 기다려 기본 60초로는 짧다
- `forward-headers-strategy: framework`(application.yml)와 짝이다. nginx가 TLS를 종단하므로
  이게 없으면 springdoc이 OpenAPI server URL을 `http://`로 만들고, https로 열린 Swagger UI
  에서 "Try it out"이 mixed content로 차단된다
- `resolver` + `set $upstream` — app 컨테이너가 교체되면 IP가 바뀌므로 도커 DNS를 다시
  해석하게 한다. 이게 없으면 배포 후 nginx가 죽은 IP를 계속 붙잡는다
- ACME 챌린지 경로(`/.well-known/acme-challenge/`)만 https 리다이렉트에서 제외했다.
  인증서 갱신을 webroot 방식으로 해서 **갱신할 때 nginx를 내리지 않아도 된다**

## 헬스체크 게이트

`docker compose up -d`는 컨테이너가 떴는지만 보고 성공한다. 그래서 Flyway 마이그레이션
실패처럼 기동 도중 터지는 오류를 초록불로 지나친다. 배포 마지막에 `/health`가 응답할 때까지
최대 60초 기다리고, 실패하면 애플리케이션 로그를 출력하며 잡을 실패시킨다.

`ddl-auto: validate`이므로 엔티티와 스키마가 어긋나면 기동 자체가 실패한다. 엔티티를 바꿨으면
`src/main/resources/db/migration/`에 마이그레이션을 추가해야 한다.

## 장애 확인

```bash
ssh -i <키>.pem ubuntu@<EC2_HOST>
cd ~/app

docker compose ps                     # 컨테이너 상태
docker compose logs --tail=100 app    # 애플리케이션 로그
docker compose logs --tail=50 nginx   # nginx 로그
ls -R ~/app                           # scp된 파일 배치 확인
cat .env.app                          # 주입된 환경변수 (비밀값 포함, 주의)
```

증상별로 자주 보는 원인:

| 증상 | 원인 |
|---|---|
| nginx만 재시작 반복 | 인증서 파일 없음, 또는 `nginx/nginx.conf`가 경로 없이 평평하게 scp돼 Docker가 디렉터리를 만들어버린 경우 |
| app이 기동 실패 | Flyway 마이그레이션 충돌, `DB_HOST`/`DB_NAME` 오류, 보안 그룹에서 3306 막힘 |
| 배포가 ECR 로그인에서 실패 | 인스턴스 역할에 ECR 읽기 권한 없음, 또는 서버에 AWS CLI 없음 |
| 파일 업로드만 실패 | `AWS_S3_BUCKET` 값 오타, 인스턴스 역할의 S3 정책 `Resource`가 `버킷/*`가 아님 |
| 업로드는 되는데 이미지가 안 보임 | 버킷에 공개 읽기 정책 없음. `S3FileStorage.toUrl`이 presigned가 아니라 고정 URL을 응답에 싣기 때문 |
| 90일 뒤 전체 중단 | 인증서 갱신 실패. 아래 참고 |

## 인증서

`api.chungs.store` 인증서는 letsencrypt로 발급하고 `/etc/cron.d/certbot-renew`가 매주
갱신을 시도한다(만료 30일 이내일 때만 실제로 갱신한다).

발급은 `--standalone`(nginx가 80을 점유하기 전), 갱신은 `--webroot`(nginx를 내리지 않기 위해)
으로 경로가 다르다. 그래서 **갱신 경로는 따로 검증해야 한다.**

```bash
sudo docker run --rm \
  -v /etc/letsencrypt:/etc/letsencrypt \
  -v /var/www/certbot:/var/www/certbot \
  certbot/certbot renew --webroot -w /var/www/certbot --dry-run
```

nginx가 떠 있는 상태에서 돌려야 한다. 이걸 건너뛰면 갱신 실패를 만료 시점에 알게 된다.

## 환경변수 바꾸기

`.env.app`에 들어가는 값은 전부 GitHub Secrets에서 온다. 바꾸는 순서는
**Secret 수정 → `develop`에 아무 커밋이나 push(또는 배포 워크플로 재실행)** 다.
서버의 `.env.app`을 직접 고치는 건 다음 배포에 덮이므로 임시 확인용으로만 쓴다.

프론트엔드 배포 주소가 정해지면 `CORS_ALLOWED_ORIGINS`만 갱신하면 된다 — 코드 기본값은
로컬 개발 주소뿐이고, 운영 값은 환경변수로 주입한다.
