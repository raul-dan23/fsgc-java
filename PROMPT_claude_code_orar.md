# Proiect: Sistem de generare automată a orarului universitar

## Context și obiectiv

Construiește o aplicație full-stack pentru generarea automată a orarului unei facultăți (cursuri, seminarii, laboratoare), pornind de la date despre săli, profesori, discipline și grupe de studenți, importate dintr-un fișier Excel. Aplicația trebuie să producă un orar care respectă un set de constrângeri obligatorii (hard constraints) și să optimizeze un set de constrângeri de calitate (soft constraints), astfel încât orarul rezultat să fie echilibrat și uman, nu doar "valid".

**Stack tehnic obligatoriu:**
- Backend: Java 21, Spring Boot 3.x, Spring Data JPA, Spring Web, Spring Validation
- Motor de optimizare: **Timefold Solver** (timefold-solver-core + timefold-solver-spring-boot-starter) — NU implementa un algoritm genetic de mână, NU implementa backtracking manual. Folosește Timefold ca motor de constraint solving.
- Bază de date: PostgreSQL (prin Spring Data JPA / Hibernate, JDBC driver org.postgresql)
- Import/export Excel: Apache POI
- Frontend: React (Vite), fără TypeScript dacă simplifică, dar TypeScript e preferat dacă poți menține consistența
- Structură: **monorepo** — un singur folder rădăcină cu `backend/` (proiect Maven Spring Boot) și `frontend/` (proiect React/Vite)
- **Fără autentificare/login în această versiune** — aplicația e single-user, fără roluri, fără security layer. Nu adăuga Spring Security.

---

## Partea 1: Modelul de date (schema + entități JPA)

Importul vine dintr-un fișier Excel cu 4 sheet-uri. Structura EXACTĂ a fiecărui sheet (cu header-ele reale din fișierul `sablon_import_semestru.xlsx`):

### Sheet "Sectii" (grupe de studenți)
| coloană | tip | exemplu | descriere |
|---|---|---|---|
| sectie | string | RISE | codul specializării |
| an | int | 1 | anul de studiu |
| program | enum | license / master | ciclul de studii |
| nr_studenti_an | int | 55 | total studenți în acel an, pe acea secție |
| cod_grupa | string | RISE1-GR1 | codul unic al grupei |
| nr_studenti_grupa | int | 27 | mărimea grupei (relevantă pentru seminar/lab) |
| department | string | FSGC | departamentul |

### Sheet "Profesori"
| coloană | tip | exemplu |
|---|---|---|
| name | string | Corina Tursie |
| email | string | corina.tursie@e-uvt.ro |
| title | string | conf.dr. / lect.dr. / prof.dr. (titlul didactic) |
| department | string | FSGC |

### Sheet "Sali"
| coloană | tip | exemplu | descriere |
|---|---|---|---|
| name | string | Amfiteatru A03 | numele/codul sălii |
| department | string | FSGC | |
| floor | string | Parter / 5 / 6 | etaj — **atenție:** poate fi text, nu doar număr |
| capacity | int | 120 | capacitate maximă |
| typology | enum | Amphitheater / Course / Seminar | tipul activității pentru care e potrivită sala |
| available_days | string | "L,M,Mi,J,V" | zile disponibile, separate prin virgulă (L=Luni, M=Marți, Mi=Miercuri, J=Joi, V=Vineri) |
| available_from | time | 08:00 | ora de început a disponibilității |
| available_until | time | 20:00 | ora de sfârșit a disponibilității |
| usage_restrictions | string nullable | (poate fi gol) | restricții suplimentare în text liber |

