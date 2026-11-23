-- Snapshot du contenu de la question au moment de la partie (revue des manches de l'écran résultat).
-- Le résultat doit montrer la question telle qu'elle a été jouée, pas son état actuel dans theme.

ALTER TABLE public.round_entry
    ADD COLUMN answers jsonb NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN translations jsonb NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN image_url character varying(2048),
    ADD COLUMN image_alt character varying(2048),
    ADD COLUMN difficulty character varying(32);
