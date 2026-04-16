-- Example English phrasing drills. Copy this to `seed_english.local.sql`,
-- add your own questions, and load it into Postgres with:
--
--   docker exec -i quizmemo-postgres psql -U quizmemo -d quizmemo < db/seed_english.local.sql
--
-- Files matching `*.local.sql` are gitignored. The example file (this one)
-- only shows the schema conventions and a handful of generic questions
-- per CEFR level so a fresh checkout has something to run against.
--
-- Schema reminder (from the EF Core model):
--   "Questions"       ("Id", "Text", "Level", "Explanation", "CreatedAt")
--   "QuestionOptions" ("Id", "QuestionId", "Text", "IsCorrect")
-- "Level" is a string: 'A1', 'A2', 'B1', 'B2', 'C1', 'C2'.

BEGIN;

-- ── A1 ────────────────────────────────────────────────────
WITH q AS (INSERT INTO "Questions" ("Text", "Level", "Explanation", "CreatedAt") VALUES ('Which article is missing? "I need to take my son ___ the doctor."', 'A1', '"To" indicates direction/destination. In English you take someone TO a place. Portuguese often drops the preposition ("levar ao médico" → "take the doctor"), but English requires it.', now()) RETURNING "Id")
INSERT INTO "QuestionOptions" ("QuestionId", "Text", "IsCorrect") SELECT "Id", v.text, v.correct FROM q, (VALUES
    ('at', false),
    ('to', true),
    ('in', false),
    ('on', false)
) AS v(text, correct);

WITH q AS (INSERT INTO "Questions" ("Text", "Level", "Explanation", "CreatedAt") VALUES ('Which compound noun order is correct in English?', 'A1', 'In English, compound nouns put the modifier FIRST: "screen resolution" (not "resolution screen"). This is opposite to Portuguese, where the main noun comes first ("resolução de tela").', now()) RETURNING "Id")
INSERT INTO "QuestionOptions" ("QuestionId", "Text", "IsCorrect") SELECT "Id", v.text, v.correct FROM q, (VALUES
    ('resolution screen', false),
    ('screen resolution', true),
    ('screen of resolution', false),
    ('resolution of screen', false)
) AS v(text, correct);

-- ── A2 ────────────────────────────────────────────────────
WITH q AS (INSERT INTO "Questions" ("Text", "Level", "Explanation", "CreatedAt") VALUES ('Fix the agreement: "trying to remember if there ___ anything pending".', 'A2', '"Anything" is singular and indefinite, so the verb must be singular: "there is" (or the contraction "there''s"). "There are" would require a plural noun like "there are any items pending".', now()) RETURNING "Id")
INSERT INTO "QuestionOptions" ("QuestionId", "Text", "IsCorrect") SELECT "Id", v.text, v.correct FROM q, (VALUES
    ('are', false),
    ('is / ''s', true),
    ('have', false),
    ('were', false)
) AS v(text, correct);

-- ── B1 ────────────────────────────────────────────────────
WITH q AS (INSERT INTO "Questions" ("Text", "Level", "Explanation", "CreatedAt") VALUES ('With a separable phrasal verb, where does a reflexive pronoun go? Pick the native form:', 'B1', 'Separable phrasal verbs like "back up" split around the object pronoun: "back itself up". The pronoun goes BETWEEN the verb and the particle. You cannot say "back up itself" — that sounds unnatural to native ears.', now()) RETURNING "Id")
INSERT INTO "QuestionOptions" ("QuestionId", "Text", "IsCorrect") SELECT "Id", v.text, v.correct FROM q, (VALUES
    ('it would back up itself', false),
    ('it would back itself up', true),
    ('it would self-back up', false),
    ('itself, it would back up', false)
) AS v(text, correct);

WITH q AS (INSERT INTO "Questions" ("Text", "Level", "Explanation", "CreatedAt") VALUES ('Which is the native idiom for "ganhar tempo" (stall)?', 'B1', '"Buy time" is the correct English idiom meaning to delay or stall to get more time. "Gain time" and "win time" are direct translations from Portuguese ("ganhar tempo") but are not idiomatic in English.', now()) RETURNING "Id")
INSERT INTO "QuestionOptions" ("QuestionId", "Text", "IsCorrect") SELECT "Id", v.text, v.correct FROM q, (VALUES
    ('to gain time', false),
    ('to win time', false),
    ('to buy time', true),
    ('to save up time', false)
) AS v(text, correct);

-- ── B2 ────────────────────────────────────────────────────
WITH q AS (INSERT INTO "Questions" ("Text", "Level", "Explanation", "CreatedAt") VALUES ('"Trust" in English means:', 'B2', '"Trust" is a false cognate trap for Portuguese speakers. It means "to have faith in / to rely on" (confiar), NOT "to assume" (which is what "assumir" means in Portuguese). When you want to say you assumed something, use "assumed" or "figured".', now()) RETURNING "Id")
INSERT INTO "QuestionOptions" ("QuestionId", "Text", "IsCorrect") SELECT "Id", v.text, v.correct FROM q, (VALUES
    ('to assume (assumir)', false),
    ('to have faith in (confiar em)', true),
    ('to realize (perceber)', false),
    ('to guess (chutar)', false)
) AS v(text, correct);

-- ── C1 ────────────────────────────────────────────────────
WITH q AS (INSERT INTO "Questions" ("Text", "Level", "Explanation", "CreatedAt") VALUES ('In casual American Slack chat, which connective is most native for "because"?', 'C1', '"As" meaning "because" is formal/literary and sounds British. In casual American English (especially Slack), "now that" or "since" are much more natural. "Now that AI has gotten better" also uses the present perfect ("has gotten") which fits the context of a recent change.', now()) RETURNING "Id")
INSERT INTO "QuestionOptions" ("QuestionId", "Text", "IsCorrect") SELECT "Id", v.text, v.correct FROM q, (VALUES
    ('As AI is better now, I''m testing things.', false),
    ('Now that AI has gotten better, I''m testing things.', true),
    ('How AI is better now, I''m testing things.', false),
    ('Since AI better now, I test things.', false)
) AS v(text, correct);

-- ── C2 ────────────────────────────────────────────────────
WITH q AS (INSERT INTO "Questions" ("Text", "Level", "Explanation", "CreatedAt") VALUES ('Full rewrite: which reads most fluently to a native ear?', 'C2', 'The best rewrite uses an em-dash for dramatic pause, empathy ("I''m as frustrated as you are"), and natural contractions. "I promise none of this is on purpose" is more idiomatic than "I am not doing anything like this on purpose" — it front-loads the reassurance and reads as genuine, not defensive.', now()) RETURNING "Id")
INSERT INTO "QuestionOptions" ("QuestionId", "Text", "IsCorrect") SELECT "Id", v.text, v.correct FROM q, (VALUES
    ('I am not doing anything like this on purpose, I swear.', false),
    ('I promise none of this is on purpose — I''m as frustrated as you are.', true),
    ('I don''t do it on purpose, I promise you.', false),
    ('I''m not doing this purposefully, I am swearing.', false)
) AS v(text, correct);

COMMIT;
