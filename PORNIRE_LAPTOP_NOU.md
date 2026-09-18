# Pornire pe un laptop nou (cu Docker)

Aplicația **Orar FSGC** rulează complet în Docker: bază de date + backend + frontend.
Pe laptopul nou nu trebuie instalate Java, Node sau Maven — **doar Docker**.

## 1. Cerințe

- **Docker Desktop** (macOS / Windows) sau **Docker Engine + Docker Compose** (Linux).
  - Windows: la instalare activează WSL2 dacă îți cere.
- Conexiune la internet **prima dată** (se descarcă imaginile și dependențele). Ulterior merge și offline.

## 2. Copiază proiectul

Copiază tot folderul `fsgc-java/` pe laptopul nou (USB, cloud sau `git clone`).
Poți exclude, dacă vrei (se regenerează automat, sunt mari degeaba):
- `frontend/node_modules/`
- `backend/target/`

## 3. Pornește aplicația

Pornește Docker Desktop, apoi într-un terminal deschis în folderul proiectului:

```bash
docker compose up --build
```

- Prima dată durează câteva minute (construiește imaginile).
- Rulările următoare pornesc în câteva secunde.

Deschide în browser: **http://localhost:5280**

## 4. Prima utilizare — importă datele

Pe un laptop nou baza de date pornește **goală** (schema se creează automat, dar fără date).
Mergi pe pagina **Import** și încarcă fișierul Excel al semestrului
(`sablon_import_semestru.xlsx` din folderul proiectului este un exemplu).
Apoi: **Generare** → **Orar**. (Vezi manualul de utilizare pentru detalii.)

## După o actualizare de cod: `--build`

Docker nu reconstruiește singur. `docker compose up -d` pornește **imaginea construită ultima
dată** — dacă între timp codul s-a schimbat (ai luat o versiune nouă, ai făcut `git pull`), în
browser îți apare tot aplicația veche, fără modificările noi. De fiecare dată când codul s-a
schimbat:

```bash
docker compose up -d --build
```

Verifică ce imagine rulezi:

```bash
docker images | grep fsgc-java     # data la care au fost construite
```

## Comenzi utile

| Ce vrei | Comandă |
|---|---|
| Pornește în fundal (cod neschimbat) | `docker compose up -d` |
| Oprește | `docker compose down` |
| Oprește și șterge datele | `docker compose down -v` |
| **Pornește după o modificare de cod** | `docker compose up -d --build` |
| Vezi logurile | `docker compose logs -f` |

## Note

- **Datele se păstrează** între reporniri (volumul Docker `orar-pgdata`). Doar `down -v` le șterge.
- Porturi folosite pe laptop: **5280** (aplicația), **8080** (backend), **5432** (baza de date).
  Dacă unul e ocupat, schimbă maparea din `docker-compose.yml` (partea `"5280:80"` etc.).
- Componente: `postgres` (PostgreSQL 16), `backend` (Spring Boot / Java 21), `frontend` (nginx + React build).

## Două secții în paralel

Cele două secții se planifică în instanțe complet separate: fiecare cu baza ei de date, deci
discipline, profesori, grupe, săli, reguli, ponderi și orar proprii. Nu se vede nimic dintr-una
în cealaltă.

### Cu Docker

```bash
# instanța A — porturile implicite
docker compose -p orar-a up -d
# se deschide la http://localhost:5280

# instanța B — porturi diferite
ORAR_PREFIX=orar-b APP_PORT=5281 BACKEND_PORT=8081 DB_PORT=5434 \
  docker compose -p orar-b up -d
# se deschide la http://localhost:5281
```

`-p` dă numele proiectului, iar volumul de date e numit după el, deci fiecare instanță are
propria bază. Oprirea uneia nu o atinge pe cealaltă:

```bash
docker compose -p orar-b down          # oprește B, păstrează datele
docker compose -p orar-b down -v       # oprește B și îi șterge datele
```