**IMPORTANT — extensii necesare față de șablon, derivate din documentul de constrângeri al beneficiarului (detaliat în Partea 2):**
- O sală trebuie să aibă și un câmp `address` / `building` (clădire/adresă), pentru că există constrângerea: dacă o grupă are ore consecutive, sălile trebuie să fie în aceeași clădire, altfel trebuie pauză pentru navetă între clădiri (exemplu real: Str. Paris vs Blvd. Pârvan).
- O sală trebuie să poată avea disponibilitate **neuniformă pe zile** (nu doar un singur interval global) — exemplu real: sala 028 e disponibilă luni integral, marți-miercuri doar 08:00-14:30, joi integral, vineri deloc. Deci `available_days/from/until` ca o singură valoare per sală NU e suficient — modelează disponibilitatea sălii ca o listă de intervale `(zi, ora_inceput, ora_sfarsit)`, nu ca un singur range global. Recomand o entitate separată `RoomAvailability` (room_id, day_of_week, start_time, end_time) cu relație One-to-Many față de `Room`.
- O sală trebuie să poată avea `equipment` (dotări: ex. "computer pe catedră", "proiector") — relevant pentru constrângerea profesor-sală (ex: un profesor fără laptop propriu poate preda doar în săli cu calculator pe catedră).

### Sheet "Discipline"
| coloană | tip | exemplu | descriere |
|---|---|---|---|
| set_studenti | string | AP1+SP1+RISE1 | poate conține MAI MULTE grupe combinate (curs comun), separate prin "+" — trebuie parsat ca listă de coduri de grupă/secție-an |
| materie | string | Administratie Publica | numele disciplinei |
| cod_materie | string | AP101 | cod unic |
| profesor | string | Tursie Corina | numele profesorului (de mapat la entitatea Professor) |
| activitate | enum | Curs Amfiteatru / Curs / Seminar / Laborator | tipul activității — folosit pentru a determina ce tip de sală e necesară |
| department | string | FSGC | |

**Extensii necesare față de șablon, derivate din constrângeri (Partea 2):**
- O disciplină/activitate trebuie să poată specifica **frecvența**: săptămânal / doar săptămâni pare / doar săptămâni impare / o dată la 2 săptămâni cu paritate variabilă. Adaugă un enum `WeekParity { EVERY_WEEK, EVEN_WEEKS, ODD_WEEKS }` pe entitatea care reprezintă o activitate ce trebuie programată.
- Trebuie un câmp pentru durata activității în module (implicit 1 modul = 90 min, dar permite activități de 2 module consecutive dacă există asemenea cazuri — verifică cu utilizatorul dacă apare, dar modelează entitatea flexibil de la început, cu `durationInSlots` (default 1)).
- O activitate trebuie să poată fi marcată ca aparținând unei categorii speciale: `DPPD`, `CCOC`, `DCT`, `LIMBI_STRAINE`, `NORMAL` — pentru a putea aplica blocările de interval descrise în Partea 2.

### Entități JPA propuse (schelet minim, completează cu validări și relații)
```
Department(id, name)
StudentGroup(id, name /* cod_grupa */, specialization /* sectie */, year, studyProgram /* license|master */, studentCount, department)
Professor(id, name, email, title, department, hasOwnLaptop /* boolean, default true */)
Building(id, name, address)
Room(id, name, department, floor, capacity, typology, building, equipment[])
RoomAvailability(id, room, dayOfWeek, startTime, endTime)
Subject(id, code, name, department)
ScheduledActivity(id, subject, professor, activityType /* COURSE|SEMINAR|LAB */, studentGroups[] /* many-to-many, pentru cursuri comune */, durationInSlots, weekParity, specialCategory /* DPPD|CCOC|DCT|... */)
TimeSlot(id, dayOfWeek, startTime, endTime, slotIndex /* 1-8, corespunzător celor 8 module orare fixe */)
```

**Modulele orare sunt fixe și cunoscute** (8 module pe zi, identice în toate sursele văzute):
```
1: 08:00–09:30
2: 09:40–11:10
3: 11:20–12:50
4: 13:00–14:30
5: 14:40–16:10
6: 16:20–17:50
7: 18:00–19:30
8: 19:40–21:10
```
Generează aceste `TimeSlot` ca date de seed (Luni-Vineri × cele 8 module = 40 sloturi), nu le importa din Excel.

