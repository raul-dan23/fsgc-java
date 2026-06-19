# Orar FSGC — generare automată de orar universitar

Aplicație full-stack care generează automat orarul unei facultăți pornind de la un fișier
Excel (săli, profesori, discipline, grupe), respectând constrângeri obligatorii (hard) și
optimizând constrângeri de calitate (soft) cu **Timefold Solver**, astfel încât orarul să fie
echilibrat și „uman", nu doar valid.

## Stack

- **Backend:** Java 21, Spring Boot 3.4, Spring Data JPA, Spring Web/Validation
- **Optimizare:** Timefold Solver (`timefold-solver-spring-boot-starter`) — fără algoritm propriu
- **DB:** PostgreSQL + Flyway (migrații explicite)
- **Import/Export Excel:** Apache POI
- **Frontend:** React + Vite
- **Structură:** monorepo (`backend/`, `frontend/`, `docker-compose.yml`)
- Fără autentificare (single-user).

> ⚠️ **JDK 21 obligatoriu.** Lombok (folosit în entități) nu rulează pe JDK 25. Pe acest Mac
> JDK 21 e instalat prin Homebrew (keg-only). Setează `JAVA_HOME` înainte de comenzile Maven:
> ```bash
> export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
> ```

## Rulare

### 1. Pornește PostgreSQL
```bash
docker compose up -d            # db=orar user=orar pass=orar pe :5432
```

### 2. Pornește backend-ul
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
mvn -f backend/pom.xml spring-boot:run    # http://localhost:8080 ; Flyway creează schema + cele 40 de sloturi
```

### 3. Pornește frontend-ul
```bash
cd frontend
npm install      # prima dată
npm run dev      # http://localhost:5280 (proxy /api -> :8080)
```

## Flux de utilizare

1. **Import** — încarcă `sablon_import_semestru.xlsx` (sheet-uri: Sectii, Profesori, Sali,
   Discipline). Validare cu numărul rândului din Excel; salvare tranzacțională (totul sau nimic);
   re-importul resetează datele.
2. **Constrângeri** — reglează ponderile soft (sliders) și administrează regulile care nu sunt în
   Excel: zile blocate (ani terminali), blocaje DPPD/CCOC/DCT/limbi străine, indisponibilități
   profesori, restricții sală-profesor, disponibilitate săli pe zile/intervale.
3. **Generare** — alege bugetul de timp (secunde) și pornește; bara de progres face polling.
   Butonul „Compară bugete" rulează succesiv mai multe bugete și arată scorul/încălcările pentru
   fiecare.
4. **Orar** — grilă cu trei vizualizări (Pe An/Grupă, Pe Sală, Pe Cadru Didactic), editare manuală
   prin drag-and-drop cu validare live a constrângerilor hard (mutarea e permisă chiar forțată,
   cu avertisment), și export Excel.

## API (rezumat)

| Metodă | Cale | Descriere |
|---|---|---|
| POST | `/api/import/excel` | import `.xlsx` (multipart `file`); 200 sau 422 + erori |
| POST | `/api/timetable/generate` | `{terminationSeconds}` → `{jobId}` (asincron) |
| GET | `/api/timetable/status/{jobId}` | stare + scor pentru polling |
| GET | `/api/timetable/result/{jobId}` | rezultat complet: scor, conflicte, activități |
| POST | `/api/timetable/compare` | `{budgets:[30,120,300]}` → tabel calitate/timp |
| GET | `/api/data/schedule` | orarul curent persistat |
| PUT | `/api/data/activities/{id}/assignment` | mutare manuală `{timeSlotId, roomId}` + violări |
| GET/PUT | `/api/weights` | ponderile soft (configurabile) |
| GET/POST/DELETE | `/api/rules/{kind}` | CRUD reguli (blocked-days, special-blocks, …) |
| GET | `/api/export/excel` | export orar `.xlsx` (Master + Pe Grupă/Sală/Cadru Didactic) |

## Model de scor

`HardMediumSoftScore`:
- **hard** — constrângerile obligatorii (niciodată încălcate cu bună știință);
- **medium** — activități neplasate (solverul preferă să lase o activitate neplasată decât să
  încalce o regulă hard → orar parțial + raport de conflicte);
- **soft** — calitatea (echilibrare zilnică/grupă, evitarea găurilor, ore târzii, compactare,
  echilibrare globală), cu ponderi configurabile din UI.

## Teste

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
mvn -f backend/pom.xml test
```
`TimetableConstraintProviderTest` verifică, cu `ConstraintVerifier`, câte un caz per constrângere
hard (plus cazuri negative pentru excepția de paritate).

## Note / limitări cunoscute

- Șablonul curent nu conține programe de **master** și nici săli de tip **amfiteatru/laborator**;
  constrângerile aferente sunt implementate și testate unitar, dar nu se declanșează pe aceste
  date. Importă un șablon completat pentru a le exercita end-to-end.
- O grupă referită în „Discipline" dar absentă din „Sectii" (ex. `SSEC3`) generează o grupă
  *placeholder* (mărime implicită 30) + avertisment, în loc să blocheze importul. Completeaz-o în
  „Sectii" pentru capacitate corectă.
- `address`/`building` pentru săli și `equipment`/`hasOwnLaptop` nu sunt în șablon; se completează
  prin UI/CRUD. Constrângerea de clădire pentru ore consecutive e activă doar când clădirile sunt
  cunoscute.