### Fără Docker, cu Maven și Vite

Creează a doua bază o singură dată:

```bash
docker exec orar-postgres psql -U orar -d postgres -c "CREATE DATABASE orar_b OWNER orar;"
```

Apoi, în terminale separate:

```bash
# backend A
cd backend && mvn spring-boot:run

# backend B
cd backend && mvn spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Dspring.datasource.url=jdbc:postgresql://localhost:5433/orar_b -Dserver.port=8081"

# frontend A
cd frontend && npm run dev

# frontend B
cd frontend && ORAR_PORT=5281 ORAR_API=http://localhost:8081 npm run dev
```

Flyway creează schema în baza nouă la prima pornire, deci nu trebuie să pregătești nimic.

### Un laptop pentru fiecare secție

Varianta cea mai curată: fiecare laptop rulează **o singură instanță**, cu porturile implicite.
Nu trebuie schimbat nimic în configurație — laptopurile diferă doar prin datele din baza lor.

Pe fiecare laptop: instalează Docker, copiază proiectul, `docker compose up -d --build`,
apoi deschide `http://localhost:5280`. Baza pornește goală.

**Mutarea datelor existente.** Dacă ai deja lucrat pe un laptop (import, ore online, săli
indisponibile, blocaje, orar generat), nu reimporta Excelul pe laptopul nou — ai pierde tot ce ai
bifat manual. Copiază baza ca atare.

Pe laptopul unde sunt datele acum, scoate câte un fișier pentru fiecare bază:

```bash
# instanța A (baza „orar")
docker exec orar-postgres pg_dump -U orar -d orar --no-owner --no-privileges > baza_A.sql

# instanța B (baza „orar_b"; numele containerului diferă dacă ai pornit cu -p)
docker exec orar-postgres pg_dump -U orar -d orar_b --no-owner --no-privileges > baza_B.sql
```

Copiază `baza_A.sql` pe primul laptop și `baza_B.sql` pe al doilea (USB, cloud, oricum).
Pe laptopul de destinație, **după** ce ai pornit o dată aplicația (ca să existe containerul):

```bash
docker compose stop backend                     # nimeni nu scrie în timp ce se încarcă
docker exec orar-postgres psql -U orar -d postgres -c "DROP DATABASE orar;"
docker exec orar-postgres psql -U orar -d postgres -c "CREATE DATABASE orar OWNER orar;"
docker exec -i orar-postgres psql -U orar -d orar < baza_A.sql    # sau baza_B.sql
docker compose start backend
```

Baza se numește `orar` pe fiecare laptop, indiferent din care instanță vine fișierul — de aceea
al doilea laptop încarcă `baza_B.sql` într-o bază tot numită `orar`. Fișierul conține și istoricul
migrărilor, deci aplicația pornește direct pe versiunea corectă a schemei.

Verifică după restaurare, în aplicație: numărul de activități, orele bifate **Online**,
indisponibilitățile de sală din **Constrângeri** și ponderile. Dacă toate sunt acolo, mutarea a
reușit.

**Versiunea de PostgreSQL** trebuie să fie aceeași (16, cea din `docker-compose.yml`). Dacă
folosești `docker compose` pe amândouă laptopurile, este.

### Sălile

Sunt aceleași săli fizice, dar cele două instanțe nu știu una de alta. Ce împiedică două ore să
cadă în aceeași sală la aceeași oră trebuie introdus **în fiecare instanță separat**, ca
indisponibilități de sală în pagina Constrângeri. Aplicația nu verifică asta între instanțe.

Pe două laptopuri separate problema e aceeași, doar că se vede mai greu: fiecare vede doar orarul
lui. În practică, ori împărțiți sălile între secții din start (fiecare marchează ca indisponibile
sălile celeilalte), ori, după ce amândouă orarele sunt gata, exportați-le și comparați sălile
înainte de a le publica.
