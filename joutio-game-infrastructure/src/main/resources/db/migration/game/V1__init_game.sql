-- Joutio game — baseline schema (squashed)
-- Generated from a fresh PostgreSQL 16 database after applying the legacy migrations.

--
-- PostgreSQL database dump
--


-- Dumped from database version 16.13
-- Dumped by pg_dump version 16.13

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: game_entry; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.game_entry (
    game_id character varying(255) NOT NULL,
    topic_id character varying(255) NOT NULL,
    player1_id character varying(255) NOT NULL,
    player1_name character varying(255) NOT NULL,
    player2_id character varying(255) NOT NULL,
    player2_name character varying(255) NOT NULL,
    player2type character varying(10) NOT NULL,
    bot_difficulty character varying(10),
    status character varying(20) NOT NULL,
    player1_score integer DEFAULT 0,
    player2_score integer DEFAULT 0,
    winner_id character varying(255),
    created_at timestamp without time zone NOT NULL,
    started_at timestamp without time zone,
    ended_at timestamp without time zone,
    version bigint,
    player1_rating integer,
    player2_rating integer
);


--
-- Name: round_entry; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.round_entry (
    id character varying(255) NOT NULL,
    game_id character varying(255) NOT NULL,
    round character varying(10) NOT NULL,
    question_id character varying(255) NOT NULL,
    question_text character varying(1000) NOT NULL,
    correct_answer character varying(5) NOT NULL,
    player1_choice character varying(5),
    player1_time_ms bigint,
    player1_points integer DEFAULT 0,
    player2_choice character varying(5),
    player2_time_ms bigint,
    player2_points integer DEFAULT 0,
    status character varying(20) NOT NULL,
    revealed_at timestamp without time zone,
    answer_deadline_at timestamp without time zone
);


--
-- Name: game_entry game_entry_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.game_entry
    ADD CONSTRAINT game_entry_pkey PRIMARY KEY (game_id);


--
-- Name: round_entry round_entry_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.round_entry
    ADD CONSTRAINT round_entry_pkey PRIMARY KEY (id);


--
-- Name: idx_round_game_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_round_game_id ON public.round_entry USING btree (game_id);


--
-- Name: round_entry fk_round_game; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.round_entry
    ADD CONSTRAINT fk_round_game FOREIGN KEY (game_id) REFERENCES public.game_entry(game_id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--


