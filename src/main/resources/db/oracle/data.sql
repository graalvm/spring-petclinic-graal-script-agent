MERGE INTO vets t
USING (SELECT 1 id, 'James' first_name, 'Carter' last_name FROM dual) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (first_name, last_name) VALUES (s.first_name, s.last_name)
@@

MERGE INTO vets t
USING (SELECT 2 id, 'Helen' first_name, 'Leary' last_name FROM dual) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (first_name, last_name) VALUES (s.first_name, s.last_name)
@@

MERGE INTO vets t
USING (SELECT 3 id, 'Linda' first_name, 'Douglas' last_name FROM dual) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (first_name, last_name) VALUES (s.first_name, s.last_name)
@@

MERGE INTO vets t
USING (SELECT 4 id, 'Rafael' first_name, 'Ortega' last_name FROM dual) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (first_name, last_name) VALUES (s.first_name, s.last_name)
@@

MERGE INTO vets t
USING (SELECT 5 id, 'Henry' first_name, 'Stevens' last_name FROM dual) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (first_name, last_name) VALUES (s.first_name, s.last_name)
@@

MERGE INTO vets t
USING (SELECT 6 id, 'Sharon' first_name, 'Jenkins' last_name FROM dual) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (first_name, last_name) VALUES (s.first_name, s.last_name)
@@

MERGE INTO specialties t
USING (SELECT 1 id, 'radiology' name FROM dual) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (name) VALUES (s.name)
@@

MERGE INTO specialties t
USING (SELECT 2 id, 'surgery' name FROM dual) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (name) VALUES (s.name)
@@

MERGE INTO specialties t
USING (SELECT 3 id, 'dentistry' name FROM dual) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (name) VALUES (s.name)
@@

MERGE INTO vet_specialties t
USING (SELECT 2 vet_id, 1 specialty_id FROM dual) s
ON (t.vet_id = s.vet_id AND t.specialty_id = s.specialty_id)
WHEN NOT MATCHED THEN INSERT (vet_id, specialty_id) VALUES (s.vet_id, s.specialty_id)
@@

MERGE INTO vet_specialties t
USING (SELECT 3 vet_id, 2 specialty_id FROM dual) s
ON (t.vet_id = s.vet_id AND t.specialty_id = s.specialty_id)
WHEN NOT MATCHED THEN INSERT (vet_id, specialty_id) VALUES (s.vet_id, s.specialty_id)
@@

MERGE INTO vet_specialties t
USING (SELECT 3 vet_id, 3 specialty_id FROM dual) s
ON (t.vet_id = s.vet_id AND t.specialty_id = s.specialty_id)
WHEN NOT MATCHED THEN INSERT (vet_id, specialty_id) VALUES (s.vet_id, s.specialty_id)
@@

MERGE INTO vet_specialties t
USING (SELECT 4 vet_id, 2 specialty_id FROM dual) s
ON (t.vet_id = s.vet_id AND t.specialty_id = s.specialty_id)
WHEN NOT MATCHED THEN INSERT (vet_id, specialty_id) VALUES (s.vet_id, s.specialty_id)
@@

MERGE INTO vet_specialties t
USING (SELECT 5 vet_id, 1 specialty_id FROM dual) s
ON (t.vet_id = s.vet_id AND t.specialty_id = s.specialty_id)
WHEN NOT MATCHED THEN INSERT (vet_id, specialty_id) VALUES (s.vet_id, s.specialty_id)
@@

MERGE INTO types t
USING (SELECT 1 id, 'cat' name FROM dual) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (name) VALUES (s.name)
@@

MERGE INTO types t
USING (SELECT 2 id, 'dog' name FROM dual) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (name) VALUES (s.name)
@@

MERGE INTO types t
USING (SELECT 3 id, 'lizard' name FROM dual) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (name) VALUES (s.name)
@@

MERGE INTO types t
USING (SELECT 4 id, 'snake' name FROM dual) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (name) VALUES (s.name)
@@

MERGE INTO types t
USING (SELECT 5 id, 'bird' name FROM dual) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (name) VALUES (s.name)
@@

MERGE INTO types t
USING (SELECT 6 id, 'hamster' name FROM dual) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (name) VALUES (s.name)
@@

