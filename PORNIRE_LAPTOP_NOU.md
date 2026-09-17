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