---

## Partea 2: Constrângerile — regulile EXACTE de business

Acestea sunt regulile reale comunicate de beneficiar (departament universitar). Implementează-le ca `ConstraintProvider` în Timefold, separând clar hard constraints (penalizare infinită / `asConstraint` cu `.penalize(HardSoftScore.ONE_HARD)`) de soft constraints (`.penalize(HardSoftScore.ONE_SOFT, ...)` cu pondere configurabilă).

### Hard constraints (obligatorii, niciodată încălcate)

1. **Fără suprapuneri profesor**: un profesor nu poate avea două activități în același TimeSlot.
2. **Fără suprapuneri sală**: o sală nu poate fi ocupată de două activități în același TimeSlot (cu excepția aceleiași perechi zi+interval dar paritate de săptămână diferită — o sală poate fi folosită de activitate A în săptămâni pare și activitate B în săptămâni impare, fără conflict).
3. **Fără suprapuneri grupă de studenți**: o grupă nu poate avea două activități în același TimeSlot (la fel, cu excepția paritate-diferită).
4. **Capacitate sală ≥ mărime grupă/grupe**: dacă activitatea e pentru o singură grupă, folosește `studentCount` al grupei; dacă e curs comun pentru mai multe grupe (set_studenti cu "+"), suma studenților din toate grupele participante.
5. **Tip sală compatibil cu tip activitate**: activitate de tip "Curs Amfiteatru" necesită `typology = Amphitheater`; "Laborator" necesită o sală cu echipamentul necesar (de definit per disciplină dacă va fi nevoie — momentan tratează generic prin typology).
6. **Disponibilitatea sălii**: activitatea poate fi plasată într-un TimeSlot doar dacă există o `RoomAvailability` care acoperă acel interval, pentru acea zi.
7. **Module fixe pentru master**: orice activitate aparținând unei grupe cu `studyProgram = master` poate fi plasată DOAR în sloturile cu interval orar în 16:20–21:10 (module 6, 7, 8), de luni până vineri.
8. **Zile blocate pentru ani terminali**: configurabil per an universitar — o zi din săptămână (sau două) e complet blocată pentru activități didactice pentru un an terminal specific (ex: an 3 licență, vineri). Trebuie să fie o configurare editabilă din UI (nu hardcodată), pentru că se schimbă de la un semestru la altul, prin decizia senatului. Modelează ca entitate `BlockedDayRule(id, studyProgram, year, dayOfWeek, semester, academicYear)`.
9. **Blocaje de interval pentru categorii speciale** (DPPD, CCOC, DCT, limbi străine): un interval specific (zi + modul) e rezervat exclusiv pentru o categorie de activitate, pentru un an/secție anume — în acel interval, pentru acea grupă, NU se pot programa alte activități normale. Modelează ca entitate `SpecialBlockRule(id, studentGroup sau specialization+year, dayOfWeek, timeSlot, category, semester, academicYear)`. Exemplu real: an 1 licență are DPPD joi 14:40–16:10; an 2 are DPPD luni 09:40–11:10.
10. **Indisponibilitate individuală profesor — zile**: un profesor poate fi marcat indisponibil în zile întregi (ex: poate preda doar luni/marți). Entitate `ProfessorUnavailability(id, professor, dayOfWeek nullable, startTime nullable, endTime nullable)` — dacă startTime/endTime sunt null, înseamnă toată ziua blocată.
11. **Indisponibilitate individuală profesor — intervale**: un profesor poate fi indisponibil doar în anumite intervale orare din anumite zile (folosește aceeași entitate de mai sus cu startTime/endTime completate).
12. **Restricții sală-profesor**: un profesor poate fi restricționat să predea doar în anumite săli, sau interzis explicit dintr-o sală anume. Entitate `ProfessorRoomRestriction(id, professor, room, restrictionType /* ONLY_THIS sau FORBIDDEN */)`. Dacă există cel puțin o restricție de tip ONLY_THIS pentru un profesor, atunci el poate preda DOAR în sălile din lista ONLY_THIS a lui.
13. **Corelare clădire/adresă pentru ore consecutive** (HARD, decizie fermă): dacă aceeași grupă are activități în module consecutive (ex: modul 2 și modul 3, în aceeași zi), sălile trebuie să fie în aceeași clădire SAU să existe un modul liber între ele pentru navetă. Implementează ca penalizare HARD strictă — dacă grupa are activități în sloturi consecutive cu clădiri diferite și fără modul liber între ele, e o încălcare HARD, niciodată acceptată, chiar dacă asta înseamnă că unele activități rămân neplasate în soluția finală. NU trata ca soft constraint.