MERGE INTO owners t
USING (
  SELECT 1 id, 'George' first_name, 'Franklin' last_name, '110 W. Liberty St.' address, 'Madison' city, '6085551023' telephone FROM dual
) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (first_name, last_name, address, city, telephone) VALUES (s.first_name, s.last_name, s.address, s.city, s.telephone)
@@

MERGE INTO owners t
USING (
  SELECT 2 id, 'Betty' first_name, 'Davis' last_name, '638 Cardinal Ave.' address, 'Sun Prairie' city, '6085551749' telephone FROM dual
) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (first_name, last_name, address, city, telephone) VALUES (s.first_name, s.last_name, s.address, s.city, s.telephone)
@@

MERGE INTO owners t
USING (
  SELECT 3 id, 'Eduardo' first_name, 'Rodriquez' last_name, '2693 Commerce St.' address, 'McFarland' city, '6085558763' telephone FROM dual
) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (first_name, last_name, address, city, telephone) VALUES (s.first_name, s.last_name, s.address, s.city, s.telephone)
@@

MERGE INTO owners t
USING (
  SELECT 4 id, 'Harold' first_name, 'Davis' last_name, '563 Friendly St.' address, 'Windsor' city, '6085553198' telephone FROM dual
) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (first_name, last_name, address, city, telephone) VALUES (s.first_name, s.last_name, s.address, s.city, s.telephone)
@@

MERGE INTO owners t
USING (
  SELECT 5 id, 'Peter' first_name, 'McTavish' last_name, '2387 S. Fair Way' address, 'Madison' city, '6085552765' telephone FROM dual
) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (first_name, last_name, address, city, telephone) VALUES (s.first_name, s.last_name, s.address, s.city, s.telephone)
@@

MERGE INTO owners t
USING (
  SELECT 6 id, 'Jean' first_name, 'Coleman' last_name, '105 N. Lake St.' address, 'Monona' city, '6085552654' telephone FROM dual
) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (first_name, last_name, address, city, telephone) VALUES (s.first_name, s.last_name, s.address, s.city, s.telephone)
@@

MERGE INTO owners t
USING (
  SELECT 7 id, 'Jeff' first_name, 'Black' last_name, '1450 Oak Blvd.' address, 'Monona' city, '6085555387' telephone FROM dual
) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (first_name, last_name, address, city, telephone) VALUES (s.first_name, s.last_name, s.address, s.city, s.telephone)
@@

MERGE INTO owners t
USING (
  SELECT 8 id, 'Maria' first_name, 'Escobito' last_name, '345 Maple St.' address, 'Madison' city, '6085557683' telephone FROM dual
) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (first_name, last_name, address, city, telephone) VALUES (s.first_name, s.last_name, s.address, s.city, s.telephone)
@@

MERGE INTO owners t
USING (
  SELECT 9 id, 'David' first_name, 'Schroeder' last_name, '2749 Blackhawk Trail' address, 'Madison' city, '6085559435' telephone FROM dual
) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (first_name, last_name, address, city, telephone) VALUES (s.first_name, s.last_name, s.address, s.city, s.telephone)
@@

MERGE INTO owners t
USING (
  SELECT 10 id, 'Carlos' first_name, 'Estaban' last_name, '2335 Independence La.' address, 'Waunakee' city, '6085555487' telephone FROM dual
) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (first_name, last_name, address, city, telephone) VALUES (s.first_name, s.last_name, s.address, s.city, s.telephone)
@@

MERGE INTO pets t
USING (
  SELECT 1 id, 'Leo' name, DATE '2000-09-07' birth_date, 1 type_id, 1 owner_id FROM dual
) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (name, birth_date, type_id, owner_id) VALUES (s.name, s.birth_date, s.type_id, s.owner_id)
@@

MERGE INTO pets t
USING (
  SELECT 2 id, 'Basil' name, DATE '2002-08-06' birth_date, 6 type_id, 2 owner_id FROM dual
) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (name, birth_date, type_id, owner_id) VALUES (s.name, s.birth_date, s.type_id, s.owner_id)
@@

MERGE INTO pets t
USING (
  SELECT 3 id, 'Rosy' name, DATE '2001-04-17' birth_date, 2 type_id, 3 owner_id FROM dual
) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (name, birth_date, type_id, owner_id) VALUES (s.name, s.birth_date, s.type_id, s.owner_id)
@@

