# 배포 (GCP VM + Docker)

Compute Engine VM 1대에서 Docker Compose로 **MySQL + 백엔드**를 띄우는 간단한 구성입니다. 프론트는 포함하지 않습니다.

```
인터넷 ──(TCP 8080)──▶ VM: backend 컨테이너 (prod 프로필, 조회 전용 계정 riftdata_app)
                              │ 컨테이너 네트워크 (외부에 열지 않음)
                              ▼
                          mysql 컨테이너 (볼륨 mysql-data)
```

| 파일 | 역할 |
| --- | --- |
| `Dockerfile` | 미리 빌드한 jar를 Java 21 실행 이미지에 넣음 (`build/libs/riftdata-backend.jar`) |
| `docker-compose.prod.yml` | MySQL(포트 비공개, 처음 만들 때 `schema.sql`·조회 전용 계정 생성) + 백엔드(`prod` 프로필) |
| `deploy/prod.env.example` | VM의 비밀번호·포트·CORS 설정 견본 → VM에서 `prod.env`로 복사 (커밋 안 됨) |
| `deploy/mysql-init/02-app-user.sh` | 서버용 조회 전용 계정 `riftdata_app`(SELECT만) 생성 |
| `src/main/resources/application-prod.yml` | 서버는 `riftdata_app`으로 접속, SQL 로그 끔 |

- 계정: 서버는 `DB_APP_USERNAME`(SELECT만), CSV 적재는 `DB_USERNAME`(riftdata DB 쓰기 권한)
- 이미지는 레지스트리 없이 로컬(Mac)에서 linux/amd64로 만들어 VM으로 복사합니다. jar는 플랫폼과 무관해 빠르게 만들 수 있습니다.

아래 명령은 로컬 저장소 루트(맥 zsh 기준)에서 실행합니다. `gcloud`는 로그인과 프로젝트 설정이 되어 있어야 합니다.

## 1. VM과 방화벽 (처음 한 번)

```bash
gcloud compute instances create riftdata-backend \
  --zone=asia-northeast3-a --machine-type=e2-small \
  --image-family=ubuntu-2404-lts-amd64 --image-project=ubuntu-os-cloud \
  --boot-disk-size=20GB --tags=riftdata-backend

gcloud compute firewall-rules create riftdata-backend-8080 \
  --allow=tcp:8080 --target-tags=riftdata-backend --description="RIFT DATA 백엔드 API"
```

- e2-small(2 vCPU 공유, 메모리 2GB)은 MySQL과 백엔드를 같이 띄우기에 충분합니다. 켜 둔 시간만큼 요금이 나오므로 쓰지 않을 때는 `gcloud compute instances stop riftdata-backend --zone=asia-northeast3-a`
- VM의 외부 IP: `gcloud compute instances describe riftdata-backend --zone=asia-northeast3-a --format="value(networkInterfaces[0].accessConfigs[0].natIP)"`
  (기본은 임시 IP라 VM을 껐다 켜면 바뀔 수 있습니다. 고정하려면 `gcloud compute addresses create`로 고정 IP를 예약합니다.)

VM에 Docker 설치와 저장소 받기:

```bash
gcloud compute ssh riftdata-backend --zone=asia-northeast3-a --command='
  curl -fsSL https://get.docker.com | sudo sh &&
  sudo usermod -aG docker $USER &&
  git clone https://github.com/lol-db/lol-db-backend.git'
```

## 2. 이미지 만들어 올리기 (배포할 때마다)

```bash
./gradlew bootJar
docker build --platform linux/amd64 -t riftdata-backend:latest .
docker save riftdata-backend:latest | gzip > /tmp/riftdata-backend.tar.gz
gcloud compute scp /tmp/riftdata-backend.tar.gz riftdata-backend:~ --zone=asia-northeast3-a
gcloud compute ssh riftdata-backend --zone=asia-northeast3-a --command='
  docker load < ~/riftdata-backend.tar.gz && cd ~/lol-db-backend && git pull'
```

## 3. 설정과 실행

처음 한 번 VM에서 `prod.env`를 만들고 비밀번호를 채웁니다 (값에 `$`는 쓰지 않음).

```bash
gcloud compute ssh riftdata-backend --zone=asia-northeast3-a
cd ~/lol-db-backend && cp deploy/prod.env.example prod.env && nano prod.env
```

실행 (VM에서):

```bash
docker compose -f docker-compose.prod.yml --env-file prod.env up -d
docker compose -f docker-compose.prod.yml --env-file prod.env ps
```

- MySQL은 처음 만들 때(빈 볼륨) `schema.sql`과 `02-app-user.sh`를 실행합니다. 비밀번호를 바꾸려면 볼륨을 지워야 하므로(`down -v`, 데이터 삭제) 처음에 정해 둡니다.
- 새 이미지를 올린 뒤에는 같은 `up -d`로 백엔드만 다시 만들어집니다. DB 데이터는 볼륨에 남습니다.

## 4. 데이터 적재 (처음 한 번, 시즌 데이터가 바뀌면 다시)

```bash
# 로컬에서 CSV를 VM으로
gcloud compute ssh riftdata-backend --zone=asia-northeast3-a --command='mkdir -p ~/lol-db-backend/data'
gcloud compute scp ~/Downloads/2026_LoL_esports_match_data_from_OraclesElixir.csv \
  riftdata-backend:~/lol-db-backend/data/ --zone=asia-northeast3-a

# VM에서: 백엔드 이미지를 loader 프로필로 한 번 실행 (쓰기 계정 DB_USERNAME 사용)
docker compose -f docker-compose.prod.yml --env-file prod.env run --rm --no-deps backend \
  --spring.profiles.active=loader --loader.file=/data/2026_LoL_esports_match_data_from_OraclesElixir.csv
```

적재 규칙과 옵션(`--loader.dry-run=true`, `--loader.replace=true`)은 [csv-loader.md](csv-loader.md)에 있습니다.

## 5. 확인

```bash
curl http://<VM 외부 IP>:8080/api/v1/meta
```

- Swagger UI: `http://<VM 외부 IP>:8080/swagger-ui.html`
- 로그: `docker compose -f docker-compose.prod.yml --env-file prod.env logs -f backend`
- 프론트 주소가 정해지면 `prod.env`의 `CORS_ALLOWED_ORIGINS`에 넣고 `up -d`로 다시 띄웁니다.

## 6. 정리

```bash
gcloud compute instances delete riftdata-backend --zone=asia-northeast3-a
gcloud compute firewall-rules delete riftdata-backend-8080
```

## 참고

- HTTPS는 없습니다 (HTTP 8080). 필요하면 앞에 Caddy·Nginx 같은 리버스 프록시나 GCP 로드 밸런서를 둡니다.
- 백업: `docker compose -f docker-compose.prod.yml --env-file prod.env exec mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --databases riftdata' > backup.sql`