### Soft constraints (de optimizat, nu de respectat obligatoriu — AICI e cheia "orarului uman")

Acestea sunt cele mai importante pentru calitatea percepută a orarului. Implementează-le cu ponderi separate și configurabile (citite dintr-un fișier de configurare sau tabel din DB, NU hardcodate ca magic numbers în cod):

1. **Echilibrare zilnică per grupă** (pondere mare): pentru fiecare grupă de studenți, penalizează diferența dintre numărul de activități din ziua cea mai încărcată și ziua cea mai goală din săptămână. Obiectiv: nicio grupă nu trebuie să aibă 8 ore luni și 1 oră vineri. *Acesta e exact bug-ul identificat în orarul actual generat de aplicația Laravel — Luni avea 64 activități plasate la nivel de facultate, Vineri doar 5. Constrângerea asta trebuie să aibă pondere suficient de mare încât solverul să prefere distribuția uniformă.*
2. **Evitarea găurilor mari în orarul unei grupe** (pondere mare): într-o zi, dacă o grupă are activități la modulul N și modulul N+2 dar nu la N+1, asta creează o "gaură" de 90 de minute fără sens. Penalizează ferestrele goale între prima și ultima activitate a zilei pentru o grupă.
3. **Evitarea orelor târzii nejustificate pentru licență** (pondere medie): pentru grupele de licență (nu master), penalizează activitățile plasate în modulele 7-8 (18:00-21:10), proporțional cu cât de târziu sunt — modulul 8 mai penalizat decât 7.
4. **Compactarea zilei** (pondere mică-medie): preferă ca activitățile unei grupe într-o zi să fie consecutive (fără găuri), începând dimineața, în loc de a fi împrăștiate.
5. **Echilibrare globală pe săptămână** (pondere mică): la nivelul întregii facultăți, nu doar per grupă, evită ca toate disciplinele să se aglomereze în 2-3 zile.
6. **Preferințe profesor** (pondere mică, opțională — implementează entitatea dar las-o neobligatorie): dacă există preferințe explicite de interval orar pentru un profesor, recompensează respectarea lor.

**Ponderile trebuie expuse într-un endpoint/UI de configurare**, pentru că utilizatorul (eu) va vrea să le ajusteze empiric, comparând rezultate. Nu le hardcoda în clasa de constrângeri fără a le face parametrizabile.

### Gestionarea cazului imposibil

Dacă există constrângeri hard incompatibile (orar imposibil de generat complet), aplicația trebuie să:
- Identifice care activități NU au putut fi plasate (Timefold suportă acest lucru prin analiză de scor — folosește `ScoreAnalysis` / `ConstraintMatchTotal` pentru a extrage care constrângeri sunt încălcate și de ce)
- Afișeze în UI o listă clară a conflictelor rămase, cu sugestii text generate din analiza constrângerilor încălcate (ex: "Sala 518 (cap. 17) e singura disponibilă pentru SP3 (25 studenți) — măriți capacitatea sau schimbați sala")
- NU eșueze silențios — întoarce un orar parțial + raport de probleme, nu o eroare opacă.

---

## Partea 3: Import Excel