MERGE INTO pets t
USING (
  SELECT 4 id, 'Jewel' name, DATE '2000-03-07' birth_date, 2 type_id, 3 owner_id FROM dual
) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (name, birth_date, type_id, owner_id) VALUES (s.name, s.birth_date, s.type_id, s.owner_id)
@@

MERGE INTO pets t
USING (
  SELECT 5 id, 'Iggy' name, DATE '2000-11-30' birth_date, 3 type_id, 4 owner_id FROM dual
) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (name, birth_date, type_id, owner_id) VALUES (s.name, s.birth_date, s.type_id, s.owner_id)
@@

MERGE INTO pets t
USING (
  SELECT 6 id, 'George' name, DATE '2000-01-20' birth_date, 4 type_id, 5 owner_id FROM dual
) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (name, birth_date, type_id, owner_id) VALUES (s.name, s.birth_date, s.type_id, s.owner_id)
@@

MERGE INTO pets t
USING (
  SELECT 7 id, 'Samantha' name, DATE '1995-09-04' birth_date, 1 type_id, 6 owner_id FROM dual
) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (name, birth_date, type_id, owner_id) VALUES (s.name, s.birth_date, s.type_id, s.owner_id)
@@

MERGE INTO pets t
USING (
  SELECT 8 id, 'Max' name, DATE '1995-09-04' birth_date, 1 type_id, 6 owner_id FROM dual
) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (name, birth_date, type_id, owner_id) VALUES (s.name, s.birth_date, s.type_id, s.owner_id)
@@

MERGE INTO pets t
USING (
  SELECT 9 id, 'Lucky' name, DATE '1999-08-06' birth_date, 5 type_id, 7 owner_id FROM dual
) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (name, birth_date, type_id, owner_id) VALUES (s.name, s.birth_date, s.type_id, s.owner_id)
@@

MERGE INTO pets t
USING (
  SELECT 10 id, 'Mulligan' name, DATE '1997-02-24' birth_date, 2 type_id, 8 owner_id FROM dual
) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (name, birth_date, type_id, owner_id) VALUES (s.name, s.birth_date, s.type_id, s.owner_id)
@@

MERGE INTO pets t
USING (
  SELECT 11 id, 'Freddy' name, DATE '2000-03-09' birth_date, 5 type_id, 9 owner_id FROM dual
) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (name, birth_date, type_id, owner_id) VALUES (s.name, s.birth_date, s.type_id, s.owner_id)
@@

MERGE INTO pets t
USING (
  SELECT 12 id, 'Lucky' name, DATE '2000-06-24' birth_date, 2 type_id, 10 owner_id FROM dual
) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (name, birth_date, type_id, owner_id) VALUES (s.name, s.birth_date, s.type_id, s.owner_id)
@@

MERGE INTO pets t
USING (
  SELECT 13 id, 'Sly' name, DATE '2002-06-08' birth_date, 1 type_id, 10 owner_id FROM dual
) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (name, birth_date, type_id, owner_id) VALUES (s.name, s.birth_date, s.type_id, s.owner_id)
@@

MERGE INTO visits t
USING (
  SELECT 1 id, 7 pet_id, DATE '2010-03-04' visit_date, 'rabies shot' description FROM dual
) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (pet_id, visit_date, description) VALUES (s.pet_id, s.visit_date, s.description)
@@

MERGE INTO visits t
USING (
  SELECT 2 id, 8 pet_id, DATE '2011-03-04' visit_date, 'rabies shot' description FROM dual
) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (pet_id, visit_date, description) VALUES (s.pet_id, s.visit_date, s.description)
@@

MERGE INTO visits t
USING (
  SELECT 3 id, 8 pet_id, DATE '2009-06-04' visit_date, 'neutered' description FROM dual
) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (pet_id, visit_date, description) VALUES (s.pet_id, s.visit_date, s.description)
@@

MERGE INTO visits t
USING (
  SELECT 4 id, 7 pet_id, DATE '2008-09-04' visit_date, 'spayed' description FROM dual
) s
ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (pet_id, visit_date, description) VALUES (s.pet_id, s.visit_date, s.description)
@@
