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

## Comenzi utile

| Ce vrei | Comandă |
|---|---|
| Pornește în fundal | `docker compose up -d` |
| Oprește | `docker compose down` |
| Oprește și șterge datele | `docker compose down -v` |
| Reconstruiește după modificări de cod | `docker compose up --build` |
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

### Sălile

Sunt aceleași săli fizice, dar cele două instanțe nu știu una de alta. Ce împiedică două ore să
cadă în aceeași sală la aceeași oră trebuie introdus **în fiecare instanță separat**, ca
indisponibilități de sală în pagina Constrângeri. Aplicația nu verifică asta între instanțe.