Implementează un endpoint `POST /api/import/excel` care primește fișierul `.xlsx` cu structura din Partea 1 (cele 4 sheet-uri: Sectii, Profesori, Sali, Discipline) și:
- Parsează fiecare sheet cu Apache POI
- Validează datele (referințe valide: profesorul dintr-un rând din "Discipline" trebuie să existe în "Profesori"; grupele din "set_studenti" trebuie să existe în "Sectii", etc.)
- Întoarce erori de validare clare, cu numărul rândului din Excel unde e problema, NU doar un stacktrace
- Salvează datele validate în baza de date (operație tranzacțională — totul sau nimic)

Pentru constrângerile suplimentare care NU sunt în șablonul de Excel curent (zile blocate, blocaje DPPD/CCOC/DCT, indisponibilități profesori, restricții sală-profesor, disponibilitate detaliată pe zile a sălilor) — implementează CRUD simplu prin UI (formulare React), nu prin import Excel, pentru că acestea se schimbă rar și au structură variabilă.

---

## Partea 4: Generarea orarului (Timefold)

- Definește `@PlanningEntity` pentru `ScheduledActivity` (variabila de planificare = `TimeSlot` + `Room`, sau separă în două planning variables dacă Timefold o cere)
- Definește `@PlanningSolution` care agregă toate activitățile de plasat + resursele disponibile (săli, sloturi)
- Implementează `ConstraintProvider` cu toate constrângerile din Partea 2, fiecare ca metodă separată și clar denumită (ex: `noProfessorOverlap`, `roomCapacitySufficient`, `balancedDailyLoad`, etc.) — NU o singură metodă monolitică
- Endpoint `POST /api/timetable/generate` care pornește solver-ul (asincron — generarea poate dura; folosește `SolverManager` din Timefold, nu blocant pe request)
- Endpoint `GET /api/timetable/status/{jobId}` pentru a verifica progresul
- Endpoint `GET /api/timetable/result/{jobId}` care întoarce orarul generat + scorul + lista de constrângeri încălcate (dacă există)

### Configurarea timpului de solving — IMPORTANT, nu hardcoda o singură valoare

Bugetul de timp al solverului trebuie să fie **configurabil per request de generare**, nu fixat în cod. Motivul: în prezentări live nu avem voie să stăm 10-20 minute, dar pentru rulări reale (nu în fața nimănui) vrem calitate maximă și putem aștepta mult.

Implementează:
- Parametrul `terminationSeconds` (sau similar) trimis în body-ul lui `POST /api/timetable/generate`, cu o valoare implicită rezonabilă (ex: 60 secunde) dacă nu e specificat.
- Folosește `SolverConfig.withTerminationConfig(new TerminationConfig().withSecondsSpentLimit(...))` din Timefold, setat dinamic per solver job, nu static în `application.yml`.
- **Mod „comparare"**: un endpoint `POST /api/timetable/compare` care primește o listă de bugete de timp (ex: `[30, 120, 300]`), rulează generarea succesiv pentru fiecare (pe aceleași date de input), și întoarce pentru fiecare rulare: scorul final (hard/soft), timpul real de rulare, numărul de constrângeri încălcate. Scopul: să poți arăta concret „cu X secunde obținem scorul Y, cu mai mult timp se îmbunătățește cu Z" — util atât pentru tine în calibrare, cât și ca argument de prezentare către beneficiar.
- UI-ul de generare (Partea 5) trebuie să aibă un input pentru a alege bugetul de timp înainte de a porni generarea, plus un buton separat „Compară mai multe bugete" care declanșează modul de comparare de mai sus și afișează rezultatele într-un tabel simplu (timp alocat / scor obținut / nr. încălcări).

---

## Partea 5: Frontend React

1. **Pagină de import**: drag-and-drop pentru fișierul Excel, afișare rezultate validare/erori
2. **Pagină de configurare constrângeri**: formulare CRUD pentru zilele blocate, blocajele DPPD/CCOC/DCT, indisponibilități profesori, restricții sală-profesor, disponibilitate săli pe zile — și un panou cu sliderele/input-urile pentru ponderile soft constraints
3. **Pagină de generare**: buton "Generează orar", bară de progres (polling pe status endpoint), afișare rezultat final cu scor
4. **Pagină de vizualizare orar**: grid replicând formatul din `timetable_export.xlsx` — zile pe rânduri, module orare, coloane = grupe/secții-an, celulă = disciplină+tip+profesor+grupă, cu rândul de sală afișat dedesubt (separat, ca în fișierul original). Trebuie view-uri multiple: "Pe An/Grupă", "Pe Sală", "Pe Profesor" — la fel ca sheet-urile din export-ul vechi (`Pe An`, `Pe Sală`, `Pe Disciplină`, `Pe Cadru Didactic`)
5. **Editare manuală drag-and-drop**: după generare, utilizatorul trebuie să poată muta o activitate dintr-un slot în altul direct din grid, cu validare live a constrângerilor hard (afișează avertisment dacă mutarea încalcă o regulă, dar permite forțarea ei dacă utilizatorul insistă — cu marcaj vizual clar "constrângere încălcată manual")
6. **Export**: buton de export Excel al orarului final, replicând formatul `timetable_export_2026-06-18.xlsx` (sheet Master + sheet-uri per filtrare)

---

## Partea 6: Structura de proiect (monorepo)

```
orar-facultate/
├── backend/
│   ├── pom.xml
│   ├── src/main/java/.../
│   │   ├── domain/          (entități JPA)
│   │   ├── repository/      (Spring Data repos)
│   │   ├── service/         (logică business, import excel, export excel)
│   │   ├── solver/          (ConstraintProvider, PlanningSolution, PlanningEntity Timefold)
│   │   ├── controller/      (REST endpoints)
│   │   ├── dto/
│   │   └── config/
│   └── src/main/resources/
│       ├── application.yml  (config DB PostgreSQL, config Timefold solver - timp limită, etc.)
│       └── db/migration/    (Flyway, recomandat pentru a controla schema explicit)
├── frontend/
│   ├── package.json
│   └── src/
│       ├── pages/
│       ├── components/
│       └── api/             (client REST către backend)
├── docker-compose.yml       (PostgreSQL local, pentru dezvoltare ușoară)
└── README.md
```

Folosește **Flyway** pentru migrații DB explicite (nu doar `ddl-auto: update`), ca să am control clar asupra schemei pe măsură ce evoluează.

Adaugă un `docker-compose.yml` minimal doar pentru PostgreSQL, ca să pot rula baza de date local fără instalare manuală.

---

## Cerințe de calitate / livrare

- Cod comentat în zonele de business logic complex (în special `ConstraintProvider`)
- Fiecare constrângere din Partea 2 trebuie să aibă un test unit dedicat (Timefold suportă testare de constrângeri izolat prin `ConstraintVerifier`) — generează cel puțin un test per hard constraint, demonstrând că o încălcare e detectată
- Nu implementa toate cele 5+ pagini de frontend perfect din prima — construiește mai întâi backend-ul complet și funcțional (import → generare → export ca JSON), apoi frontend-ul. Confirmă cu mine după ce backend-ul e funcțional, înainte de a trece masiv pe frontend.
- Dacă întâlnești o ambiguitate în oricare constrângere de mai sus pe care nu o poți rezolva clar din context, oprește-te și întreabă, nu presupune.

---

## Date de test

Voi furniza separat (deja disponibile, nu trebuie generate de tine):
- `sablon_import_semestru.xlsx` — exemplu de fișier de import valid
- Un document Word cu toate constrângerile descrise mai sus, în formă narativă (sursa acestui prompt)
- Un export anterior de orar (`timetable_export.xlsx`) ca referință de format de output
- Un orar real, folosit ca etalon de "bun" pentru distribuție echilibrată

Folosește-le ca date de seed/test pentru a valida că importul și generarea funcționează corect end-to-end.
